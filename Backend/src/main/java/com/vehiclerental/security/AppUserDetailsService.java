package com.vehiclerental.security;

import com.vehiclerental.dao.UserDao;
import com.vehiclerental.model.User;
import org.springframework.security.authentication.LockedException;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

@Service
public class AppUserDetailsService implements UserDetailsService {

    private final UserDao userDao;
    private final LoginAttemptService loginAttemptService;

    public AppUserDetailsService(UserDao userDao, LoginAttemptService loginAttemptService) {
        this.userDao = userDao;
        this.loginAttemptService = loginAttemptService;
    }

    @Override
    public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {
        // Checked before the password is compared, so a locked account gives no
        // further information to whoever is guessing.
        long wait = loginAttemptService.remainingLockMinutes(email);
        if (wait > 0) {
            throw new LockedException(
                "Too many failed sign-in attempts. Try again in " + wait + " minute(s).");
        }

        User user = userDao.findByEmail(email == null ? null : email.trim().toLowerCase())
                .orElseThrow(() -> new UsernameNotFoundException("No user with email: " + email));
        return new AppUserPrincipal(user);
    }
}
