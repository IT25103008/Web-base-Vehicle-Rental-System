package com.vehiclerental.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** The single-use token from an email link. */
public class TokenRequest {

    @NotBlank(message = "The link is incomplete - open it from your email again")
    @Size(max = 200, message = "The link is not valid - open it from your email again")
    private String token;

    public String getToken() { return token; }
    public void setToken(String token) { this.token = token == null ? null : token.trim(); }
}
