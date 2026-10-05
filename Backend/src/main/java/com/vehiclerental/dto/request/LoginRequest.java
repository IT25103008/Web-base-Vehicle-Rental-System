package com.vehiclerental.dto.request;

import com.vehiclerental.validation.Rules;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

// Body for POST /api/auth/login (JSON login for a frontend).
public class LoginRequest {

    @NotBlank(message = "Email is required")
    @Size(max = Rules.EMAIL, message = "Email can be at most " + Rules.EMAIL + " characters")
    private String email;

    // No minimum here: a wrong password is an ordinary failed sign-in.
    // The maximum is BCrypt's: it only ever reads the first 72 bytes.
    @NotBlank(message = "Password is required")
    @Size(max = Rules.PASSWORD_MAX, message = "Password can be at most " + Rules.PASSWORD_MAX + " characters")
    private String password;

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = Rules.clean(email); }
    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }
}
