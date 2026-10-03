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

    com.vehiclerental.dto.response.BookingChargesResponse charges(int bookingId);

    com.vehiclerental.dto.response.PageResponse<BookingResponse> listPage(String status, Integer branchId, String text,
                                                                         Integer page, Integer size);
}
