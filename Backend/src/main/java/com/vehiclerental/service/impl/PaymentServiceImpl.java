package com.vehiclerental.service.impl;

import com.vehiclerental.dao.BookingDao;
import com.vehiclerental.dao.PaymentDao;
import com.vehiclerental.dto.response.PageResponse;
import com.vehiclerental.dto.response.PaymentResponse;
import com.vehiclerental.enums.BookingStatus;
import com.vehiclerental.enums.PaymentStatus;
import com.vehiclerental.exception.InvalidStatusTransitionException;
import com.vehiclerental.exception.ResourceNotFoundException;
import com.vehiclerental.model.Booking;
import com.vehiclerental.model.Payment;
import com.vehiclerental.security.BranchGuard;
import com.vehiclerental.service.AuditService;
import com.vehiclerental.service.NotificationService;
import com.vehiclerental.service.PaymentService;
import com.vehiclerental.util.AppClock;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
public class PaymentServiceImpl implements PaymentService {

    private final PaymentDao paymentDao;
    private final BookingDao bookingDao;
    private final AuditService auditService;
    private final NotificationService notificationService;
    private final BranchGuard branchGuard;

    public PaymentServiceImpl(PaymentDao paymentDao, BookingDao bookingDao,
                              AuditService auditService, NotificationService notificationService,
                              BranchGuard branchGuard) {
        this.paymentDao = paymentDao;
        this.bookingDao = bookingDao;
        this.auditService = auditService;
        this.notificationService = notificationService;
        this.branchGuard = branchGuard;
    }

    @Override
    @Transactional
    public Payment createPendingPayment(int bookingId, BigDecimal amount) {
        Payment p = new Payment();
        p.setBookingId(bookingId);
        p.setAmount(amount);
        p.setStatus(PaymentStatus.PENDING);
        p.setUpdatedBy(null);
        p.setUpdatedAt(AppClock.now());
        Payment saved = paymentDao.save(p);
        auditService.record("PAYMENT", saved.getPaymentId(), "CREATE", null,
                            "Raised for booking #" + bookingId + " at " + amount);
        return saved;
    }

    @Override
    @Transactional
    public void updatePendingAmount(int bookingId, BigDecimal newAmount) {
        Payment p = loadForBooking(bookingId);
        if (p.getStatus() != PaymentStatus.PENDING) {
            throw new InvalidStatusTransitionException(
                "The payment for booking " + bookingId + " is " + p.getStatus()
                + " and its amount can no longer be changed");
        }
        paymentDao.updateAmount(p.getPaymentId(), newAmount);
        auditService.record("PAYMENT", p.getPaymentId(), "UPDATE", null,
                            "Amount re-quoted from " + p.getAmount() + " to " + newAmount);
    }

    @Override
    public PaymentResponse findByBooking(int bookingId) {
        return toResponse(loadForBooking(bookingId));
    }

    @Override
    public PaymentResponse findById(int paymentId) {
        return toResponse(loadOrThrow(paymentId));
    }

    @Override
    public List<PaymentResponse> listAll() {
        return paymentDao.findAll().stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    @Override
    public List<PaymentResponse> listByStatus(String status) {
        PaymentStatus s = PaymentStatus.valueOf(status.toUpperCase());  // validates
        return paymentDao.findByStatus(s.name()).stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    /**
     * Money can only be taken against a booking that is still going to happen,
     * and only for the amount the booking actually says is owed.
     */
    @Override
    @Transactional
    public void markAsPaid(int paymentId, int staffId) {
        Payment p = loadOrThrow(paymentId);

        if (p.getStatus() == PaymentStatus.PAID) {
            throw new InvalidStatusTransitionException("Payment " + paymentId + " is already PAID");
        }
        if (p.getStatus() == PaymentStatus.CANCELLED || p.getStatus() == PaymentStatus.REFUNDED) {
            throw new InvalidStatusTransitionException(
                "Payment " + paymentId + " is " + p.getStatus() + " and cannot be collected");
        }

        Booking b = bookingDao.findById(p.getBookingId())
            .orElseThrow(() -> new ResourceNotFoundException("Booking not found: " + p.getBookingId()));
        branchGuard.requireStaffAtBranch(staffId, b.getPickupBranchId(), "take payment for this booking");

        // A closed booking owes nothing - unless a late-cancellation or
        // no-show charge was raised on it, which is then what is collected.
        boolean closed = b.getStatus() == BookingStatus.CANCELLED || b.getStatus() == BookingStatus.REJECTED
                      || b.getStatus() == BookingStatus.NO_SHOW;
        boolean chargeOnClosed = closed && p.getNote() != null;
        if (closed && !chargeOnClosed) {
            throw new InvalidStatusTransitionException(
                "Booking #" + b.getBookingId() + " is " + b.getStatus() + " — no payment is due for it");
        }
        if (p.getAmount() == null || p.getAmount().compareTo(BigDecimal.ZERO) <= 0) {
            throw new InvalidStatusTransitionException("There is nothing to collect on this booking");
        }

        // The amount on the payment must still match what the booking says is owed.
        BigDecimal owed = b.getFinalCost() != null ? b.getFinalCost() : b.getEstimatedCost();
        if (!chargeOnClosed && owed != null && owed.compareTo(p.getAmount()) != 0) {
            throw new InvalidStatusTransitionException(
                "The payment amount (" + p.getAmount() + ") no longer matches the booking total ("
                + owed + "). Reload the booking before collecting.");
        }

        paymentDao.updateStatus(paymentId, PaymentStatus.PAID.name(), staffId);
        auditService.recordStatusChange("PAYMENT", paymentId, p.getStatus().name(),
                                        PaymentStatus.PAID.name(), staffId, "Collected at the counter");
        notificationService.safeSend(b.getCustomerId(), "PAYMENT_RECEIVED",
            "We have received your payment of " + p.getAmount()
            + " for booking #" + b.getBookingId() + ".");
    }

    @Override
    @Transactional
    public void markAsPending(int paymentId, int staffId, String reason) {
        Payment p = loadOrThrow(paymentId);
        if (p.getStatus() == PaymentStatus.PENDING) {
            throw new InvalidStatusTransitionException("Payment " + paymentId + " is already PENDING");
        }
        if (reason == null || reason.isBlank()) {
            throw new IllegalArgumentException("Reversing a payment requires a reason");
        }
        bookingDao.findById(p.getBookingId()).ifPresent(b ->
            branchGuard.requireStaffAtBranch(staffId, b.getPickupBranchId(), "change payments for this booking"));
        paymentDao.updateStatus(paymentId, PaymentStatus.PENDING.name(), staffId);
        auditService.recordStatusChange("PAYMENT", paymentId, p.getStatus().name(),
                                        PaymentStatus.PENDING.name(), staffId, reason);
    }

    @Override
    @Transactional
    public void closeForCancelledBooking(int bookingId, Integer actorUserId, String reason) {
        Optional<Payment> maybe = paymentDao.findByBooking(bookingId);
        if (maybe.isEmpty()) {
            return;     // nothing was ever raised
        }
        Payment p = maybe.get();
        if (p.getStatus() == PaymentStatus.CANCELLED || p.getStatus() == PaymentStatus.REFUNDED) {
            return;     // already closed
        }

        PaymentStatus target = p.getStatus() == PaymentStatus.PAID
                ? PaymentStatus.REFUNDED    // money was taken, so it has to go back
                : PaymentStatus.CANCELLED;  // nothing was ever taken

        paymentDao.updateStatus(p.getPaymentId(), target.name(), actorUserId);
        auditService.recordStatusChange("PAYMENT", p.getPaymentId(), p.getStatus().name(),
                                        target.name(), actorUserId, reason);

        if (target == PaymentStatus.REFUNDED) {
            bookingDao.findById(bookingId).ifPresent(b ->
                notificationService.safeSend(b.getCustomerId(), "PAYMENT_REFUND_DUE",
                    "Booking #" + bookingId + " was cancelled. A refund of " + p.getAmount()
                    + " is being processed."));
        }
    }

    @Override
    @Transactional
    public void settleFinalAmount(int bookingId, BigDecimal finalAmount, Integer actorUserId, String reason) {
        Payment p = loadForBooking(bookingId);

        if (p.getStatus() == PaymentStatus.CANCELLED || p.getStatus() == PaymentStatus.REFUNDED) {
            return;     // the booking never happened; nothing to settle
        }
        if (p.getAmount() != null && p.getAmount().compareTo(finalAmount) == 0
                && p.getStatus() == PaymentStatus.PAID) {
            return;     // already paid exactly this
        }

        boolean owesMore = p.getStatus() == PaymentStatus.PAID
                && p.getAmount() != null
                && finalAmount.compareTo(p.getAmount()) > 0;

        paymentDao.updateAmount(p.getPaymentId(), finalAmount);

        if (owesMore) {
            // They paid the quote, but the trip ended up costing more. Re-open the balance.
            paymentDao.updateStatus(p.getPaymentId(), PaymentStatus.PENDING.name(), actorUserId);
            auditService.recordStatusChange("PAYMENT", p.getPaymentId(), PaymentStatus.PAID.name(),
                                            PaymentStatus.PENDING.name(), actorUserId,
                                            "Balance due after return: " + reason);
            bookingDao.findById(bookingId).ifPresent(b ->
                notificationService.safeSend(b.getCustomerId(), "PAYMENT_BALANCE_DUE",
                    "Booking #" + bookingId + " finished at " + finalAmount
                    + ". There is a balance to settle at the branch."));
        } else {
            auditService.record("PAYMENT", p.getPaymentId(), "UPDATE", actorUserId,
                                "Final amount set to " + finalAmount + " (" + reason + ")");
        }
    }

    /**
     * A booking's total changed before the trip ended (an extension). Unpaid,
     * the amount just follows. Paid, the extra becomes a balance due at the
     * counter - the payment goes back to PENDING for the new total.
     */
    @Override
    @Transactional
    public void adjustTotal(int bookingId, BigDecimal newTotal, Integer actorUserId, String reason) {
        Payment p = loadForBooking(bookingId);
        if (p.getStatus() == PaymentStatus.PENDING) {
            paymentDao.updateAmount(p.getPaymentId(), newTotal);
            auditService.record("PAYMENT", p.getPaymentId(), "UPDATE", actorUserId,
                                reason + ": amount " + p.getAmount() + " -> " + newTotal);
            return;
        }
        if (p.getStatus() == PaymentStatus.PAID && newTotal.compareTo(p.getAmount()) > 0) {
            BigDecimal extra = newTotal.subtract(p.getAmount());
            paymentDao.updateAmount(p.getPaymentId(), newTotal);
            paymentDao.updateStatus(p.getPaymentId(), PaymentStatus.PENDING.name(), actorUserId);
            auditService.recordStatusChange("PAYMENT", p.getPaymentId(), PaymentStatus.PAID.name(),
                PaymentStatus.PENDING.name(), actorUserId, reason + ": " + extra + " more to settle");
            bookingDao.findById(bookingId).ifPresent(b ->
                notificationService.safeSend(b.getCustomerId(), "PAYMENT_BALANCE_DUE",
                    "Booking #" + bookingId + " was extended. " + extra + " more is due, payable at the branch."));
        }
    }

    /**
     * Closes the payment of a booking that ended without a trip, keeping a
     * charge (late cancellation, no-show). With nothing to charge it is the
     * ordinary close. Paid money comes back minus the charge; unpaid, the
     * charge itself becomes what is owed.
     */
    @Override
    @Transactional
    public void closeWithCharge(int bookingId, BigDecimal charge, Integer actorUserId, String reason, String label) {
        if (charge == null || charge.compareTo(BigDecimal.ZERO) <= 0) {
            closeForCancelledBooking(bookingId, actorUserId, reason);
            return;
        }
        Payment p = paymentDao.findByBooking(bookingId).orElse(null);
        if (p == null || p.getStatus() == PaymentStatus.CANCELLED || p.getStatus() == PaymentStatus.REFUNDED) {
            return;
        }
        // Never more than the booking itself was worth.
        BigDecimal kept = p.getAmount() == null ? charge : charge.min(p.getAmount());
        String note = label + " " + kept;
        Booking b = bookingDao.findById(bookingId).orElse(null);

        if (p.getStatus() == PaymentStatus.PAID) {
            BigDecimal refund = p.getAmount().subtract(kept).max(BigDecimal.ZERO);
            paymentDao.updateRefund(p.getPaymentId(), refund, note);
            paymentDao.updateStatus(p.getPaymentId(), PaymentStatus.REFUNDED.name(), actorUserId);
            auditService.recordStatusChange("PAYMENT", p.getPaymentId(), PaymentStatus.PAID.name(),
                PaymentStatus.REFUNDED.name(), actorUserId, reason + " - " + note + ", refund " + refund);
            if (b != null) {
                notificationService.safeSend(b.getCustomerId(), "PAYMENT_REFUND_DUE",
                    "Booking #" + bookingId + " was closed. " + label + " of " + kept
                    + " applies; a refund of " + refund + " is being processed.");
            }
        } else {
            paymentDao.updateAmount(p.getPaymentId(), kept);
            paymentDao.updateNote(p.getPaymentId(), note);
            auditService.record("PAYMENT", p.getPaymentId(), "UPDATE", actorUserId, reason + " - " + note);
            if (b != null) {
                notificationService.safeSend(b.getCustomerId(), "PAYMENT_BALANCE_DUE",
                    "Booking #" + bookingId + " was closed. " + label + " of " + kept
                    + " is due, payable at the branch.");
            }
        }
    }

    @Override
    public PageResponse<PaymentResponse> listPage(String status, Integer branchId, String text, Integer page, Integer size) {
        int s = PageResponse.size(size);
        int pg = PageResponse.page(page);
        String st = status == null || status.isBlank() || "all".equalsIgnoreCase(status) ? null : status.toUpperCase();
        List<PaymentResponse> items = paymentDao.findPage(st, branchId, text, (pg - 1) * s, s).stream()
            .map(this::toResponse).collect(Collectors.toList());
        java.util.Map<String, Long> counts = new java.util.LinkedHashMap<>();
        for (PaymentStatus ps : PaymentStatus.values()) {
            counts.put(ps.name(), paymentDao.count(ps.name(), branchId, text));
        }
        counts.put("all", paymentDao.count(null, branchId, text));
        return new PageResponse<>(items, pg, s, paymentDao.count(st, branchId, text), counts);
    }

    @Override
    public boolean isSettled(int bookingId) {
        return paymentDao.findByBooking(bookingId)
                .map(p -> p.getStatus() == PaymentStatus.PAID)
                .orElse(false);
    }

    // ---------- helpers ----------
    private Payment loadOrThrow(int paymentId) {
        return paymentDao.findById(paymentId)
                .orElseThrow(() -> new ResourceNotFoundException("Payment not found: " + paymentId));
    }

    private Payment loadForBooking(int bookingId) {
        return paymentDao.findByBooking(bookingId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "No payment record for booking: " + bookingId));
    }

    private PaymentResponse toResponse(Payment p) {
        PaymentResponse r = new PaymentResponse();
        r.setPaymentId(p.getPaymentId());
        r.setBookingId(p.getBookingId());
        r.setAmount(p.getAmount());
        r.setStatus(p.getStatus().name());
        r.setUpdatedBy(p.getUpdatedBy());
        r.setUpdatedAt(p.getUpdatedAt());
        r.setRefundAmount(p.getRefundAmount());
        r.setNote(p.getNote());
        return r;
    }
}
