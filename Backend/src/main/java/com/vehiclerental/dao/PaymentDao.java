package com.vehiclerental.dao;

import com.vehiclerental.model.Payment;

import java.util.List;
import java.util.Optional;

public interface PaymentDao {
    Payment save(Payment payment);
    Optional<Payment> findById(int paymentId);
    Optional<Payment> findByBooking(int bookingId);
    List<Payment> findByStatus(String status);
    List<Payment> findAll();
    void updateStatus(int paymentId, String newStatus, Integer updatedByStaffId);
    void updateAmount(int paymentId, java.math.BigDecimal newAmount);
    void updateRefund(int paymentId, java.math.BigDecimal refundAmount, String note);
    void updateNote(int paymentId, String note);
    /** One page of payments, newest change first (C1). branchId null = every branch. */
    List<Payment> findPage(String status, Integer branchId, String text, int offset, int limit);
    long count(String status, Integer branchId, String text);
}
