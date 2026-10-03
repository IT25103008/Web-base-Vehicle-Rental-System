package com.vehiclerental.validation;

/**
 * Every format rule and length limit for incoming requests, in one place.
 *
 * The lengths match the database columns (V1__baseline.sql), so a value that
 * passes here always fits, and too-long input comes back as a clear 400
 * instead of a database error. The same rules are mirrored for the browser
 * in static/js/rules.js - change both together.
 *
 * Patterns that describe an optional field accept the empty string, because
 * forms send "" for a box left blank.
 */
public final class Rules {

    private Rules() { }

    // ---------- lengths (= column sizes) ----------
    public static final int NAME = 80;             // users.first_name / last_name
    public static final int EMAIL = 120;
    public static final int PHONE = 20;
    public static final int ADDRESS = 255;
    public static final int LICENCE = 50;
    public static final int CODE = 30;             // employee_code, admin_code
    public static final int POSITION = 80;
    /** BCrypt only looks at the first 72 bytes, so anything longer gives a false sense of security. */
    public static final int PASSWORD_MAX = 72;
    public static final int PASSWORD_MIN = 8;

    public static final int NOTES = 500;           // special requests, condition notes, descriptions
    /** Reasons are stored with a short prefix added ("No-show: ..."), so they stay well under the 500-char audit column. */
    public static final int REASON = 255;
    public static final int PROVIDER = 120;        // insurance provider, service provider, repair type
    public static final int POLICY_NUMBER = 50;

    public static final int BRANCH_NAME = 120;
    public static final int STREET = 120;
    public static final int CITY = 80;

    public static final int PLATE = 20;
    public static final int MODEL = 80;
    public static final int FUEL_TYPE = 30;
    public static final int SEARCH_TEXT = 100;

    /** DECIMAL(10,2): at most 8 digits before the point. */
    public static final String MONEY_MAX = "99999999.99";
    public static final int MILEAGE_MAX = 10_000_000;

    // ---------- formats ----------
    /** Sri Lankan numbers: 077 123 4567, 0771234567, +94 77 123 4567, 011-234-5678. */
    public static final String PHONE_PATTERN = "^$|^(\\+94|0)[ -]?\\d{2}[ -]?\\d{3}[ -]?\\d{4}$";
    public static final String PHONE_MSG = "Enter a Sri Lankan phone number, e.g. 077 123 4567 or +94 77 123 4567";

    /** Current cards are one letter and seven digits (B1234567); older ones are 8 to 12 digits. */
    public static final String LICENCE_PATTERN = "^$|^[A-Z]\\d{7}$|^\\d{8,12}$";
    public static final String LICENCE_MSG = "Enter the licence number as printed on the card, e.g. B1234567";

    /** CAB-1234, WP CAB-1234, KX 1234, 301-1234 (older numeric series). */
    public static final String PLATE_PATTERN = "^(?:[A-Z]{2}[ -])?(?:[A-Z]{1,3}|\\d{1,3})[ -]?\\d{4}$";
    public static final String PLATE_MSG = "Enter a plate like CAB-1234, WP CAB-1234 or 301-1234";

    /** Letters in any script, spaces, apostrophes, hyphens and dots - no digits or markup. */
    public static final String NAME_PATTERN = "^[\\p{L}][\\p{L} .'-]*$";
    public static final String NAME_MSG = "Use letters, spaces, apostrophes or hyphens only";

    public static final String CODE_PATTERN = "^[A-Za-z0-9-]{2,30}$";
    public static final String CODE_MSG = "Use 2 to 30 letters, digits or hyphens";

    public static final String POLICY_PATTERN = "^[A-Za-z0-9/-]{3,50}$";
    public static final String POLICY_MSG = "Use 3 to 50 letters, digits, slashes or hyphens";

    public static final String FUEL_LEVEL_PATTERN = "^(Full|3/4|1/2|1/4|Empty)$";
    public static final String FUEL_LEVEL_MSG = "Fuel level must be Full, 3/4, 1/2, 1/4 or Empty";

    public static final String SEVERITY_PATTERN = "^(MINOR|MODERATE|SEVERE)$";
    public static final String SEVERITY_MSG = "Severity must be MINOR, MODERATE or SEVERE";

    public static final String OTP_PATTERN = "^\\d{6}$";
    public static final String OTP_MSG = "Enter the six-digit code from your app";

    /** The 25 administrative districts of Sri Lanka. */
    public static final java.util.List<String> DISTRICTS = java.util.List.of(
        "Ampara", "Anuradhapura", "Badulla", "Batticaloa", "Colombo", "Galle", "Gampaha",
        "Hambantota", "Jaffna", "Kalutara", "Kandy", "Kegalle", "Kilinochchi", "Kurunegala",
        "Mannar", "Matale", "Matara", "Monaragala", "Mullaitivu", "Nuwara Eliya", "Polonnaruwa",
        "Puttalam", "Ratnapura", "Trincomalee", "Vavuniya");

    // ---------- tidy-up used by request setters ----------
    /** Trim, and turn a blank into null. */
    public static String clean(String s) {
        if (s == null) return null;
        String t = s.trim();
        return t.isEmpty() ? null : t;
    }

    /** Trim and upper-case (plates, licence numbers). */
    public static String upper(String s) {
        String t = clean(s);
        return t == null ? null : t.toUpperCase(java.util.Locale.ROOT);
    }

    /** Collapse repeated spaces inside a name. */
    public static String name(String s) {
        String t = clean(s);
        return t == null ? null : t.replaceAll("\\s+", " ");
    }
}
