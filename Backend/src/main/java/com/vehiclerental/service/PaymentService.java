package com.vehiclerental.service;

import com.vehiclerental.dto.response.PaymentResponse;
import com.vehiclerental.model.Payment;

import java.math.BigDecimal;
import java.util.List;

public interface PaymentService {

    Payment createPendingPayment(int bookingId, BigDecimal amount);

    /** Re-quote an unpaid booking after its dates change. */
    void updatePendingAmount(int bookingId, BigDecimal newAmount);

    PaymentResponse findByBooking(int bookingId);

    PaymentResponse findById(int paymentId);

    List<PaymentResponse> listAll();

    List<PaymentResponse> listByStatus(String status);

    void markAsPaid(int paymentId, int staffId);

    /** Reverse a payment. A reason is required — this is an exception, not routine. */
    void markAsPending(int paymentId, int staffId, String reason);

    /**
     * Close the payment of a booking that will never happen. An unpaid balance
     * becomes CANCELLED; money already taken becomes REFUNDED so the refund is
     * visible to staff instead of silently disappearing.
     */
    void closeForCancelledBooking(int bookingId, Integer actorUserId, String reason);

    /**
     * Set what the customer really owes once the vehicle is back (base rental
     * plus any late-return surcharge and damage charge). If they had already
     * paid the original quote and now owe more, the payment goes back to
     * PENDING for the balance.
     */
    void settleFinalAmount(int bookingId, BigDecimal finalAmount, Integer actorUserId, String reason);

    /** True when the booking has been paid in full. */
    boolean isSettled(int bookingId);

    /** The booking total changed mid-way (an extension): follow it, re-opening a paid payment for the difference. */
    void adjustTotal(int bookingId, BigDecimal newTotal, Integer actorUserId, String reason);

    /** Close the payment of a booking that ended without a trip, keeping `charge` (0 = nothing kept). */
    void closeWithCharge(int bookingId, BigDecimal charge, Integer actorUserId, String reason, String label);

    com.vehiclerental.dto.response.PageResponse<PaymentResponse> listPage(String status, Integer branchId, String text,
                                                                         Integer page, Integer size);
}
