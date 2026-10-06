package com.vehiclerental.model;

import java.time.LocalDateTime;

/** A single-use link sent by email (password reset, email verification). */
public class UserToken {

    public static final String PASSWORD_RESET = "PASSWORD_RESET";
    public static final String EMAIL_VERIFY = "EMAIL_VERIFY";

    private int tokenId;
    private int userId;
    private String purpose;
    private LocalDateTime expiresAt;
    private LocalDateTime usedAt;

    public int getTokenId() { return tokenId; }
    public void setTokenId(int tokenId) { this.tokenId = tokenId; }

    public int getUserId() { return userId; }
    public void setUserId(int userId) { this.userId = userId; }

    public String getPurpose() { return purpose; }
    public void setPurpose(String purpose) { this.purpose = purpose; }

    public LocalDateTime getExpiresAt() { return expiresAt; }
    public void setExpiresAt(LocalDateTime expiresAt) { this.expiresAt = expiresAt; }

    public LocalDateTime getUsedAt() { return usedAt; }
    public void setUsedAt(LocalDateTime usedAt) { this.usedAt = usedAt; }
}
