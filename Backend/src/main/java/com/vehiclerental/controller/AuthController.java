package com.vehiclerental.controller;

import com.vehiclerental.dao.UserDao;
import com.vehiclerental.dto.request.*;
import com.vehiclerental.dto.response.UserResponse;
import com.vehiclerental.exception.UnauthorizedActionException;
import com.vehiclerental.model.User;
import com.vehiclerental.security.AppUserPrincipal;
import com.vehiclerental.security.LoginAttemptService;
import com.vehiclerental.security.SessionHelper;
import com.vehiclerental.service.AccountService;
import com.vehiclerental.service.UserService;
import com.vehiclerental.util.AppClock;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.Map;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    /** Session attribute holding "password was right, code still needed". */
    private static final String PENDING_2FA = "AXLE_PENDING_2FA";
    private static final int PENDING_2FA_MINUTES = 5;
    private static final int MAX_CODE_ATTEMPTS = 5;

    private final UserService userService;
    private final AccountService accountService;
    private final AuthenticationManager authenticationManager;
    private final SessionHelper sessions;
    private final LoginAttemptService loginAttempts;
    private final UserDao userDao;

    public AuthController(UserService userService, AccountService accountService,
                          AuthenticationManager authenticationManager, SessionHelper sessions,
                          LoginAttemptService loginAttempts, UserDao userDao) {
        this.userService = userService;
        this.accountService = accountService;
        this.authenticationManager = authenticationManager;
        this.sessions = sessions;
        this.loginAttempts = loginAttempts;
        this.userDao = userDao;
    }

    // Register a new customer, and send the link that confirms their email.
    @PostMapping("/register")
    public ResponseEntity<UserResponse> register(@Valid @RequestBody RegisterCustomerRequest request) {
        UserResponse response = userService.registerCustomer(request);
        accountService.sendEmailVerification(response.getUserId());
        return ResponseEntity.ok(response);
    }

    /**
     * JSON sign-in. With two-step sign-in turned on, a right password is not
     * enough: the answer is 202 { twoFactorRequired: true } and the session
     * stays signed OUT until POST /login/2fa brings the code.
     */
    @PostMapping("/login")
    public ResponseEntity<?> login(@Valid @RequestBody LoginRequest request,
                                   HttpServletRequest httpRequest,
                                   HttpServletResponse httpResponse) {
        Authentication auth = authenticationManager.authenticate(
            UsernamePasswordAuthenticationToken.unauthenticated(request.getEmail(), request.getPassword()));
        AppUserPrincipal principal = (AppUserPrincipal) auth.getPrincipal();

        if (principal.getUser().isTotpEnabled()) {
            HttpSession session = httpRequest.getSession(true);
            httpRequest.changeSessionId();
            session.setAttribute(PENDING_2FA, new Pending2fa(principal.getUserId(), AppClock.now()));
            return ResponseEntity.status(HttpStatus.ACCEPTED)
                .body(Map.of("twoFactorRequired", true,
                             "message", "Enter the six-digit code from your authenticator app"));
        }

        sessions.signIn(principal, httpRequest, httpResponse);
        return ResponseEntity.ok(userService.toFullResponse(principal.getUser()));
    }

    /** Second step of sign-in: the code from the authenticator app. */
    @PostMapping("/login/2fa")
    public ResponseEntity<UserResponse> loginSecondStep(@Valid @RequestBody CodeRequest request,
                                                        HttpServletRequest httpRequest,
                                                        HttpServletResponse httpResponse) {
        HttpSession session = httpRequest.getSession(false);
        Pending2fa pending = session == null ? null : (Pending2fa) session.getAttribute(PENDING_2FA);
        if (pending == null || pending.startedAt.plusMinutes(PENDING_2FA_MINUTES).isBefore(AppClock.now())) {
            if (session != null) session.removeAttribute(PENDING_2FA);
            throw new UnauthorizedActionException("Your sign-in timed out. Enter your email and password again.");
        }
        User user = userDao.findById(pending.userId)
            .orElseThrow(() -> new UnauthorizedActionException("Sign in again"));
        if (!accountService.checkTotp(pending.userId, request.getCode())) {
            pending.attempts++;
            loginAttempts.loginFailed(user.getEmail());
            if (pending.attempts >= MAX_CODE_ATTEMPTS) {
                session.removeAttribute(PENDING_2FA);
                throw new UnauthorizedActionException("Too many wrong codes. Enter your email and password again.");
            }
            throw new UnauthorizedActionException("That code is not right. Try the newest one from your app.");
        }
        session.removeAttribute(PENDING_2FA);
        loginAttempts.loginSucceeded(user.getEmail());
        sessions.signIn(new AppUserPrincipal(user), httpRequest, httpResponse);
        return ResponseEntity.ok(userService.toFullResponse(user));
    }

    // The signed-in person, read fresh from the database (so edits, a newly
    // verified licence or a disabled account show straight away), or 204 when
    // nobody is signed in. The frontend calls this on every page load.
    @GetMapping("/me")
    public ResponseEntity<UserResponse> currentUser(HttpServletRequest request) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || !(auth.getPrincipal() instanceof AppUserPrincipal p)) {
            return ResponseEntity.noContent().build();
        }
        User fresh = userDao.findById(p.getUserId()).orElse(null);
        if (fresh == null || !fresh.isActive()) {
            HttpSession session = request.getSession(false);
            if (session != null) session.invalidate();
            SecurityContextHolder.clearContext();
            return ResponseEntity.noContent().build();
        }
        return ResponseEntity.ok(userService.toFullResponse(fresh));
    }

    // Manual logout: invalidates the current session.
    @PostMapping("/logout")
    public ResponseEntity<Map<String, String>> logout(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        if (session != null) {
            session.invalidate();
        }
        SecurityContextHolder.clearContext();
        return ResponseEntity.ok(Map.of("message", "Signed out"));
    }

    // ---------- password reset (A1) ----------
    /** Always the same answer, so it cannot be used to discover who has an account. */
    @PostMapping("/forgot-password")
    public ResponseEntity<Map<String, String>> forgotPassword(@Valid @RequestBody EmailRequest request) {
        accountService.requestPasswordReset(request.getEmail());
        return ResponseEntity.ok(Map.of("message",
            "If that email belongs to an account, a reset link is on its way. It works for one hour."));
    }

    @PostMapping("/reset-password")
    public ResponseEntity<Map<String, String>> resetPassword(@Valid @RequestBody TokenPasswordRequest request) {
        int userId = accountService.resetPassword(request.getToken(), request.getPassword());
        sessions.endOtherSessions(userId, null);      // anyone signed in with the old password is out
        return ResponseEntity.ok(Map.of("message", "Password changed. Sign in with your new password."));
    }

    // ---------- email verification (A2) ----------
    @PostMapping("/verify-email")
    public ResponseEntity<Map<String, String>> verifyEmail(@Valid @RequestBody TokenRequest request) {
        accountService.verifyEmail(request.getToken());
        return ResponseEntity.ok(Map.of("message", "Email confirmed. Thank you."));
    }

    // ---------- two-step sign-in set-up (A5), for staff and administrators ----------
    @PostMapping("/2fa/setup")
    public AccountService.TotpSetup startTwoFactor(@AuthenticationPrincipal AppUserPrincipal me) {
        return accountService.startTotpSetup(me.getUserId());
    }

    @PostMapping("/2fa/enable")
    public ResponseEntity<UserResponse> enableTwoFactor(@Valid @RequestBody CodeRequest request,
                                                        @AuthenticationPrincipal AppUserPrincipal me,
                                                        HttpServletRequest httpRequest, HttpServletResponse httpResponse) {
        accountService.enableTotp(me.getUserId(), request.getCode());
        sessions.refreshPrincipal(me.getUserId(), httpRequest, httpResponse);
        return ResponseEntity.ok(accountService.profile(me.getUserId()));
    }

    @PostMapping("/2fa/disable")
    public ResponseEntity<UserResponse> disableTwoFactor(@Valid @RequestBody PasswordRequest request,
                                                         @AuthenticationPrincipal AppUserPrincipal me,
                                                         HttpServletRequest httpRequest, HttpServletResponse httpResponse) {
        accountService.disableTotp(me.getUserId(), request.getPassword());
        sessions.refreshPrincipal(me.getUserId(), httpRequest, httpResponse);
        return ResponseEntity.ok(accountService.profile(me.getUserId()));
    }

    /** "Password right, code outstanding" - kept in the session between the two steps. */
    static final class Pending2fa implements Serializable {
        final int userId;
        final LocalDateTime startedAt;
        int attempts;

        Pending2fa(int userId, LocalDateTime startedAt) {
            this.userId = userId;
            this.startedAt = startedAt;
        }
    }
}
