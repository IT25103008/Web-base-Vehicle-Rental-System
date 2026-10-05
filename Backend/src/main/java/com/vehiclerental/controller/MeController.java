package com.vehiclerental.controller;

import com.vehiclerental.dao.FavouriteDao;
import com.vehiclerental.dao.VehicleDao;
import com.vehiclerental.dto.request.ChangePasswordRequest;
import com.vehiclerental.dto.request.PasswordRequest;
import com.vehiclerental.dto.request.UpdateProfileRequest;
import com.vehiclerental.dto.response.UserResponse;
import com.vehiclerental.exception.ResourceNotFoundException;
import com.vehiclerental.security.AppUserPrincipal;
import com.vehiclerental.security.SessionHelper;
import com.vehiclerental.service.AccountService;
import com.vehiclerental.service.NotificationService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * The signed-in person's own account, whatever their role. Everything here
 * acts on the caller only - there is no id in the path to tamper with.
 */
@RestController
@RequestMapping("/api/users/me")
public class MeController {

    private final AccountService accountService;
    private final SessionHelper sessions;
    private final FavouriteDao favouriteDao;
    private final VehicleDao vehicleDao;
    private final NotificationService notificationService;

    public MeController(AccountService accountService, SessionHelper sessions, FavouriteDao favouriteDao,
                        VehicleDao vehicleDao, NotificationService notificationService) {
        this.accountService = accountService;
        this.sessions = sessions;
        this.favouriteDao = favouriteDao;
        this.vehicleDao = vehicleDao;
        this.notificationService = notificationService;
    }

    @GetMapping
    public UserResponse me(@AuthenticationPrincipal AppUserPrincipal me) {
        UserResponse r = accountService.profile(me.getUserId());
        return r;
    }

    /** Name, phone, address - and for customers a renewed licence (which then needs verifying again). */
    @PutMapping
    public UserResponse update(@Valid @RequestBody UpdateProfileRequest request,
                               @AuthenticationPrincipal AppUserPrincipal me,
                               HttpServletRequest httpRequest, HttpServletResponse httpResponse) {
        UserResponse r = accountService.updateProfile(me.getUserId(), request);
        sessions.refreshPrincipal(me.getUserId(), httpRequest, httpResponse);
        return r;
    }

    /** Changing the password signs every other device out. */
    @PatchMapping("/password")
    public Map<String, Object> changePassword(@Valid @RequestBody ChangePasswordRequest request,
                                              @AuthenticationPrincipal AppUserPrincipal me,
                                              HttpServletRequest httpRequest) {
        accountService.changePassword(me.getUserId(), request.getCurrentPassword(), request.getNewPassword());
        int ended = sessions.endOtherSessions(me.getUserId(), httpRequest);
        return Map.of("message", "Password changed", "otherSessionsEnded", ended);
    }

    @GetMapping("/sessions")
    public Map<String, Object> sessions(@AuthenticationPrincipal AppUserPrincipal me) {
        return Map.of("signedIn", sessions.countSessions(me.getUserId()));
    }

    @PostMapping("/sessions/end-others")
    public Map<String, Object> endOtherSessions(@AuthenticationPrincipal AppUserPrincipal me,
                                                HttpServletRequest httpRequest) {
        int ended = sessions.endOtherSessions(me.getUserId(), httpRequest);
        return Map.of("message", ended == 0 ? "No other devices were signed in" : "Signed out everywhere else",
                      "otherSessionsEnded", ended);
    }

    @PostMapping("/verification-email")
    public Map<String, String> resendVerification(@AuthenticationPrincipal AppUserPrincipal me) {
        accountService.sendEmailVerification(me.getUserId());
        return Map.of("message", "A new confirmation link is on its way");
    }

    // ---------- personal data (PDPA) ----------
    @GetMapping("/export")
    public ResponseEntity<Map<String, Object>> export(@AuthenticationPrincipal AppUserPrincipal me) {
        return ResponseEntity.ok()
            .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"axle-my-data.json\"")
            .contentType(MediaType.APPLICATION_JSON)
            .body(accountService.exportData(me.getUserId()));
    }

    /** Erasure: needs the password, ends every session including this one. */
    @DeleteMapping
    public Map<String, String> erase(@Valid @RequestBody PasswordRequest request,
                                     @AuthenticationPrincipal AppUserPrincipal me,
                                     HttpServletRequest httpRequest) {
        accountService.eraseAccount(me.getUserId(), request.getPassword());
        sessions.endOtherSessions(me.getUserId(), httpRequest);
        HttpSession session = httpRequest.getSession(false);
        if (session != null) session.invalidate();
        SecurityContextHolder.clearContext();
        return Map.of("message", "Your personal details have been erased and you have been signed out.");
    }

    // ---------- saved cars (E6) ----------
    @GetMapping("/favourites")
    public List<Integer> favourites(@AuthenticationPrincipal AppUserPrincipal me) {
        return favouriteDao.findVehicleIds(me.getUserId());
    }

    @PutMapping("/favourites/{vehicleId}")
    public List<Integer> addFavourite(@PathVariable int vehicleId, @AuthenticationPrincipal AppUserPrincipal me) {
        vehicleDao.findById(vehicleId)
            .orElseThrow(() -> new ResourceNotFoundException("Vehicle not found: " + vehicleId));
        favouriteDao.add(me.getUserId(), vehicleId);
        return favouriteDao.findVehicleIds(me.getUserId());
    }

    @DeleteMapping("/favourites/{vehicleId}")
    public List<Integer> removeFavourite(@PathVariable int vehicleId, @AuthenticationPrincipal AppUserPrincipal me) {
        favouriteDao.remove(me.getUserId(), vehicleId);
        return favouriteDao.findVehicleIds(me.getUserId());
    }

    /** Merge a device's saved cars into the account (called once after sign-in). */
    @PostMapping("/favourites/merge")
    public List<Integer> mergeFavourites(@RequestBody
                                         @Size(max = 100, message = "At most 100 saved cars can be merged at once")
                                         List<@NotNull @Positive Integer> vehicleIds,
                                         @AuthenticationPrincipal AppUserPrincipal me) {
        if (vehicleIds != null) {
            vehicleIds.stream().distinct().limit(100)
                .filter(id -> id != null && vehicleDao.findById(id).isPresent())
                .forEach(id -> favouriteDao.add(me.getUserId(), id));
        }
        return favouriteDao.findVehicleIds(me.getUserId());
    }

    // ---------- notification preferences (N3) ----------
    @GetMapping("/notification-preferences")
    public List<NotificationService.Preference> preferences(@AuthenticationPrincipal AppUserPrincipal me) {
        return notificationService.preferences(me.getUserId());
    }

    @PutMapping("/notification-preferences")
    public List<NotificationService.Preference> savePreferences(@RequestBody
                                                                @Size(max = 10, message = "Too many preferences in one request")
                                                                List<NotificationService.@NotNull Preference> prefs,
                                                                @AuthenticationPrincipal AppUserPrincipal me) {
        notificationService.savePreferences(me.getUserId(), prefs);
        return notificationService.preferences(me.getUserId());
    }
}
