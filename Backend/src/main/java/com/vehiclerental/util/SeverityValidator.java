package com.vehiclerental.util;

import java.util.Set;

// Damage severity is stored as a plain string

public class SeverityValidator {

    private static final Set<String> ALLOWED = Set.of("MINOR", "MODERATE", "SEVERE");

    private SeverityValidator() {
    }

    /** Returns the upper-cased severity, or throws IllegalArgumentException if it isn't allowed. */
    public static String normalise(String severity) {
        if (severity == null || !ALLOWED.contains(severity.trim().toUpperCase())) {
            throw new IllegalArgumentException(
                "Damage severity must be one of MINOR, MODERATE, SEVERE (got: " + severity + ")");
        }
        return severity.trim().toUpperCase();
    }
}
