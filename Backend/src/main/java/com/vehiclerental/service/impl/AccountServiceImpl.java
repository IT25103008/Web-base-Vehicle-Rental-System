package com.vehiclerental.service.impl;

import com.vehiclerental.dao.*;
import com.vehiclerental.dto.request.UpdateProfileRequest;
import com.vehiclerental.dto.response.UserResponse;
import com.vehiclerental.enums.PaymentStatus;
import com.vehiclerental.enums.Role;
import com.vehiclerental.exception.ResourceNotFoundException;
import com.vehiclerental.exception.UnauthorizedActionException;
import com.vehiclerental.model.Booking;
import com.vehiclerental.model.Customer;
import com.vehiclerental.model.User;
import com.vehiclerental.model.UserToken;
import com.vehiclerental.security.TotpService;
import com.vehiclerental.service.AccountService;
import com.vehiclerental.service.AuditService;
import com.vehiclerental.service.MailService;
import com.vehiclerental.service.UserService;
import com.vehiclerental.util.AppClock;
import com.vehiclerental.util.PasswordPolicy;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

@Service
public class AccountServiceImpl implements AccountService {

    private static final int RESET_HOURS = 1;
    private static final int VERIFY_HOURS = 48;
    private static final String ISSUER = "Axle Rental";

    private final UserDao userDao;
    private final UserTokenDao tokenDao;
    private final BookingDao bookingDao;
    private final PaymentDao paymentDao;
    private final NotificationDao notificationDao;
    private final FavouriteDao favouriteDao;
    private final AuditDao auditDao;
    private final UserService userService;
    private final AuditService auditService;
    private final MailService mail;
    private final PasswordEncoder passwordEncoder;
    private final TotpService totp;
    private final String publicUrl;
    private final SecureRandom random = new SecureRandom();

    public AccountServiceImpl(UserDao userDao, UserTokenDao tokenDao, BookingDao bookingDao, PaymentDao paymentDao,
                              NotificationDao notificationDao, FavouriteDao favouriteDao, AuditDao auditDao,
                              UserService userService, AuditService auditService, MailService mail,
                              PasswordEncoder passwordEncoder, TotpService totp,
                              @Value("${rental.public-url:http://localhost:8080}") String publicUrl) {
        this.userDao = userDao;
        this.tokenDao = tokenDao;
        this.bookingDao = bookingDao;
        this.paymentDao = paymentDao;
        this.notificationDao = notificationDao;
        this.favouriteDao = favouriteDao;
        this.auditDao = auditDao;
        this.userService = userService;
        this.auditService = auditService;
        this.mail = mail;
        this.passwordEncoder = passwordEncoder;
        this.totp = totp;
        this.publicUrl = publicUrl.endsWith("/") ? publicUrl.substring(0, publicUrl.length() - 1) : publicUrl;
    }

    // ============================================================
    // Password reset
    // ============================================================
    @Override
    @Transactional
    public void requestPasswordReset(String email) {
        String e = email == null ? "" : email.trim().toLowerCase();
        User u = userDao.findByEmail(e).orElse(null);
        if (u == null || !u.isActive() || u.getAnonymisedAt() != null) {
            return;   // the same answer whether or not the account exists
        }
        String token = issueToken(u.getUserId(), UserToken.PASSWORD_RESET, RESET_HOURS);
        mail.send(u.getEmail(), "Reset your Axle password",
            "Hello " + u.getFirstName() + ",\n\n"
            + "Someone (hopefully you) asked to reset the password for this account.\n"
            + "Choose a new one here. The link works once, for the next hour:\n\n"
            + publicUrl + "/#/reset?token=" + token + "\n\n"
            + "If this was not you, ignore this email: your password has not changed.\n\n- Axle");
        auditService.record("USER", u.getUserId(), "UPDATE", null, "Password reset requested");
    }

    @Override
    @Transactional
    public int resetPassword(String token, String newPassword) {
        PasswordPolicy.require(newPassword);
        UserToken t = redeem(token, UserToken.PASSWORD_RESET,
            "This reset link is not valid, has already been used, or has expired. Ask for a new one.");
        userDao.updatePasswordHash(t.getUserId(), passwordEncoder.encode(newPassword));
        tokenDao.invalidateAll(t.getUserId(), UserToken.PASSWORD_RESET, AppClock.now());
        auditService.record("USER", t.getUserId(), "UPDATE", t.getUserId(), "Password reset by email link");
        userDao.findById(t.getUserId()).ifPresent(u -> mail.send(u.getEmail(), "Your Axle password was changed",
            "Hello " + u.getFirstName() + ",\n\nThe password for your account was just reset, and every "
            + "signed-in device was signed out. If this was not you, contact any branch straight away.\n\n- Axle"));
        return t.getUserId();
    }

    // ============================================================
    // Email verification
    // ============================================================
    @Override
    @Transactional
    public void sendEmailVerification(int userId) {
        User u = load(userId);
        if (u.isEmailVerified()) {
            return;
        }
        tokenDao.invalidateAll(userId, UserToken.EMAIL_VERIFY, AppClock.now());
        String token = issueToken(userId, UserToken.EMAIL_VERIFY, VERIFY_HOURS);
        mail.send(u.getEmail(), "Confirm your email for Axle",
            "Welcome to Axle, " + u.getFirstName() + ".\n\n"
            + "Please confirm this is your email address. You need to before your first reservation:\n\n"
            + publicUrl + "/#/verify?token=" + token + "\n\n"
            + "The link works for 48 hours. If you did not create an account, ignore this email.\n\n- Axle");
    }

    @Override
    @Transactional
    public void verifyEmail(String token) {
        UserToken t = redeem(token, UserToken.EMAIL_VERIFY,
            "This confirmation link is not valid or has expired. Sign in and ask for a new one.");
        userDao.updateEmailVerified(t.getUserId(), true);
        auditService.recordStatusChange("USER", t.getUserId(), "EMAIL_UNVERIFIED", "EMAIL_VERIFIED",
                                        t.getUserId(), "Email address confirmed");
    }

    // ============================================================
    // Profile and password
    // ============================================================
    @Override
    public UserResponse profile(int userId) {
        return userService.toFullResponse(load(userId));
    }

    @Override
    @Transactional
    public UserResponse updateProfile(int userId, UpdateProfileRequest r) {
        User u = load(userId);
        String first = r.getFirstName().trim();
        String last = r.getLastName().trim();
        String phone = blankToNull(r.getPhoneNumber());
        String address = blankToNull(r.getAddress());
        userDao.updateProfile(userId, first, last, phone, address);

        List<String> changed = new ArrayList<>();
        if (!Objects.equals(u.getFirstName(), first) || !Objects.equals(u.getLastName(), last)) changed.add("name");
        if (!Objects.equals(u.getPhoneNumber(), phone)) changed.add("phone");
        if (!Objects.equals(u.getAddress(), address)) changed.add("address");

        if (u instanceof Customer c && (r.getDrivingLicenseNumber() != null || r.getLicenseExpiryDate() != null)) {
            String number = r.getDrivingLicenseNumber() == null
                ? c.getDrivingLicenseNumber() : r.getDrivingLicenseNumber().trim();
            LocalDate expiry = r.getLicenseExpiryDate() == null ? c.getLicenseExpiryDate() : r.getLicenseExpiryDate();
            boolean licenceChanged = !Objects.equals(number, c.getDrivingLicenseNumber())
                                  || !Objects.equals(expiry, c.getLicenseExpiryDate());
            if (licenceChanged) {
                if (number == null || number.isBlank()) {
                    throw new IllegalArgumentException("Driving licence number is required");
                }
                if (expiry == null || !expiry.isAfter(AppClock.today())) {
                    throw new IllegalArgumentException("The licence expiry date must be in the future");
                }
                // A different licence is a different document: staff have to see it again.
                userDao.updateLicence(userId, number, expiry);
                changed.add("licence (to be verified again)");
                if (c.isLicenseVerified()) {
                    auditService.recordStatusChange("USER", userId, "LICENCE_VERIFIED", "LICENCE_UNVERIFIED",
                                                    userId, "Licence details changed by the customer");
                }
            }
        }
        if (!changed.isEmpty()) {
            auditService.record("USER", userId, "UPDATE", userId, "Profile updated: " + String.join(", ", changed));
        }
        return profile(userId);
    }

    @Override
    @Transactional
    public void changePassword(int userId, String currentPassword, String newPassword) {
        User u = load(userId);
        if (!passwordEncoder.matches(currentPassword, u.getPasswordHash())) {
            throw new UnauthorizedActionException("Your current password is not correct");
        }
        if (passwordEncoder.matches(newPassword, u.getPasswordHash())) {
            throw new IllegalArgumentException("The new password must be different from the current one");
        }
        PasswordPolicy.require(newPassword);
        userDao.updatePasswordHash(userId, passwordEncoder.encode(newPassword));
        tokenDao.invalidateAll(userId, UserToken.PASSWORD_RESET, AppClock.now());
        auditService.record("USER", userId, "UPDATE", userId, "Password changed");
        mail.send(u.getEmail(), "Your Axle password was changed",
            "Hello " + u.getFirstName() + ",\n\nYour password was just changed, and every other signed-in "
            + "device was signed out. If this was not you, reset it now from the sign-in page.\n\n- Axle");
    }

    // ============================================================
    // Two-step sign-in (staff and administrators)
    // ============================================================
    @Override
    @Transactional
    public TotpSetup startTotpSetup(int userId) {
        User u = load(userId);
        if (u.getRole() == Role.CUSTOMER) {
            throw new UnauthorizedActionException("Two-step sign-in is for staff and administrator accounts");
        }
        if (u.isTotpEnabled()) {
            throw new IllegalArgumentException(
                "Two-step sign-in is already on. Turn it off first to set up a new device.");
        }
        String secret = totp.newSecret();
        userDao.updateTotp(userId, secret, false);     // stored, but not required until confirmed
        return new TotpSetup(secret, totp.otpauthUri(ISSUER, u.getEmail(), secret));
    }

    @Override
    @Transactional
    public void enableTotp(int userId, String code) {
        User u = load(userId);
        if (u.getTotpSecret() == null) {
            throw new IllegalArgumentException("Start the setup first");
        }
        if (!totp.verify(u.getTotpSecret(), code)) {
            throw new IllegalArgumentException(
                "That code is not right. Check the time on your phone and try the newest code.");
        }
        userDao.updateTotp(userId, u.getTotpSecret(), true);
        auditService.recordStatusChange("USER", userId, "2FA_OFF", "2FA_ON", userId, "Two-step sign-in turned on");
    }

    @Override
    @Transactional
    public void disableTotp(int userId, String password) {
        User u = load(userId);
        if (!passwordEncoder.matches(password, u.getPasswordHash())) {
            throw new UnauthorizedActionException("Your password is not correct");
        }
        userDao.updateTotp(userId, null, false);
        auditService.recordStatusChange("USER", userId, "2FA_ON", "2FA_OFF", userId, "Two-step sign-in turned off");
    }

    @Override
    public boolean checkTotp(int userId, String code) {
        User u = load(userId);
        return u.isTotpEnabled() && totp.verify(u.getTotpSecret(), code);
    }

    // ============================================================
    // Personal data (PDPA)
    // ============================================================
    @Override
    public Map<String, Object> exportData(int userId) {
        User u = load(userId);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("exportedAt", AppClock.now().toString());
        out.put("about", "Everything Axle holds about you. Bookings and payments are kept for as long as "
                       + "the law requires; everything else can be erased from your account page.");
        out.put("account", userService.toFullResponse(u));
        if (u.getRole() == Role.CUSTOMER) {
            List<Booking> bookings = bookingDao.findByCustomer(userId);
            out.put("bookings", bookings);
            out.put("payments", bookings.stream()
                .map(b -> paymentDao.findByBooking(b.getBookingId()).orElse(null))
                .filter(Objects::nonNull).toList());
            out.put("savedCars", favouriteDao.findVehicleIds(userId));
        }
        out.put("notifications", notificationDao.findByUser(userId));
        out.put("accountHistory", auditDao.findByEntity("USER", userId));
        auditService.record("USER", userId, "EXPORT", userId, "Personal data exported by the account holder");
        return out;
    }

    @Override
    @Transactional
    public void eraseAccount(int userId, String password) {
        User u = load(userId);
        if (u.getRole() != Role.CUSTOMER) {
            throw new UnauthorizedActionException(
                "Staff and administrator accounts are closed by an administrator, not erased");
        }
        if (!passwordEncoder.matches(password, u.getPasswordHash())) {
            throw new UnauthorizedActionException("Your password is not correct");
        }
        if (bookingDao.countLiveByCustomer(userId) > 0) {
            throw new IllegalArgumentException(
                "You still have a booking that is pending, approved or on the road. "
                + "Finish or cancel it first, then erase your account.");
        }
        boolean owes = bookingDao.findByCustomer(userId).stream()
            .map(b -> paymentDao.findByBooking(b.getBookingId()).orElse(null))
            .anyMatch(p -> p != null && p.getStatus() == PaymentStatus.PENDING);
        if (owes) {
            throw new IllegalArgumentException(
                "There is an unpaid balance on this account. Settle it at a branch before erasing the account.");
        }
        LocalDateTime now = AppClock.now();
        // The row stays, so the bookings and payments the business must keep
        // still point at something - but nothing on it identifies a person.
        userDao.anonymise(userId, "erased-" + userId + "@erased.invalid",
                          passwordEncoder.encode(randomToken()), now);
        for (Integer vehicleId : favouriteDao.findVehicleIds(userId)) {
            favouriteDao.remove(userId, vehicleId);
        }
        tokenDao.invalidateAll(userId, UserToken.PASSWORD_RESET, now);
        tokenDao.invalidateAll(userId, UserToken.EMAIL_VERIFY, now);
        auditService.record("USER", userId, "ERASE", userId,
            "Personal data erased at the account holder's request (bookings and payments kept, anonymised)");
    }

    // ============================================================
    // Helpers
    // ============================================================
    private User load(int userId) {
        return userDao.findById(userId)
            .orElseThrow(() -> new ResourceNotFoundException("User not found: " + userId));
    }

    private String issueToken(int userId, String purpose, int hours) {
        String token = randomToken();
        LocalDateTime now = AppClock.now();
        tokenDao.create(userId, purpose, sha256(token), now.plusHours(hours), now);
        return token;
    }

    /** Checks and uses up a token in one go; an unusable token always gets the same message. */
    private UserToken redeem(String token, String purpose, String failureMessage) {
        if (token == null || token.isBlank()) {
            throw new IllegalArgumentException(failureMessage);
        }
        UserToken t = tokenDao.findByHash(sha256(token.trim()), purpose)
            .orElseThrow(() -> new IllegalArgumentException(failureMessage));
        if (t.getUsedAt() != null || t.getExpiresAt().isBefore(AppClock.now())) {
            throw new IllegalArgumentException(failureMessage);
        }
        tokenDao.markUsed(t.getTokenId(), AppClock.now());
        return t;
    }

    private String randomToken() {
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    static String sha256(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }
}
