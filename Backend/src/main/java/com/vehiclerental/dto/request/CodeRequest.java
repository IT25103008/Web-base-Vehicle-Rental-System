package com.vehiclerental.dto.request;

import com.vehiclerental.validation.Rules;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/** A six-digit code from an authenticator app. */
public class CodeRequest {

    @NotBlank(message = "Enter the six-digit code")
    @Pattern(regexp = Rules.OTP_PATTERN, message = Rules.OTP_MSG)
    private String code;

    public String getCode() { return code; }
    // Apps show the code as "123 456"; the space is not part of it.
    public void setCode(String code) { this.code = code == null ? null : code.replaceAll("\\s", ""); }
}
