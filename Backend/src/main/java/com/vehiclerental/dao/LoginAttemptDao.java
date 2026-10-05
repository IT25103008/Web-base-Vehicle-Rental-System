package com.vehiclerental.dao;

import java.time.LocalDateTime;
import java.util.Optional;

public interface LoginAttemptDao {

    /** failures, last failure, and the end of any cool-off. */
    record Attempt(String key, int failures, LocalDateTime lastFailure, LocalDateTime lockedUntil) { }

    Optional<Attempt> find(String key);
    void save(Attempt attempt);
    void delete(String key);
}
