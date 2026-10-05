package com.vehiclerental.controller;

import com.vehiclerental.dao.MaintenanceDao;
import com.vehiclerental.dao.VehicleDao;
import com.vehiclerental.dao.VehicleTransferDao;
import com.vehiclerental.dto.request.CreateBookingRequest;
import com.vehiclerental.dto.request.ReasonRequest;
import com.vehiclerental.dto.response.BookingChargesResponse;
import com.vehiclerental.dto.response.BookingResponse;
import com.vehiclerental.dto.response.PageResponse;
import com.vehiclerental.dto.response.PaymentResponse;
import com.vehiclerental.exception.UnauthorizedActionException;
import com.vehiclerental.model.MaintenanceRecord;
import com.vehiclerental.model.Vehicle;
import com.vehiclerental.model.VehicleTransfer;
import com.vehiclerental.security.AppUserPrincipal;
import com.vehiclerental.security.BranchGuard;
import com.vehiclerental.service.BookingService;
import com.vehiclerental.service.PaymentService;
import com.vehiclerental.util.AppClock;
import com.vehiclerental.validation.Rules;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/bookings")
public class BookingController {

    private static final int MAX_BULK = 50;
    private static final int MAX_TIMELINE_DAYS = 92;

    private final BookingService bookingService;
    private final PaymentService paymentService;
    private final BranchGuard branchGuard;
    private final VehicleDao vehicleDao;
    private final MaintenanceDao maintenanceDao;
    private final VehicleTransferDao transferDao;

    public BookingController(BookingService bookingService, PaymentService paymentService, BranchGuard branchGuard,
                             VehicleDao vehicleDao, MaintenanceDao maintenanceDao, VehicleTransferDao transferDao) {
        this.bookingService = bookingService;
        this.paymentService = paymentService;
        this.branchGuard = branchGuard;
        this.vehicleDao = vehicleDao;
        this.maintenanceDao = maintenanceDao;
        this.transferDao = transferDao;
    }

    // Customer creates a booking, Then customer id comes from the login session not from the request body. So a customer can never book on someone else's behalf
    @PostMapping
    @PreAuthorize("hasRole('CUSTOMER')")
    public ResponseEntity<BookingResponse> create(
            @Valid @RequestBody CreateBookingRequest request,
            @AuthenticationPrincipal AppUserPrincipal me) {
        BookingResponse r = bookingService.create(me.getUserId(), request);
        return ResponseEntity.ok(r);
    }

    // Customers can only see their own bookings while admins can see any
    @GetMapping("/{id}")
    public BookingResponse findById(@PathVariable int id,
                                    @AuthenticationPrincipal AppUserPrincipal me) {
        BookingResponse r = bookingService.findById(id);
        requireOwnerOrStaff(r, me);
        return r;
    }

    // Payment status of a booking (customer sees own wile admin can any)
    @GetMapping("/{id}/payment")
    public PaymentResponse payment(@PathVariable int id,
                                   @AuthenticationPrincipal AppUserPrincipal me) {
        requireOwnerOrStaff(bookingService.findById(id), me);
        return paymentService.findByBooking(id);
    }

    /** Every line of what the trip cost: rental, late fee, damage and claims, charges (B6). */
    @GetMapping("/{id}/charges")
    public BookingChargesResponse charges(@PathVariable int id,
                                          @AuthenticationPrincipal AppUserPrincipal me) {
        requireOwnerOrStaff(bookingService.findById(id), me);
        return bookingService.charges(id);
    }

    @GetMapping("/mine")
    @PreAuthorize("hasRole('CUSTOMER')")
    public List<BookingResponse> myBookings(@AuthenticationPrincipal AppUserPrincipal me) {
        return bookingService.listByCustomer(me.getUserId());
    }

    // staff lists: a staff member sees their own branch while admin can all
    @GetMapping("/pending")
    @PreAuthorize("hasAnyRole('STAFF', 'ADMINISTRATOR')")
    public List<BookingResponse> pending(@AuthenticationPrincipal AppUserPrincipal me) {
        return scoped(bookingService.listPending(), me);
    }

    /** Vehicles that should have come back already. */
    @GetMapping("/overdue")
    @PreAuthorize("hasAnyRole('STAFF', 'ADMINISTRATOR')")
    public List<BookingResponse> overdue(@AuthenticationPrincipal AppUserPrincipal me) {
        return scoped(bookingService.listOverdueReturns(), me);
    }

    /** Approved bookings nobody turned up for. */
    @GetMapping("/missed-pickups")
    @PreAuthorize("hasAnyRole('STAFF', 'ADMINISTRATOR')")
    public List<BookingResponse> missedPickups(@AuthenticationPrincipal AppUserPrincipal me) {
        return scoped(bookingService.listMissedPickups(), me);
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('STAFF', 'ADMINISTRATOR')")
    public List<BookingResponse> all(@AuthenticationPrincipal AppUserPrincipal me) {
        return scoped(bookingService.listAll(), me);
    }

    /**
     * One page for the console, with per-status counts for the tabs (C1).
     * Staff see their own branch; an administrator can pass ?branchId= or see all.
     */
    @GetMapping("/page")
    @PreAuthorize("hasAnyRole('STAFF', 'ADMINISTRATOR')")
    public PageResponse<BookingResponse> page(@RequestParam(required = false) String status,
                                              @RequestParam(required = false) @Size(max = Rules.SEARCH_TEXT, message = "Search text is too long") String q,
                                              @RequestParam(required = false) Integer branchId,
                                              @RequestParam(required = false) Integer page,
                                              @RequestParam(required = false) Integer size,
                                              @AuthenticationPrincipal AppUserPrincipal me) {
        Integer scope = branchGuard.scopeFor(me.getUserId());
        return bookingService.listPage(status, scope != null ? scope : branchId, q, page, size);
    }

    /**
     * Everything that occupies a car between two dates, for the fleet
     * timeline (C2): live bookings, workshop windows and pending transfers.
     */
    @GetMapping("/timeline")
    @PreAuthorize("hasAnyRole('STAFF', 'ADMINISTRATOR')")
    public Map<String, Object> timeline(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) Integer branchId,
            @AuthenticationPrincipal AppUserPrincipal me) {
        LocalDate start = from == null ? AppClock.today() : from;
        LocalDate end = to == null ? start.plusDays(29) : to;
        if (end.isBefore(start)) {
            throw new IllegalArgumentException("The end of the timeline must be after its start");
        }
        if (end.isAfter(start.plusDays(MAX_TIMELINE_DAYS))) {
            end = start.plusDays(MAX_TIMELINE_DAYS);
        }
        Integer scope = branchGuard.scopeFor(me.getUserId());
        Integer branch = scope != null ? scope : branchId;
        LocalDate s = start;
        LocalDate e = end;

        List<Vehicle> vehicles = branch == null ? vehicleDao.findAll() : vehicleDao.findByBranch(branch);
        java.util.Set<Integer> ids = vehicles.stream().map(Vehicle::getVehicleId).collect(Collectors.toSet());

        List<Map<String, Object>> works = new ArrayList<>();
        for (MaintenanceRecord m : maintenanceDao.findAll()) {
            if (!ids.contains(m.getVehicleId())) continue;
            if (!"SCHEDULED".equals(m.getStatus()) && !"IN_PROGRESS".equals(m.getStatus())) continue;
            if (m.getEventDate().isAfter(e) || m.blockedUntil().isBefore(s)) continue;
            Map<String, Object> w = new LinkedHashMap<>();
            w.put("vehicleId", m.getVehicleId());
            w.put("from", m.getEventDate());
            w.put("to", m.blockedUntil());
            w.put("status", m.getStatus());
            w.put("label", m.getRepairType());
            works.add(w);
        }
        List<Map<String, Object>> moves = new ArrayList<>();
        for (VehicleTransfer t : transferDao.findPending()) {
            if (!ids.contains(t.getVehicleId()) || t.getTransferDate().isAfter(e) || t.getTransferDate().isBefore(s)) continue;
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("vehicleId", t.getVehicleId());
            m.put("date", t.getTransferDate());
            m.put("toBranchId", t.getToBranchId());
            moves.add(m);
        }

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("from", s);
        out.put("to", e);
        out.put("branchId", branch);
        out.put("vehicles", vehicles);
        out.put("bookings", bookingService.listAll().stream()
            .filter(b -> ids.contains(b.getVehicleId()))
            .filter(b -> List.of("PENDING_APPROVAL", "APPROVED", "ACTIVE_RENTAL").contains(b.getStatus()))
            .filter(b -> !b.getPickupDate().isAfter(e) && !b.getReturnDate().isBefore(s))
            .collect(Collectors.toList()));
        out.put("maintenance", works);
        out.put("transfers", moves);
        return out;
    }

    // decisions: each returns the booking as it now stands
    @PatchMapping("/{id}/approve")
    @PreAuthorize("hasAnyRole('STAFF', 'ADMINISTRATOR')")
    public BookingResponse approve(@PathVariable int id,
                                   @AuthenticationPrincipal AppUserPrincipal me) {
        bookingService.approve(id, me.getUserId());
        return bookingService.findById(id);
    }

    @PatchMapping("/{id}/reject")
    @PreAuthorize("hasAnyRole('STAFF', 'ADMINISTRATOR')")
    public BookingResponse reject(@PathVariable int id,
                                  @RequestParam(required = false) String reason,
                                  @Valid @RequestBody(required = false) ReasonRequest body,
                                  @AuthenticationPrincipal AppUserPrincipal me) {
        bookingService.reject(id, me.getUserId(), reasonOf(body, reason));
        return bookingService.findById(id);
    }

    /**
     * Approve or reject several requests at once. Each one is decided on
     * its own - one that fails (a clash, another branch's booking) does not
     * stop the rest - and the answer says what happened to every id
     */
    @PostMapping("/bulk")
    @PreAuthorize("hasAnyRole('STAFF', 'ADMINISTRATOR')")
    public List<Map<String, Object>> bulk(@Valid @RequestBody BulkRequest request,
                                          @AuthenticationPrincipal AppUserPrincipal me) {
        if (request == null || request.ids() == null || request.ids().isEmpty()) {
            throw new IllegalArgumentException("Choose at least one booking");
        }
        if (request.ids().size() > MAX_BULK) {
            throw new IllegalArgumentException("At most " + MAX_BULK + " bookings at a time");
        }
        boolean approve = "approve".equalsIgnoreCase(request.action());
        if (!approve && !"reject".equalsIgnoreCase(request.action())) {
            throw new IllegalArgumentException("Action must be approve or reject");
        }
        List<Map<String, Object>> results = new ArrayList<>();
        for (Integer id : request.ids().stream().distinct().toList()) {
            Map<String, Object> r = new LinkedHashMap<>();
            r.put("bookingId", id);
            try {
                if (approve) {
                    bookingService.approve(id, me.getUserId());
                } else {
                    bookingService.reject(id, me.getUserId(), request.reason());
                }
                r.put("ok", true);
                r.put("status", bookingService.findById(id).getStatus());
            } catch (RuntimeException ex) {
                r.put("ok", false);
                r.put("message", ex.getMessage());
            }
            results.add(r);
        }
        return results;
    }

    public record BulkRequest(
        @NotBlank(message = "Choose approve or reject")
        @Pattern(regexp = "(?i)^(approve|reject)$", message = "Action must be approve or reject")
        String action,
        @NotEmpty(message = "Choose at least one booking")
        @Size(max = MAX_BULK, message = "At most " + MAX_BULK + " bookings at a time")
        List<@NotNull @Positive Integer> ids,
        @Size(max = Rules.REASON, message = "Reason can be at most " + Rules.REASON + " characters")
        String reason) { }

    @PatchMapping("/{id}/cancel")
    @PreAuthorize("hasRole('CUSTOMER')")
    public BookingResponse cancel(@PathVariable int id,
                                  @RequestParam(required = false) String reason,
                                  @Valid @RequestBody(required = false) ReasonRequest body,
                                  @AuthenticationPrincipal AppUserPrincipal me) {
        bookingService.cancelByCustomer(id, me.getUserId(), reasonOf(body, reason));
        return bookingService.findById(id);
    }

    @PatchMapping("/{id}/staff-cancel")
    @PreAuthorize("hasAnyRole('STAFF', 'ADMINISTRATOR')")
    public BookingResponse staffCancel(@PathVariable int id,
                                       @RequestParam(required = false) String reason,
                                       @Valid @RequestBody(required = false) ReasonRequest body,
                                       @AuthenticationPrincipal AppUserPrincipal me) {
        bookingService.cancelByStaff(id, me.getUserId(), reasonOf(body, reason));
        return bookingService.findById(id);
    }

    /** The customer never came for the car */
    @PatchMapping("/{id}/no-show")
    @PreAuthorize("hasAnyRole('STAFF', 'ADMINISTRATOR')")
    public BookingResponse noShow(@PathVariable int id,
                                  @Valid @RequestBody(required = false) ReasonRequest body,
                                  @AuthenticationPrincipal AppUserPrincipal me) {
        return bookingService.markNoShow(id, me.getUserId(), reasonOf(body, null));
    }

    @PatchMapping("/{id}/dates")
    @PreAuthorize("hasRole('CUSTOMER')")
    public BookingResponse modifyDates(
            @PathVariable int id,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate pickup,
            @RequestParam("return") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate returnDate,
            @AuthenticationPrincipal AppUserPrincipal me) {
        bookingService.modifyDates(id, me.getUserId(), pickup, returnDate);
        return bookingService.findById(id);
    }

    /** A later return date for an approved or active rental */
    @PatchMapping("/{id}/extend")
    @PreAuthorize("hasRole('CUSTOMER')")
    public BookingResponse extend(
            @PathVariable int id,
            @RequestParam("return") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate returnDate,
            @AuthenticationPrincipal AppUserPrincipal me) {
        return bookingService.extend(id, me.getUserId(), returnDate);
    }

    // ---------- helpers ----------
    private void requireOwnerOrStaff(BookingResponse booking, AppUserPrincipal me) {
        String role = me.getUser().getRole().name();
        if (role.equals("CUSTOMER") && booking.getCustomerId() != me.getUserId()) {
            throw new UnauthorizedActionException("You can only view your own bookings");
        }
    }

    private List<BookingResponse> scoped(List<BookingResponse> list, AppUserPrincipal me) {
        Integer scope = branchGuard.scopeFor(me.getUserId());
        if (scope == null) {
            return list;
        }
        return list.stream().filter(b -> b.getPickupBranchId() == scope).collect(Collectors.toList());
    }


    static String reasonOf(ReasonRequest body, String queryReason) {
        if (body != null && body.getReason() != null && !body.getReason().isBlank()) {
            return body.getReason();
        }
        String q = queryReason == null ? null : queryReason.trim();
        if (q != null && q.length() > Rules.REASON) {
            throw new IllegalArgumentException("Reason can be at most " + Rules.REASON + " characters");
        }
        return q == null || q.isEmpty() ? null : q;
    }
}
