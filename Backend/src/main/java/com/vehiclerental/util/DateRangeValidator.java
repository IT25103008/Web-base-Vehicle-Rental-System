package com.vehiclerental.util;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

public class DateRangeValidator {

    private DateRangeValidator() {
    }

    /**
     * Returns true if the range [pickup, returnDate] is valid:
     *   - both non-null
     *   - pickup is today or later
     *   - returnDate is after pickup
     */
    public static boolean isValid(LocalDate pickup, LocalDate returnDate) {
        if (pickup == null || returnDate == null) {
            return false;
        }
        if (pickup.isBefore(AppClock.today())) {
            return false;
        }
        return returnDate.isAfter(pickup);
    }

    /**
     * The same check, plus the company's booking-window rules, throwing a
     * message that tells the customer exactly what is wrong.
     */
    public static void validateBookingWindow(LocalDate pickup, LocalDate returnDate) {
        if (pickup == null || returnDate == null) {
            throw new IllegalArgumentException("Pickup and return dates are both required");
        }
        LocalDate today = AppClock.today();
        if (pickup.isBefore(today)) {
            throw new IllegalArgumentException("Pickup date cannot be in the past");
        }
        if (!returnDate.isAfter(pickup)) {
            throw new IllegalArgumentException("Return date must be after the pickup date");
        }
        long days = ChronoUnit.DAYS.between(pickup, returnDate);
        if (days > RentalPolicy.MAX_RENTAL_DAYS) {
            throw new IllegalArgumentException(
                "A single booking cannot be longer than " + RentalPolicy.MAX_RENTAL_DAYS + " days");
        }
        long ahead = ChronoUnit.DAYS.between(today, pickup);
        if (ahead > RentalPolicy.MAX_ADVANCE_DAYS) {
            throw new IllegalArgumentException(
                "Bookings can only be made up to " + RentalPolicy.MAX_ADVANCE_DAYS + " days in advance");
        }
    }

    /** True when the two inclusive date ranges share at least one day. */
    public static boolean overlaps(LocalDate aStart, LocalDate aEnd, LocalDate bStart, LocalDate bEnd) {
        return !aStart.isAfter(bEnd) && !bStart.isAfter(aEnd);
    }
}
