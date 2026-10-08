package com.vehiclerental.service;

import com.vehiclerental.dto.request.CreateBookingRequest;
import com.vehiclerental.dto.response.BookingResponse;

import java.time.LocalDate;
import java.util.List;

public interface BookingService {

    BookingResponse create(int customerId, CreateBookingRequest request);

    BookingResponse findById(int bookingId);

    List<BookingResponse> listByCustomer(int customerId);

    List<BookingResponse> listPending();

    List<BookingResponse> listAll();

    /** Rentals whose return date has passed and the vehicle is still out. */
    List<BookingResponse> listOverdueReturns();

    /** Approved bookings whose pickup date has passed without anyone collecting the vehicle. */
    List<BookingResponse> listMissedPickups();

    void approve(int bookingId, int approverStaffId);

    void reject(int bookingId, int approverStaffId, String reason);

    /** The customer calls this off themselves. Only allowed before the pickup day. */
    void cancelByCustomer(int bookingId, int callerUserId, String reason);

    /** Staff cancel on the customer's behalf (no-show, fraud, vehicle written off). */
    void cancelByStaff(int bookingId, int staffUserId, String reason);

    void modifyDates(int bookingId, int callerUserId, LocalDate newPickup, LocalDate newReturn);

    /** Approved but never collected. staffId null = closed by the daily run. */
    BookingResponse markNoShow(int bookingId, Integer staffId, String reason);

    /** Closes every approved booking still uncollected after the grace period; returns how many. */
    int closeNoShows(LocalDate asOf);

    /** A later return date for an approved or active rental, re-quoted. */
    BookingResponse extend(int bookingId, int customerId, LocalDate newReturn);

    /**
     * Permanently removes a booking and everything attached to it: its pick-up and
     * return records, resolved damage reports and their claims, and its payment.
     * Refused while the car is out with the customer (record the return first) and
     * while a damage report from the rental is still under review. Administrators
     * only, with a reason; the audit trail keeps what was removed.
     */
    void delete(int bookingId, int actorUserId, String reason);

    com.vehiclerental.dto.response.BookingChargesResponse charges(int bookingId);

    com.vehiclerental.dto.response.PageResponse<BookingResponse> listPage(String status, Integer branchId, String text,
                                                                         Integer page, Integer size);
}
