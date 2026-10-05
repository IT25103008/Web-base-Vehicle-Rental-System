package com.vehiclerental.util;

import java.math.BigDecimal;

/**
 * Every tunable business rule of the rental company, in one place.
 *
 * These used to be scattered through the services as magic numbers, or simply
 * missing. Keeping them here means the rules can be quoted and defended
 * without hunting through the code.
 */
public final class RentalPolicy {

    private RentalPolicy() {
    }

    /** A driver must be at least this old on the pickup date. */
    public static final int MIN_DRIVER_AGE = 18;

    /** A single booking may not run longer than this. */
    public static final int MAX_RENTAL_DAYS = 30;

    /** A booking may not start more than this many days from today. */
    public static final int MAX_ADVANCE_DAYS = 180;

    /** How many bookings one customer may have open (pending/approved/active) at once. */
    public static final int MAX_LIVE_BOOKINGS_PER_CUSTOMER = 3;

    /** A licence must stay valid until at least this many days after the return date. */
    public static final int LICENCE_MUST_OUTLAST_RETURN_BY_DAYS = 0;

    /** Late returns are charged the daily rate multiplied by this. */
    public static final BigDecimal LATE_RETURN_MULTIPLIER = new BigDecimal("1.5");

    /** A pickup may not be recorded more than this many days after the booked date. */
    public static final int MAX_LATE_PICKUP_DAYS = 2;

    /** Damage of this severity or worse takes the vehicle off the road until the report is resolved. */
    public static boolean blocksVehicle(String severity) {
        return "MODERATE".equals(severity) || "SEVERE".equals(severity);
    }

    /** How far ahead an approved booking makes the vehicle RESERVED on the lot. */
    public static final int RESERVE_VEHICLE_DAYS_AHEAD = 1;

    /** Cancelling at least this many days before pickup is free. */
    public static final int FREE_CANCELLATION_DAYS = 2;

    /**
     * Cancelling later than that (online, the day before pickup), or not
     * turning up at all, costs this many nights at the daily rate - never more
     * than the booking itself.
     */
    public static final int LATE_CANCELLATION_FEE_NIGHTS = 1;

    /** An approved booking nobody collected becomes NO_SHOW after this many days (same grace as a late pickup). */
    public static final int NO_SHOW_AFTER_DAYS = MAX_LATE_PICKUP_DAYS;
}
