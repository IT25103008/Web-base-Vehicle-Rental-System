package com.vehiclerental.security;

import com.vehiclerental.model.User;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.List;

public class AppUserPrincipal implements UserDetails {

    private final User user;

    public AppUserPrincipal(User user) {
        this.user = user;
    }

    public User getUser() {
        return user;
    }

    public int getUserId() {
        return user.getUserId();
    }

    // Spring Security uses "ROLE_" prefix for role-based rules.
    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of(new SimpleGrantedAuthority("ROLE_" + user.getRole().name()));
    }

    @Override
    public String getPassword() {
        return user.getPasswordHash();
    }

    @Override
    public String getUsername() {
        return user.getEmail();      // we log in with email
    }

    @Override public boolean isAccountNonExpired()     { return true; }
    @Override public boolean isAccountNonLocked()      { return true; }
    @Override public boolean isCredentialsNonExpired() { return true; }
    // A disabled account cannot sign in at all: Spring Security refuses the
    // login before any controller runs.
    @Override public boolean isEnabled()               { return user.isActive(); }

    // The session registry finds a person's sessions by principal, so two
    // principals for the same account must be equal.
    @Override
    public boolean equals(Object o) {
        return o instanceof AppUserPrincipal other && other.getUserId() == getUserId();
    }

    @Override
    public int hashCode() {
        return Integer.hashCode(getUserId());
    }
}
