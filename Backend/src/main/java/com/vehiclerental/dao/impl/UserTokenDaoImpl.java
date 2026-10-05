package com.vehiclerental.dao.impl;

import com.vehiclerental.dao.AbstractJdbcDao;
import com.vehiclerental.dao.UserTokenDao;
import com.vehiclerental.model.UserToken;
import org.springframework.stereotype.Repository;

import javax.sql.DataSource;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.Optional;

@Repository
public class UserTokenDaoImpl extends AbstractJdbcDao<UserToken, Integer> implements UserTokenDao {

    public UserTokenDaoImpl(DataSource dataSource) {
        super(dataSource);
    }

    @Override
    public void create(int userId, String purpose, String tokenHash, LocalDateTime expiresAt, LocalDateTime createdAt) {
        executeUpdate("INSERT INTO user_tokens (user_id, purpose, token_hash, expires_at, created_at) "
                    + "VALUES (?, ?, ?, ?, ?)",
                      userId, purpose, tokenHash, Timestamp.valueOf(expiresAt), Timestamp.valueOf(createdAt));
    }

    @Override
    public Optional<UserToken> findByHash(String tokenHash, String purpose) {
        return queryOne("SELECT * FROM user_tokens WHERE token_hash = ? AND purpose = ?", rs -> {
            UserToken t = new UserToken();
            t.setTokenId(rs.getInt("token_id"));
            t.setUserId(rs.getInt("user_id"));
            t.setPurpose(rs.getString("purpose"));
            t.setExpiresAt(rs.getTimestamp("expires_at").toLocalDateTime());
            Timestamp used = rs.getTimestamp("used_at");
            if (used != null) t.setUsedAt(used.toLocalDateTime());
            return t;
        }, tokenHash, purpose);
    }

    @Override
    public void markUsed(int tokenId, LocalDateTime when) {
        executeUpdate("UPDATE user_tokens SET used_at = ? WHERE token_id = ?", Timestamp.valueOf(when), tokenId);
    }

    @Override
    public void invalidateAll(int userId, String purpose, LocalDateTime when) {
        executeUpdate("UPDATE user_tokens SET used_at = ? WHERE user_id = ? AND purpose = ? AND used_at IS NULL",
                      Timestamp.valueOf(when), userId, purpose);
    }
}
