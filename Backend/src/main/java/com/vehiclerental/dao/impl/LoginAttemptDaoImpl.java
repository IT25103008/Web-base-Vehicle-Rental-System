package com.vehiclerental.dao.impl;

import com.vehiclerental.dao.AbstractJdbcDao;
import com.vehiclerental.dao.LoginAttemptDao;
import org.springframework.stereotype.Repository;

import javax.sql.DataSource;
import java.sql.Timestamp;
import java.util.Optional;

@Repository
public class LoginAttemptDaoImpl extends AbstractJdbcDao<LoginAttemptDao.Attempt, String> implements LoginAttemptDao {

    public LoginAttemptDaoImpl(DataSource dataSource) {
        super(dataSource);
    }

    @Override
    public Optional<Attempt> find(String key) {
        return queryOne("SELECT * FROM login_attempts WHERE attempt_key = ?", rs -> {
            Timestamp locked = rs.getTimestamp("locked_until");
            return new Attempt(rs.getString("attempt_key"), rs.getInt("failures"),
                               rs.getTimestamp("last_failure").toLocalDateTime(),
                               locked == null ? null : locked.toLocalDateTime());
        }, key);
    }

    @Override
    public void save(Attempt a) {
        Timestamp locked = a.lockedUntil() == null ? null : Timestamp.valueOf(a.lockedUntil());
        int updated = executeUpdate("UPDATE login_attempts SET failures = ?, last_failure = ?, locked_until = ? "
                                  + "WHERE attempt_key = ?",
                                    a.failures(), Timestamp.valueOf(a.lastFailure()), locked, a.key());
        if (updated == 0) {
            executeUpdate("INSERT INTO login_attempts (attempt_key, failures, last_failure, locked_until) "
                        + "VALUES (?, ?, ?, ?)", a.key(), a.failures(), Timestamp.valueOf(a.lastFailure()), locked);
        }
    }

    @Override
    public void delete(String key) {
        executeUpdate("DELETE FROM login_attempts WHERE attempt_key = ?", key);
    }
}
