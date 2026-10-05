package com.vehiclerental.security;

import com.vehiclerental.dao.LoginAttemptDao;
import com.vehiclerental.dao.LoginAttemptDao.Attempt;
import com.vehiclerental.util.AppClock;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;

/**
 * Slows down password guessing, without handing anyone a way to lock others out.
 *
 * Failures are counted per email address AND per network address. Someone
 * typing wrong passwords against another person's email only ever locks
 * themselves out: the real owner, signing in from their own connection, is
 * counted separately and is not affected.
 *
 * After the allowed number of failures the next attempt must wait, and every
 * further failure doubles the wait (15, 30, 60 minutes ... up to a day).
 * A successful sign-in clears the count. Counts live in the login_attempts
 * table, so a restart does not wipe them and a second server shares them.
 */
@Service
public class LoginAttemptService {

    private final LoginAttemptDao dao;
    private final int maxAttempts;
    private final int lockMinutes;
    private final int maxLockMinutes;

    public LoginAttemptService(LoginAttemptDao dao,
                               @Value("${rental.login.max-attempts:5}") int maxAttempts,
                               @Value("${rental.login.lock-minutes:15}") int lockMinutes,
                               @Value("${rental.login.max-lock-minutes:1440}") int maxLockMinutes) {
        this.dao = dao;
        this.maxAttempts = maxAttempts;
        this.lockMinutes = lockMinutes;
        this.maxLockMinutes = maxLockMinutes;
    }

    public boolean isBlocked(String email) {
        return remainingLockMinutes(email) > 0;
    }

    /** Minutes still to wait for this email from this address, or 0. */
    public long remainingLockMinutes(String email) {
        Attempt a = dao.find(key(email)).orElse(null);
        if (a == null || a.lockedUntil() == null) {
            return 0;
        }
        LocalDateTime now = AppClock.now();
        if (!now.isBefore(a.lockedUntil())) {
            return 0;
        }
        return Math.max(1, ChronoUnit.MINUTES.between(now, a.lockedUntil()));
    }

    public void loginFailed(String email) {
        String k = key(email);
        LocalDateTime now = AppClock.now();
        int failures = dao.find(k).map(Attempt::failures).orElse(0) + 1;
        LocalDateTime lockedUntil = null;
        if (failures >= maxAttempts) {
            long minutes = (long) lockMinutes << Math.min(20, failures - maxAttempts);
            lockedUntil = now.plusMinutes(Math.min(minutes, maxLockMinutes));
        }
        dao.save(new Attempt(k, failures, now, lockedUntil));
    }

    public void loginSucceeded(String email) {
        dao.delete(key(email));
    }

    private String key(String email) {
        String e = email == null ? "" : email.trim().toLowerCase();
        String k = e + "|" + clientAddress();
        return k.length() > 200 ? k.substring(0, 200) : k;
    }

    /** The caller's address, as seen for the current request (or "-" outside one). */
    static String clientAddress() {
        if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attrs) {
            HttpServletRequest request = attrs.getRequest();
            return request.getRemoteAddr();
        }
        return "-";
    }
}
