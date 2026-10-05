package com.vehiclerental.util;

import java.util.Set;

/**
 * The password rule, in one place for registration, staff accounts, password
 * change and reset. Length alone is a weak rule, so a mix of letters and
 * digits is required and the passwords everyone tries first are refused.
 */
public final class PasswordPolicy {

    public static final int MIN_LENGTH = 8;

    private static final Set<String> BANNED = Set.of("password", "password1", "password123", "12345678",
                                                    "qwertyui", "11111111", "abcd1234", "admin123");

    private PasswordPolicy() {
    }

    public static void require(String password) {
        if (password == null || password.length() < MIN_LENGTH) {
            throw new IllegalArgumentException("Password must be at least " + MIN_LENGTH + " characters");
        }
        boolean hasLetter = password.chars().anyMatch(Character::isLetter);
        boolean hasDigit = password.chars().anyMatch(Character::isDigit);
        if (!hasLetter || !hasDigit) {
            throw new IllegalArgumentException("Password must contain both letters and numbers");
        }
        if (BANNED.contains(password.toLowerCase())) {
            throw new IllegalArgumentException("That password is too easy to guess");
        }
    }
}
