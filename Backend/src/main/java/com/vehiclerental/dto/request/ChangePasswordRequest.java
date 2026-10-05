package com.vehiclerental.dto.request;

import com.vehiclerental.validation.Rules;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public class ChangePasswordRequest {

    @NotBlank(message = "Enter your current password")
    @Size(max = Rules.PASSWORD_MAX, message = "Password can be at most " + Rules.PASSWORD_MAX + " characters")
    private String currentPassword;

    // Letters-and-digits and the common-password list are checked by PasswordPolicy.
    @NotBlank(message = "Enter a new password")
    @Size(min = Rules.PASSWORD_MIN, max = Rules.PASSWORD_MAX,
          message = "New password must be " + Rules.PASSWORD_MIN + " to " + Rules.PASSWORD_MAX + " characters")
    private String newPassword;

    public String getCurrentPassword() { return currentPassword; }
    public void setCurrentPassword(String currentPassword) { this.currentPassword = currentPassword; }
    public String getNewPassword() { return newPassword; }
    public void setNewPassword(String newPassword) { this.newPassword = newPassword; }
}
