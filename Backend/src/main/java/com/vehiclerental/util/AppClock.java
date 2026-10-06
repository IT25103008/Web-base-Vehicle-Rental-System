package com.vehiclerental.util;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;

/**
 * The single clock for the whole application.
 *
 * Every insert passes its own timestamp taken from here, so all dates and
 * times in the database come from one source and are directly comparable.
 *
 * It reads the time in ONE configured zone (rental.zone, Sri Lanka by
 * default), not whatever zone the server happens to run in. Without that, a
 * cloud host on UTC would think "today" starts at 05:30 in Colombo, and every
 * rule phrased in days - cancel up to the day before, no early pickup, late
 * returns - would be off by one for five and a half hours each morning.
 */
public final class AppClock {

    public static final String DEFAULT_ZONE = "Asia/Colombo";

    private static volatile ZoneId zone = ZoneId.of(DEFAULT_ZONE);

    private AppClock() {
    }

    /** Set once at start-up from rental.zone (see ClockConfig). */
    public static void setZone(ZoneId newZone) {
        zone = newZone;
    }

    public static ZoneId zone() {
        return zone;
    }

    public static LocalDateTime now() {
        return LocalDateTime.now(zone);
    }

    public static LocalDate today() {
        return LocalDate.now(zone);
    }
}
