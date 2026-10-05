package com.vehiclerental.dao;

import com.vehiclerental.model.UserToken;

import java.time.LocalDateTime;
import java.util.Optional;

public interface UserTokenDao {
    void create(int userId, String purpose, String tokenHash, LocalDateTime expiresAt, LocalDateTime createdAt);
    Optional<UserToken> findByHash(String tokenHash, String purpose);
    void markUsed(int tokenId, LocalDateTime when);
    /** Retire every outstanding token of one kind, so only the newest link works. */
    void invalidateAll(int userId, String purpose, LocalDateTime when);
}
