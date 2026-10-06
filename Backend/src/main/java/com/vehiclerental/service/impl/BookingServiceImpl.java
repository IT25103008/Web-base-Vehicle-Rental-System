package com.vehiclerental.service.impl;

import com.vehiclerental.dao.*;
import com.vehiclerental.dto.request.CreateBookingRequest;
import com.vehiclerental.dto.response.BookingChargesResponse;
import com.vehiclerental.dto.response.BookingResponse;
import com.vehiclerental.dto.response.PageResponse;
import com.vehiclerental.enums.BookingStatus;
import com.vehiclerental.enums.NotificationChannel;
import com.vehiclerental.exception.DoubleBookingException;
import com.vehiclerental.exception.InvalidStatusTransitionException;
import com.vehiclerental.exception.ResourceNotFoundException;
import com.vehiclerental.exception.UnauthorizedActionException;
import com.vehiclerental.model.*;
import com.vehiclerental.security.BranchGuard;
import com.vehiclerental.service.*;
import com.vehiclerental.util.AppClock;
import com.vehiclerental.util.CostCalculator;
import com.vehiclerental.util.DateRangeValidator;
import com.vehiclerental.util.RentalPolicy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class BookingServiceImpl implements BookingService {

    private static final String ENTITY = "BOOKING";

    private final BookingDao bookingDao;
    private final VehicleDao vehicleDao;
    private final BranchDao branchDao;
    private final NotificationService notificationService;
    private final PaymentService paymentService;
    private final AvailabilityService availabilityService;
    private final UserService userService;
    private final AuditService auditService;
    private final UserDao userDao;
    private final BranchGuard branchGuard;
    private final HandoverDao handoverDao;
    private final DamageReportDao damageReportDao;
    private final InsuranceClaimDao claimDao;

    public BookingServiceImpl(BookingDao bookingDao,
                              VehicleDao vehicleDao,
                              BranchDao branchDao,
                              NotificationService notificationService,
                              PaymentService paymentService,
                              AvailabilityService availabilityService,
                              UserService userService,
                              AuditService auditService,
                              UserDao userDao,
                              BranchGuard branchGuard,
                              HandoverDao handoverDao,
                              DamageReportDao damageReportDao,
                              InsuranceClaimDao claimDao) {
        this.userDao = userDao;
        this.branchGuard = branchGuard;
        this.handoverDao = handoverDao;
        this.damageReportDao = damageReportDao;
        this.claimDao = claimDao;
        this.bookingDao = bookingDao;
        this.vehicleDao = vehicleDao;
        this.branchDao = branchDao;
        this.notificationService = notificationService;
        this.paymentService = paymentService;
        this.availabilityService = availabilityService;
        this.userService = userService;
        this.auditService = auditService;
    }

    // ============================================================
    // Create
    // ============================================================
    @Override
    @Transactional
    public BookingResponse create(int customerId, CreateBookingRequest r) {

        // 1. Dates must be sane and inside the company's booking window.
        DateRangeValidator.validateBookingWindow(r.getPickupDate(), r.getReturnDate());

        // 2. The customer must be allowed to drive: enabled account, old enough,
        //    licence verified and valid for the whole trip.
        userService.requireEligibleToBook(customerId, r.getPickupDate(), r.getReturnDate());

        // Lock the customer, then the car, until this transaction ends. Every
        // booking change takes the same two locks in the same order, so two
        // requests for the same car (or by the same person) are checked one
        // after the other: the second sees the first's booking and is refused.
        // Without this, both could pass the clash check before either saved.
        userDao.lockForUpdate(customerId);
        vehicleDao.lockForUpdate(r.getVehicleId());

        // 3. One person cannot be out in two cars at once, and cannot hoard the fleet.
        List<Booking> ownClashes = bookingDao.findOverlappingForCustomer(
            customerId, r.getPickupDate(), r.getReturnDate(), null);
        if (!ownClashes.isEmpty()) {
            Booking c = ownClashes.get(0);
            throw new DoubleBookingException(
                "You already have booking #" + c.getBookingId() + " from " + c.getPickupDate()
                + " to " + c.getReturnDate() + " covering these dates");
        }
        if (bookingDao.countLiveByCustomer(customerId) >= RentalPolicy.MAX_LIVE_BOOKINGS_PER_CUSTOMER) {
            throw new IllegalArgumentException(
                "You already have " + RentalPolicy.MAX_LIVE_BOOKINGS_PER_CUSTOMER
                + " open bookings. Complete or cancel one before making another.");
        }

        // 4. The vehicle must exist and be rentable for exactly these dates.
        Vehicle v = vehicleDao.findById(r.getVehicleId())
            .orElseThrow(() -> new ResourceNotFoundException("Vehicle not found: " + r.getVehicleId()));
        availabilityService.requireBookable(v, r.getPickupDate(), r.getReturnDate(), null);

        // 5. The vehicle has to be collected from the branch that actually holds it.
        Branch branch = branchDao.findById(r.getPickupBranchId())
            .orElseThrow(() -> new ResourceNotFoundException("Branch not found: " + r.getPickupBranchId()));
        if (!"ACTIVE".equals(branch.getStatus())) {
            throw new IllegalArgumentException("Pickup branch is not open for business");
        }
        if (v.getBranchId() != branch.getBranchId()) {
            throw new IllegalArgumentException(
                "This vehicle is kept at a different branch. Choose the branch it is parked at, "
                + "or pick another vehicle at " + branch.getName() + ".");
        }

        // 6. Quote the price.
        BigDecimal cost = CostCalculator.totalCost(
            v.getRentalPricePerDay(), r.getPickupDate(), r.getReturnDate());

        // 7. Save the booking (starts as PENDING_APPROVAL).
        Booking b = new Booking();
        b.setCustomerId(customerId);
        b.setVehicleId(v.getVehicleId());
        b.setPickupBranchId(r.getPickupBranchId());
        b.setPickupDate(r.getPickupDate());
        b.setReturnDate(r.getReturnDate());
        b.setSpecialRequests(r.getSpecialRequests());
        b.setEstimatedCost(cost);
        b.setStatus(BookingStatus.PENDING_APPROVAL);
        b.setSubmittedDate(AppClock.now());

        Booking saved = bookingDao.save(b);

        // 8. Raise the matching PENDING payment.
        paymentService.createPendingPayment(saved.getBookingId(), saved.getEstimatedCost());

        auditService.recordStatusChange(ENTITY, saved.getBookingId(), null,
                                        BookingStatus.PENDING_APPROVAL.name(), customerId,
                                        "Booking submitted by the customer");

        // 9. Tell the customer, and tell the branch there is something to approve.
        notificationService.safeSend(customerId, "BOOKING_SUBMITTED",
            "Your booking #" + saved.getBookingId() + " has been submitted and is pending approval.",
            NotificationChannel.WEBSITE);
        notificationService.safeSendToBranchStaff(branch.getBranchId(), "BOOKING_AWAITING_APPROVAL",
            "Booking #" + saved.getBookingId() + " is waiting for approval ("
            + v.getModel() + ", " + r.getPickupDate() + " to " + r.getReturnDate() + ").");

        return toResponse(loadOrThrow(saved.getBookingId()));
    }

    // ============================================================
    // Read
    // ============================================================
    @Override
    public BookingResponse findById(int bookingId) {
        return toResponse(loadOrThrow(bookingId));
    }

    @Override
    public List<BookingResponse> listByCustomer(int customerId) {
        return bookingDao.findByCustomer(customerId).stream()
            .map(this::toResponse).collect(Collectors.toList());
    }

    @Override
    public List<BookingResponse> listPending() {
        return bookingDao.findByStatus(BookingStatus.PENDING_APPROVAL.name()).stream()
            .map(this::toResponse).collect(Collectors.toList());
    }

    @Override
    public List<BookingResponse> listAll() {
        return bookingDao.findAll().stream().map(this::toResponse).collect(Collectors.toList());
    }

    @Override
    public List<BookingResponse> listOverdueReturns() {
        return bookingDao.findOverdueReturns(AppClock.today()).stream()
            .map(this::toResponse).collect(Collectors.toList());
    }

    @Override
    public List<BookingResponse> listMissedPickups() {
        return bookingDao.findMissedPickups(AppClock.today()).stream()
            .map(this::toResponse).collect(Collectors.toList());
    }

    // ============================================================
    // State transitions
    // ============================================================

    /**
     * Approval is not a rubber stamp. Days may have passed since the booking was
     * submitted, so everything is checked again before the customer is promised
     * the vehicle.
     */
    @Override
    @Transactional
    public void approve(int bookingId, int approverStaffId) {
        Booking b = loadOrThrow(bookingId);
        requireStatus(b, BookingStatus.PENDING_APPROVAL, "approve");
        branchGuard.requireStaffAtBranch(approverStaffId, b.getPickupBranchId(), "approve it");
        vehicleDao.lockForUpdate(b.getVehicleId());

        if (b.getPickupDate().isBefore(AppClock.today())) {
            throw new InvalidStatusTransitionException(
                "The pickup date (" + b.getPickupDate() + ") has already passed. "
                + "Ask the customer to book new dates.");
        }

        Vehicle v = vehicleDao.findById(b.getVehicleId())
            .orElseThrow(() -> new ResourceNotFoundException("Vehicle not found: " + b.getVehicleId()));

        // Re-run every availability rule, ignoring this booking itself.
        availabilityService.requireBookable(v, b.getPickupDate(), b.getReturnDate(), bookingId);

        // And re-check the customer: their licence may have expired in the meantime.
        userService.requireEligibleToBook(b.getCustomerId(), b.getPickupDate(), b.getReturnDate());

        bookingDao.updateStatus(bookingId, BookingStatus.APPROVED.name());
        bookingDao.updateApprover(bookingId, approverStaffId);
        availabilityService.refreshReservationStatus(b.getVehicleId());

        auditService.recordStatusChange(ENTITY, bookingId, BookingStatus.PENDING_APPROVAL.name(),
                                        BookingStatus.APPROVED.name(), approverStaffId, null);
        notificationService.safeSend(b.getCustomerId(), "BOOKING_APPROVED",
            "Your booking #" + bookingId + " has been approved. Payment is due before collection.");
    }

    @Override
    @Transactional
    public void reject(int bookingId, int approverStaffId, String reason) {
        Booking b = loadOrThrow(bookingId);
        requireStatus(b, BookingStatus.PENDING_APPROVAL, "reject");
        branchGuard.requireStaffAtBranch(approverStaffId, b.getPickupBranchId(), "reject it");

        // approved_by is left alone: a rejection is not an approval. Who rejected
        // it, when and why lives in the audit trail.
        bookingDao.updateStatus(bookingId, BookingStatus.REJECTED.name());
        paymentService.closeForCancelledBooking(bookingId, approverStaffId,
                                                "Booking rejected: " + safeReason(reason));
        availabilityService.refreshReservationStatus(b.getVehicleId());

        auditService.recordStatusChange(ENTITY, bookingId, BookingStatus.PENDING_APPROVAL.name(),
                                        BookingStatus.REJECTED.name(), approverStaffId, safeReason(reason));
        notificationService.safeSend(b.getCustomerId(), "BOOKING_REJECTED",
            "Your booking #" + bookingId + " was rejected. " + safeReason(reason));
    }

    @Override
    @Transactional
    public void cancelByCustomer(int bookingId, int callerUserId, String reason) {
        Booking b = loadOrThrow(bookingId);

        if (b.getCustomerId() != callerUserId) {
            throw new UnauthorizedActionException("Only the booking's customer can cancel it");
        }
        if (b.getStatus() != BookingStatus.PENDING_APPROVAL && b.getStatus() != BookingStatus.APPROVED) {
            throw new InvalidStatusTransitionException(
                "A booking that is " + b.getStatus() + " cannot be cancelled online");
        }
        // Cancelling on the day the vehicle is due out leaves the branch with a
        // car nobody else can book, so it has to be done at the counter.
        if (!b.getPickupDate().isAfter(AppClock.today())) {
            throw new InvalidStatusTransitionException(
                "Bookings can only be cancelled online up to the day before pickup. "
                + "Please call the branch.");
        }

        // Free until FREE_CANCELLATION_DAYS before pickup; later than that one
        // night is kept, because the car was held and can rarely be re-let in time.
        long daysAhead = java.time.temporal.ChronoUnit.DAYS.between(AppClock.today(), b.getPickupDate());
        BigDecimal charge = daysAhead >= RentalPolicy.FREE_CANCELLATION_DAYS ? BigDecimal.ZERO : lateCharge(b);
        doCancel(b, callerUserId, safeReason(reason), "Cancelled by the customer", charge);
    }

    @Override
    @Transactional
    public void cancelByStaff(int bookingId, int staffUserId, String reason) {
        Booking b = loadOrThrow(bookingId);

        if (reason == null || reason.isBlank()) {
            throw new IllegalArgumentException("Cancelling a customer's booking requires a reason");
        }
        if (b.getStatus() == BookingStatus.ACTIVE_RENTAL) {
            throw new InvalidStatusTransitionException(
                "The vehicle is out with the customer. Record the return first.");
        }
        if (b.getStatus() != BookingStatus.PENDING_APPROVAL && b.getStatus() != BookingStatus.APPROVED) {
            throw new InvalidStatusTransitionException(
                "A booking that is " + b.getStatus() + " cannot be cancelled");
        }

        branchGuard.requireStaffAtBranch(staffUserId, b.getPickupBranchId(), "cancel it");
        doCancel(b, staffUserId, reason, "Cancelled by staff", BigDecimal.ZERO);
    }

    @Override
    @Transactional
    public void modifyDates(int bookingId, int callerUserId, LocalDate newPickup, LocalDate newReturn) {
        Booking b = loadOrThrow(bookingId);

        if (b.getCustomerId() != callerUserId) {
            throw new UnauthorizedActionException("Only the booking's customer can modify it");
        }
        if (b.getStatus() != BookingStatus.PENDING_APPROVAL) {
            throw new InvalidStatusTransitionException(
                "Only bookings that are still awaiting approval can have their dates changed");
        }

        DateRangeValidator.validateBookingWindow(newPickup, newReturn);
        userService.requireEligibleToBook(callerUserId, newPickup, newReturn);
        userDao.lockForUpdate(callerUserId);
        vehicleDao.lockForUpdate(b.getVehicleId());
        List<Booking> ownClashes = bookingDao.findOverlappingForCustomer(
            callerUserId, newPickup, newReturn, bookingId);
        if (!ownClashes.isEmpty()) {
            throw new DoubleBookingException(
                "You already have another booking covering those dates");
        }

        Vehicle v = vehicleDao.findById(b.getVehicleId())
            .orElseThrow(() -> new ResourceNotFoundException("Vehicle not found: " + b.getVehicleId()));

        // The full availability rule set, ignoring this booking.
        availabilityService.requireBookable(v, newPickup, newReturn, bookingId);

        BigDecimal newCost = CostCalculator.totalCost(v.getRentalPricePerDay(), newPickup, newReturn);
        bookingDao.updateDates(bookingId, newPickup, newReturn, newCost);
        paymentService.updatePendingAmount(bookingId, newCost);

        auditService.record(ENTITY, bookingId, "UPDATE", callerUserId,
            "Dates changed from " + b.getPickupDate() + ".." + b.getReturnDate()
            + " to " + newPickup + ".." + newReturn + "; quote " + b.getEstimatedCost() + " -> " + newCost);
        notificationService.safeSendToBranchStaff(b.getPickupBranchId(), "BOOKING_DATES_CHANGED",
            "Booking #" + bookingId + " moved to " + newPickup + " - " + newReturn + ".");
    }

    // ============================================================
    // No-shows (B2)
    // ============================================================
    @Override
    @Transactional
    public BookingResponse markNoShow(int bookingId, Integer staffId, String reason) {
        Booking b = loadOrThrow(bookingId);
        requireStatus(b, BookingStatus.APPROVED, "record a no-show for");
        if (staffId != null) {
            branchGuard.requireStaffAtBranch(staffId, b.getPickupBranchId(), "close it as a no-show");
        }
        if (!b.getPickupDate().isBefore(AppClock.today())) {
            throw new InvalidStatusTransitionException(
                "A booking only becomes a no-show once its pickup day (" + b.getPickupDate() + ") has passed");
        }
        String why = safeReason(reason);
        bookingDao.updateStatus(bookingId, BookingStatus.NO_SHOW.name());
        paymentService.closeWithCharge(bookingId, lateCharge(b), staffId, "No-show: " + why, "No-show charge");
        availabilityService.refreshReservationStatus(b.getVehicleId());
        auditService.recordStatusChange(ENTITY, bookingId, BookingStatus.APPROVED.name(),
                                        BookingStatus.NO_SHOW.name(), staffId, why);
        notificationService.safeSend(b.getCustomerId(), "BOOKING_NO_SHOW",
            "Booking #" + bookingId + " was closed as a no-show: the vehicle booked for "
            + b.getPickupDate() + " was not collected.");
        notificationService.safeSendToBranchStaff(b.getPickupBranchId(), "BOOKING_NO_SHOW",
            "Booking #" + bookingId + " was closed as a no-show; the vehicle is free again.");
        return toResponse(loadOrThrow(bookingId));
    }

    @Override
    @Transactional
    public int closeNoShows(LocalDate asOf) {
        LocalDate cutoff = asOf.minusDays(RentalPolicy.NO_SHOW_AFTER_DAYS);
        int closed = 0;
        for (Booking b : bookingDao.findMissedPickups(cutoff)) {
            markNoShow(b.getBookingId(), null,
                "Not collected within " + RentalPolicy.NO_SHOW_AFTER_DAYS + " days of the pickup date");
            closed++;
        }
        return closed;
    }

    // ============================================================
    // Extending a rental (B1)
    // ============================================================
    /**
     * A later return date for a booking that is approved or already on the
     * road. The extra nights must be free and insured, the licence must still
     * cover them, and the new total is re-quoted at the car's daily rate.
     */
    @Override
    @Transactional
    public BookingResponse extend(int bookingId, int customerId, LocalDate newReturn) {
        Booking b = loadOrThrow(bookingId);
        if (b.getCustomerId() != customerId) {
            throw new UnauthorizedActionException("Only the booking's customer can extend it");
        }
        if (b.getStatus() == BookingStatus.PENDING_APPROVAL) {
            throw new InvalidStatusTransitionException("This booking is still awaiting approval - change its dates instead");
        }
        if (b.getStatus() != BookingStatus.APPROVED && b.getStatus() != BookingStatus.ACTIVE_RENTAL) {
            throw new InvalidStatusTransitionException("A booking that is " + b.getStatus() + " cannot be extended");
        }
        if (newReturn == null || !newReturn.isAfter(b.getReturnDate())) {
            throw new IllegalArgumentException("The new return date must be after " + b.getReturnDate());
        }
        if (newReturn.isBefore(AppClock.today())) {
            throw new IllegalArgumentException("The new return date cannot be in the past");
        }
        if (CostCalculator.rentalDays(b.getPickupDate(), newReturn) > RentalPolicy.MAX_RENTAL_DAYS) {
            throw new IllegalArgumentException(
                "A single booking cannot be longer than " + RentalPolicy.MAX_RENTAL_DAYS + " days");
        }
        userService.requireEligibleToBook(customerId, b.getPickupDate(), newReturn);
        userDao.lockForUpdate(customerId);
        vehicleDao.lockForUpdate(b.getVehicleId());

        LocalDate from = b.getReturnDate();
        if (!bookingDao.findOverlappingForCustomer(customerId, from, newReturn, bookingId).isEmpty()) {
            throw new DoubleBookingException("You have another booking during those extra days");
        }
        Vehicle v = vehicleDao.findById(b.getVehicleId())
            .orElseThrow(() -> new ResourceNotFoundException("Vehicle not found: " + b.getVehicleId()));
        // The extra nights, checked with every availability rule (ignoring this booking).
        availabilityService.requireBookable(v, from, newReturn, bookingId);

        BigDecimal newCost = CostCalculator.totalCost(v.getRentalPricePerDay(), b.getPickupDate(), newReturn);
        bookingDao.updateDates(bookingId, b.getPickupDate(), newReturn, newCost);
        paymentService.adjustTotal(bookingId, newCost, customerId, "Rental extended to " + newReturn);
        auditService.record(ENTITY, bookingId, "UPDATE", customerId,
            "Extended from " + b.getReturnDate() + " to " + newReturn + "; total " + b.getEstimatedCost() + " -> " + newCost);
        notificationService.safeSendToBranchStaff(b.getPickupBranchId(), "BOOKING_EXTENDED",
            "Booking #" + bookingId + " (" + v.getModel() + ") now returns on " + newReturn + ".");
        notificationService.safeSend(customerId, "BOOKING_EXTENDED",
            "Booking #" + bookingId + " now runs until " + newReturn + ". New total: " + newCost + ".");
        return toResponse(loadOrThrow(bookingId));
    }

    // ============================================================
    // What a trip cost, line by line (B6)
    // ============================================================
    @Override
    public BookingChargesResponse charges(int bookingId) {
        Booking b = loadOrThrow(bookingId);
        Vehicle v = vehicleDao.findById(b.getVehicleId()).orElse(null);
        BookingChargesResponse r = new BookingChargesResponse();
        r.setBookingId(bookingId);
        r.setStatus(b.getStatus().name());
        r.setNights(CostCalculator.rentalDays(b.getPickupDate(), b.getReturnDate()));
        BigDecimal rate = v != null ? v.getRentalPricePerDay()
            : b.getEstimatedCost().divide(BigDecimal.valueOf(r.getNights()), 2, java.math.RoundingMode.HALF_UP);
        r.setDailyRate(rate);
        r.setBaseCost(b.getEstimatedCost());

        long lateDays = CostCalculator.lateDays(b.getReturnDate(), b.getActualReturnDate());
        r.setLateDays(lateDays);
        r.setLateFee(CostCalculator.lateFee(rate, lateDays));

        BigDecimal damageTotal = BigDecimal.ZERO;
        BigDecimal credit = BigDecimal.ZERO;
        for (Handover h : handoverDao.findByBooking(bookingId)) {
            for (DamageReport d : damageReportDao.findByHandover(h.getHandoverId())) {
                BookingChargesResponse.DamageLine line = new BookingChargesResponse.DamageLine();
                line.setDescription(d.getDescription());
                line.setSeverity(d.getDamageSeverity());
                line.setCost(d.getEstimatedRepairCost());
                line.setStatus(d.getStatus());
                line.setReportedOn(d.getEventDate());
                if (d.getEstimatedRepairCost() != null) damageTotal = damageTotal.add(d.getEstimatedRepairCost());
                for (InsuranceClaim c : claimDao.findByDamageReport(d.getEventId())) {
                    line.setClaimStatus(c.getStatus().name());
                    line.setClaimAmount(c.getClaimAmount());
                    if ("APPROVED".equals(c.getStatus().name()) && c.getClaimAmount() != null) {
                        credit = credit.add(c.getClaimAmount());
                    }
                }
                r.getDamage().add(line);
            }
        }
        r.setDamageTotal(damageTotal);
        r.setInsuranceCredit(credit);

        var payment = paymentService.findByBooking(bookingId);
        r.setPayment(payment);
        boolean closedEarly = b.getStatus() == BookingStatus.CANCELLED || b.getStatus() == BookingStatus.NO_SHOW
                           || b.getStatus() == BookingStatus.REJECTED;
        if (closedEarly) {
            BigDecimal kept = payment == null || payment.getNote() == null ? BigDecimal.ZERO
                : payment.getRefundAmount() != null ? payment.getAmount().subtract(payment.getRefundAmount())
                : payment.getAmount();
            r.setCancellationCharge(kept);
            r.setTotal(kept);
            r.setFinalTotal(true);
        } else {
            r.setTotal(b.getFinalCost() != null ? b.getFinalCost() : b.getEstimatedCost());
            r.setFinalTotal(b.getFinalCost() != null);
        }
        return r;
    }

    // ============================================================
    // Paging for the console (C1)
    // ============================================================
    @Override
    public PageResponse<BookingResponse> listPage(String status, Integer branchId, String text, Integer page, Integer size) {
        int s = PageResponse.size(size);
        int pg = PageResponse.page(page);
        java.util.Map<String, Long> counts = new java.util.LinkedHashMap<>(bookingDao.countByStatus(branchId, text));
        long all = counts.values().stream().mapToLong(Long::longValue).sum();
        counts.put("all", all);
        String st = status == null || status.isBlank() ? null : status.toUpperCase();
        long total = st == null || "ALL".equals(st) ? all : counts.getOrDefault(st, 0L);
        List<BookingResponse> items = bookingDao.findPage(st, branchId, text, (pg - 1) * s, s).stream()
            .map(this::toResponse).collect(Collectors.toList());
        return new PageResponse<>(items, pg, s, total, counts);
    }

    /** One night at the daily rate, never more than the booking itself. */
    private BigDecimal lateCharge(Booking b) {
        Vehicle v = vehicleDao.findById(b.getVehicleId()).orElse(null);
        if (v == null) {
            return BigDecimal.ZERO;
        }
        BigDecimal fee = v.getRentalPricePerDay().multiply(BigDecimal.valueOf(RentalPolicy.LATE_CANCELLATION_FEE_NIGHTS));
        return fee.min(b.getEstimatedCost()).setScale(2, java.math.RoundingMode.HALF_UP);
    }

    // ============================================================
    // Helpers
    // ============================================================
    private void doCancel(Booking b, int actorUserId, String reason, String auditNote, BigDecimal charge) {
        bookingDao.updateStatus(b.getBookingId(), BookingStatus.CANCELLED.name());
        // A cancelled booking owes nothing but any late-cancellation charge.
        // Anything already paid beyond that is marked for refund.
        paymentService.closeWithCharge(b.getBookingId(), charge, actorUserId, reason, "Late cancellation charge");

        // Free the vehicle again if it was being held for this pickup.
        availabilityService.refreshReservationStatus(b.getVehicleId());

        auditService.recordStatusChange(ENTITY, b.getBookingId(), b.getStatus().name(),
                                        BookingStatus.CANCELLED.name(), actorUserId,
                                        auditNote + ": " + reason);
        notificationService.safeSend(b.getCustomerId(), "BOOKING_CANCELLED",
            "Booking #" + b.getBookingId() + " has been cancelled. " + reason);
        notificationService.safeSendToBranchStaff(b.getPickupBranchId(), "BOOKING_CANCELLED",
            "Booking #" + b.getBookingId() + " was cancelled (" + reason + ").");
    }

    private String safeReason(String reason) {
        return reason == null || reason.isBlank() ? "No reason given." : reason.trim();
    }

    private Booking loadOrThrow(int bookingId) {
        return bookingDao.findById(bookingId)
            .orElseThrow(() -> new ResourceNotFoundException("Booking not found: " + bookingId));
    }

    private void requireStatus(Booking b, BookingStatus expected, String action) {
        if (b.getStatus() != expected) {
            throw new InvalidStatusTransitionException(
                "Cannot " + action + " a booking in status " + b.getStatus());
        }
    }

    // Manual entity -> DTO conversion
    private BookingResponse toResponse(Booking b) {
        BookingResponse r = new BookingResponse();
        r.setBookingId(b.getBookingId());
        r.setCustomerId(b.getCustomerId());
        r.setVehicleId(b.getVehicleId());
        r.setPickupBranchId(b.getPickupBranchId());
        r.setApprovedBy(b.getApprovedBy());
        r.setPickupDate(b.getPickupDate());
        r.setReturnDate(b.getReturnDate());
        r.setActualReturnDate(b.getActualReturnDate());
        r.setSpecialRequests(b.getSpecialRequests());
        r.setEstimatedCost(b.getEstimatedCost());
        r.setFinalCost(b.getFinalCost());
        r.setStatus(b.getStatus().name());
        r.setSubmittedDate(b.getSubmittedDate());
        return r;
    }
}
