package com.vehiclerental.service;

import com.vehiclerental.model.Vehicle;

import java.time.LocalDate;
import java.util.List;

/**
 * One place that answers "can this vehicle be rented out over these dates?".
 *
 * Booking creation, date changes, approval, maintenance scheduling, branch
 * transfers and manual status changes all ask the same questions here, so the
 * answer cannot drift between them.
 */
public interface AvailabilityService {

    /**
     * Throws a descriptive exception unless the vehicle can be rented for the
     * whole of [pickup, returnDate].
     *
     * @param excludeBookingId a booking to ignore when looking for clashes
     *                         (the one being modified), or null
     */
    void requireBookable(Vehicle vehicle, LocalDate pickup, LocalDate returnDate,
                         Integer excludeBookingId);

    /** Same checks, as a plain yes/no. */
    boolean isBookable(Vehicle vehicle, LocalDate pickup, LocalDate returnDate,
                       Integer excludeBookingId);

    /**
     * Throws unless the vehicle has no live booking at all that overlaps
     * [from, to]. Used before taking a vehicle off the road for maintenance,
     * a branch transfer or a manual status change.
     */
    void requireNoLiveBookings(int vehicleId, LocalDate from, LocalDate to, String action);

    /**
     * Keeps vehicle.status in step with its bookings:
     * a car with an approved pickup due in the next day or so is RESERVED,
     * otherwise a free car is AVAILABLE. Cars that are RENTED, in the workshop
     * or written off are left alone.
     */
    void refreshReservationStatus(int vehicleId);

    /** A stretch of days a car cannot be booked, and why (public: no customer details). */
    record BusyRange(java.time.LocalDate from, java.time.LocalDate to, String kind) { }

    /**
     * Every day between from and to that this car cannot start or be part of a
     * rental (E1): bookings, workshop windows, a transfer away, days without
     * insurance cover, or the whole range when it is off the road.
     */
    List<BusyRange> busy(int vehicleId, LocalDate from, LocalDate to);

    /**
     * The first pickup date on or after `from` from which the car is free for
     * `nights` nights (E2), or null when there is none inside the booking horizon.
     */
    LocalDate nextFree(int vehicleId, int nights, LocalDate from);
}
