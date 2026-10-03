package com.vehiclerental.dao;

import com.vehiclerental.model.Booking;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface BookingDao {

    Booking save(Booking booking);

    Optional<Booking> findById(int bookingId);

    List<Booking> findAll();

    List<Booking> findByCustomer(int customerId);

    List<Booking> findByStatus(String status);

    /** Every booking ever made for a vehicle, live or not. */
    List<Booking> findByVehicle(int vehicleId);

    /** Any 'live' booking (not cancelled/rejected/completed) that overlaps the date range. */
    List<Booking> findOverlapping(int vehicleId, LocalDate pickup, LocalDate returnDate);

    /**
     * Same as above but ignoring one booking — used when a customer changes the
     * dates of a booking that already exists.
     */
    List<Booking> findOverlapping(int vehicleId, LocalDate pickup, LocalDate returnDate,
                                  Integer excludeBookingId);

    /**
     * Live bookings the SAME CUSTOMER already has in that window, on any vehicle.
     * One person cannot drive two cars at once.
     */
    List<Booking> findOverlappingForCustomer(int customerId, LocalDate pickup, LocalDate returnDate,
                                             Integer excludeBookingId);

    /** How many bookings this customer has open right now (pending/approved/active). */
    int countLiveByCustomer(int customerId);

    /** Every live booking for a vehicle — used before maintenance, transfers and status changes. */
    List<Booking> findLiveByVehicle(int vehicleId);

    /** Live bookings for a vehicle that overlap a date window. */
    List<Booking> findLiveByVehicleInWindow(int vehicleId, LocalDate from, LocalDate to);

    /** ACTIVE_RENTAL bookings whose return date has already passed. */
    List<Booking> findOverdueReturns(LocalDate asOf);

    /** APPROVED bookings whose pickup date has already passed — the customer never showed up. */
    List<Booking> findMissedPickups(LocalDate asOf);

    /** ACTIVE_RENTAL bookings due back on a given day (used for reminders). */
    List<Booking> findReturnsDueOn(LocalDate day);

    /** APPROVED bookings for a vehicle with a pickup due on or before the given day. */
    List<Booking> findApprovedPickupsDueBy(int vehicleId, LocalDate day);

    void updateStatus(int bookingId, String newStatus);

    /** One page for the console (C1): status null or "all" = every status; branchId null = every branch. */
    List<Booking> findPage(String status, Integer branchId, String text, int offset, int limit);
    java.util.Map<String, Long> countByStatus(Integer branchId, String text);
    List<Booking> findLiveInWindow(LocalDate from, LocalDate to, Integer branchId);
    List<Booking> findLiveByVehicleFrom(int vehicleId, LocalDate from);

    void updateApprover(int bookingId, Integer approverId);

    void updateDates(int bookingId, LocalDate pickup, LocalDate returnDate, BigDecimal newCost);

    /** Written when the vehicle comes back: real return date and what the customer owes. */
    void updateReturnOutcome(int bookingId, LocalDate actualReturnDate, BigDecimal finalCost);

    void updateFinalCost(int bookingId, BigDecimal finalCost);
}
