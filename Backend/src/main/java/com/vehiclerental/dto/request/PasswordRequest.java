package com.vehiclerental.dto.request;

import com.vehiclerental.validation.Rules;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** A password re-typed to confirm something irreversible (erasure, turning off 2FA). */
public class PasswordRequest {

    @NotBlank(message = "Enter your password to confirm")
    @Size(max = Rules.PASSWORD_MAX, message = "Password can be at most " + Rules.PASSWORD_MAX + " characters")
    private String password;

    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }
}
