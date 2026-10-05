package com.vehiclerental.dto.request;

import com.vehiclerental.validation.Rules;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public class EmailRequest {

    @NotBlank(message = "Email is required")
    @Email(message = "Enter a valid email address")
    @Size(max = Rules.EMAIL, message = "Email can be at most " + Rules.EMAIL + " characters")
    private String email;

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = Rules.clean(email); }
}
