package com.vehiclerental.controller;

import com.vehiclerental.dto.request.ReasonRequest;
import com.vehiclerental.dto.response.BookingResponse;
import com.vehiclerental.dto.response.PageResponse;
import com.vehiclerental.dto.response.PaymentResponse;
import com.vehiclerental.security.AppUserPrincipal;
import com.vehiclerental.security.BranchGuard;
import com.vehiclerental.service.BookingService;
import com.vehiclerental.service.PaymentService;
import com.vehiclerental.validation.Rules;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/payments")
@PreAuthorize("hasAnyRole('STAFF', 'ADMINISTRATOR')")
public class PaymentController {

    private final PaymentService paymentService;
    private final BookingService bookingService;
    private final BranchGuard branchGuard;

    public PaymentController(PaymentService paymentService, BookingService bookingService, BranchGuard branchGuard) {
        this.paymentService = paymentService;
        this.bookingService = bookingService;
        this.branchGuard = branchGuard;
    }

    @GetMapping("/{id}")
    public PaymentResponse findById(@PathVariable int id) {
        return paymentService.findById(id);
    }

    @GetMapping("/booking/{bookingId}")
    public PaymentResponse findByBooking(@PathVariable int bookingId) {
        return paymentService.findByBooking(bookingId);
    }

    /** Staff see their own branch's payments; an administrator sees every branch. */
    @GetMapping
    public List<PaymentResponse> all(@RequestParam(required = false) String status,
                                     @AuthenticationPrincipal AppUserPrincipal me) {
        List<PaymentResponse> list = status == null || status.isBlank()
            ? paymentService.listAll() : paymentService.listByStatus(status);
        Integer scope = branchGuard.scopeFor(me.getUserId());
        if (scope == null) {
            return list;
        }
        Set<Integer> ownBookings = bookingService.listAll().stream()
            .filter(b -> b.getPickupBranchId() == scope)
            .map(BookingResponse::getBookingId).collect(Collectors.toSet());
        return list.stream().filter(p -> ownBookings.contains(p.getBookingId())).collect(Collectors.toList());
    }

    /** One page, with per-status counts for the tabs (C1). */
    @GetMapping("/page")
    public PageResponse<PaymentResponse> page(@RequestParam(required = false) String status,
                                              @RequestParam(required = false) @Size(max = Rules.SEARCH_TEXT, message = "Search text is too long") String q,
                                              @RequestParam(required = false) Integer branchId,
                                              @RequestParam(required = false) Integer page,
                                              @RequestParam(required = false) Integer size,
                                              @AuthenticationPrincipal AppUserPrincipal me) {
        Integer scope = branchGuard.scopeFor(me.getUserId());
        return paymentService.listPage(status, scope != null ? scope : branchId, q, page, size);
    }

    @PatchMapping("/{id}/paid")
    public PaymentResponse markPaid(@PathVariable int id,
                                    @AuthenticationPrincipal AppUserPrincipal me) {
        paymentService.markAsPaid(id, me.getUserId());
        return paymentService.findById(id);
    }

    // Reversing a payment needs a reason: it is an exception, and the audit
    // trail has to say why it happened.
    @PatchMapping("/{id}/pending")
    public PaymentResponse markPending(@PathVariable int id,
                                       @RequestParam(required = false) String reason,
                                       @Valid @RequestBody(required = false) ReasonRequest body,
                                       @AuthenticationPrincipal AppUserPrincipal me) {
        paymentService.markAsPending(id, me.getUserId(), BookingController.reasonOf(body, reason));
        return paymentService.findById(id);
    }
}
