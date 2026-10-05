package com.vehiclerental.service;

import com.vehiclerental.dto.request.UpdateProfileRequest;
import com.vehiclerental.dto.response.UserResponse;

import java.util.Map;

/**
 * Self-service for a signed-in person, and the email-link flows that happen
 * before sign-in: password reset and email verification. Also two-step
 * sign-in for staff and administrators, and the data-protection rights
 * (a copy of your data, erasure).
 */
public interface AccountService {

    // ---- password reset (A1) ----
    /** Always "succeeds" from the caller's point of view, so it cannot be used to find out who has an account. */
    void requestPasswordReset(String email);
    /** Returns the user whose password was reset, so their other sessions can be ended. */
    int resetPassword(String token, String newPassword);

    // ---- email verification (A2) ----
    void sendEmailVerification(int userId);
    void verifyEmail(String token);

    // ---- profile and password (A3) ----
    UserResponse profile(int userId);
    UserResponse updateProfile(int userId, UpdateProfileRequest request);
    void changePassword(int userId, String currentPassword, String newPassword);

    // ---- two-step sign-in (A5) ----
    record TotpSetup(String secret, String otpauthUri) { }
    TotpSetup startTotpSetup(int userId);
    void enableTotp(int userId, String code);
    void disableTotp(int userId, String password);
    boolean checkTotp(int userId, String code);

    // ---- personal data (3.10) ----
    Map<String, Object> exportData(int userId);
    void eraseAccount(int userId, String password);
}
