package com.vehiclerental.dto.request;

import com.vehiclerental.validation.Rules;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Password reset: the token from the email link, and the new password. */
public class TokenPasswordRequest {

    @NotBlank(message = "The link is incomplete - open it from your email again")
    @Size(max = 200, message = "The link is not valid - open it from your email again")
    private String token;

    @NotBlank(message = "Enter a new password")
    @Size(min = Rules.PASSWORD_MIN, max = Rules.PASSWORD_MAX,
          message = "Password must be " + Rules.PASSWORD_MIN + " to " + Rules.PASSWORD_MAX + " characters")
    private String password;

    public String getToken() { return token; }
    public void setToken(String token) { this.token = token == null ? null : token.trim(); }
    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }
}
