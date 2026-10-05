package com.vehiclerental.service.impl;

import com.vehiclerental.dao.BookingDao;
import com.vehiclerental.dao.DamageReportDao;
import com.vehiclerental.dao.HandoverDao;
import com.vehiclerental.dao.InsuranceClaimDao;
import com.vehiclerental.dao.MaintenanceDao;
import com.vehiclerental.dao.VehicleDao;
import com.vehiclerental.dto.request.CreateClaimRequest;
import com.vehiclerental.dto.request.CreateDamageReportRequest;
import com.vehiclerental.dto.response.ClaimResponse;
import com.vehiclerental.dto.response.DamageReportResponse;
import com.vehiclerental.enums.ClaimStatus;
import com.vehiclerental.enums.DamageStatus;
import com.vehiclerental.enums.VehicleStatus;
import com.vehiclerental.exception.InvalidStatusTransitionException;
import com.vehiclerental.exception.ResourceNotFoundException;
import com.vehiclerental.model.Booking;
import com.vehiclerental.model.DamageReport;
import com.vehiclerental.model.Handover;
import com.vehiclerental.model.InsuranceClaim;
import com.vehiclerental.model.Vehicle;
import com.vehiclerental.service.AuditService;
import com.vehiclerental.service.AvailabilityService;
import com.vehiclerental.service.DamageClaimService;
import com.vehiclerental.service.NotificationService;
import com.vehiclerental.service.PaymentService;
import com.vehiclerental.util.AppClock;
import com.vehiclerental.util.CostCalculator;
import com.vehiclerental.util.RentalPolicy;
import com.vehiclerental.util.SeverityValidator;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
public class DamageClaimServiceImpl implements DamageClaimService {

    private static final String DAMAGE = "DAMAGE_REPORT";
    private static final String CLAIM = "INSURANCE_CLAIM";

    private final DamageReportDao damageReportDao;
    private final InsuranceClaimDao insuranceClaimDao;
    private final VehicleDao vehicleDao;
    private final HandoverDao handoverDao;
    private final BookingDao bookingDao;
    private final MaintenanceDao maintenanceDao;
    private final PaymentService paymentService;
    private final NotificationService notificationService;
    private final AvailabilityService availabilityService;
    private final AuditService auditService;

    public DamageClaimServiceImpl(DamageReportDao damageReportDao,
                                  InsuranceClaimDao insuranceClaimDao,
                                  VehicleDao vehicleDao,
                                  HandoverDao handoverDao,
                                  BookingDao bookingDao,
                                  MaintenanceDao maintenanceDao,
                                  PaymentService paymentService,
                                  NotificationService notificationService,
                                  AvailabilityService availabilityService,
                                  AuditService auditService) {
        this.damageReportDao = damageReportDao;
        this.insuranceClaimDao = insuranceClaimDao;
        this.vehicleDao = vehicleDao;
        this.handoverDao = handoverDao;
        this.bookingDao = bookingDao;
        this.maintenanceDao = maintenanceDao;
        this.paymentService = paymentService;
        this.notificationService = notificationService;
        this.availabilityService = availabilityService;
        this.auditService = auditService;
    }

    // ==================== Damage Reports ====================

    @Override
    @Transactional
    public DamageReportResponse createDamageReport(int staffId, CreateDamageReportRequest request) {

        Vehicle vehicle = vehicleDao.findById(request.getVehicleId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Vehicle not found: " + request.getVehicleId()));

        if (request.getHandoverId() != null) {
            handoverDao.findById(request.getHandoverId())
                    .orElseThrow(() -> new ResourceNotFoundException(
                            "Handover not found: " + request.getHandoverId()));
        }

        LocalDate today = AppClock.today();
        LocalDate damageDate = request.getDamageDate() == null ? today : request.getDamageDate();
        if (damageDate.isAfter(today)) {
            throw new IllegalArgumentException("Damage cannot be dated in the future");
        }

        String severity = SeverityValidator.normalise(request.getDamageSeverity());

        DamageReport d = new DamageReport();
        d.setVehicleId(request.getVehicleId());
        d.setEventDate(today);                 // reported_date
        d.setDamageDate(damageDate);
        d.setStatus(DamageStatus.UNDER_REVIEW.name());
        d.setDescription(request.getDescription());
        d.setHandoverId(request.getHandoverId());
        d.setReportedBy(staffId);
        d.setDamageSeverity(severity);
        d.setEstimatedRepairCost(request.getEstimatedRepairCost());
        damageReportDao.save(d);

        // Serious damage takes the vehicle off the road until the report is closed.
        if (RentalPolicy.blocksVehicle(severity)
                && vehicle.getStatus() != VehicleStatus.RENTED
                && vehicle.getStatus() != VehicleStatus.UNDER_MAINTENANCE) {
            vehicleDao.updateStatus(vehicle.getVehicleId(), VehicleStatus.UNDER_MAINTENANCE.name());
            auditService.recordStatusChange("VEHICLE", vehicle.getVehicleId(), vehicle.getStatus().name(),
                                            VehicleStatus.UNDER_MAINTENANCE.name(), staffId,
                                            "Damage report #" + d.getEventId());
        }

        auditService.record(DAMAGE, d.getEventId(), "CREATE", staffId,
                            severity + " damage on " + vehicle.getPlateNumber());

        // If the damage is tied to a rental, the customer has a right to know.
        findBookingForReport(d).ifPresent(b ->
            notificationService.safeSend(b.getCustomerId(), "DAMAGE_REPORTED",
                "A " + severity.toLowerCase() + " damage report has been filed against booking #"
                + b.getBookingId() + ". The branch will contact you about it."));

        return toDamageResponse(d);
    }

    @Override
    public DamageReportResponse findDamageReportById(int eventId) {
        return toDamageResponse(loadReport(eventId));
    }

    @Override
    public List<DamageReportResponse> listAllDamageReports() {
        return damageReportDao.findAll().stream()
                .map(this::toDamageResponse)
                .collect(Collectors.toList());
    }

    @Override
    public List<DamageReportResponse> listDamageReportsByVehicle(int vehicleId) {
        return damageReportDao.findByVehicle(vehicleId).stream()
                .map(this::toDamageResponse)
                .collect(Collectors.toList());
    }

    @Override
    public List<DamageReportResponse> listDamageReportsByStatus(String status) {
        DamageStatus s = DamageStatus.valueOf(status.toUpperCase());  // validates the string
        return damageReportDao.findByStatus(s.name()).stream()
                .map(this::toDamageResponse)
                .collect(Collectors.toList());
    }

    /**
     * Closing a damage report is the step that used to be missing: it is what
     * puts the vehicle back into service. A report also cannot be re-opened once
     * closed, and cannot be closed while an insurance claim is still undecided.
     */
    @Override
    @Transactional
    public void updateDamageReportStatus(int eventId, String newStatus, int actorUserId, String reason) {
        DamageStatus target = DamageStatus.valueOf(newStatus.toUpperCase());
        DamageReport d = loadReport(eventId);
        DamageStatus current = DamageStatus.valueOf(d.getStatus());

        if (target == current) {
            return;
        }
        if (current == DamageStatus.RESOLVED) {
            throw new InvalidStatusTransitionException(
                "This damage report is already resolved and cannot be re-opened. "
                + "File a new report if more damage is found.");
        }

        boolean claimPending = insuranceClaimDao.findByDamageReport(eventId).stream()
                .anyMatch(c -> c.getStatus() == ClaimStatus.SUBMITTED);
        if (target == DamageStatus.RESOLVED && claimPending) {
            throw new InvalidStatusTransitionException(
                "An insurance claim for this damage is still undecided. Settle the claim first.");
        }

        damageReportDao.updateStatus(eventId, target.name());
        auditService.recordStatusChange(DAMAGE, eventId, current.name(), target.name(),
                                        actorUserId, reason);

        if (target == DamageStatus.RESOLVED) {
            releaseVehicleIfFree(d.getVehicleId(), eventId, actorUserId);
        }
    }

    @Override
    @Transactional
    public void deleteClosedReport(int eventId, int actorUserId) {
        DamageReport d = loadReport(eventId);

        if (!DamageStatus.RESOLVED.name().equals(d.getStatus())) {
            throw new InvalidStatusTransitionException(
                    "Only RESOLVED damage reports can be deleted. Current status: " + d.getStatus());
        }
        if (!insuranceClaimDao.findByDamageReport(eventId).isEmpty()) {
            throw new InvalidStatusTransitionException(
                    "Damage report has insurance claims attached; delete those first");
        }

        // rental. It stays with the booking.
        if (d.getHandoverId() != null) {
            throw new InvalidStatusTransitionException(
                    "This report is attached to a rental handover and is part of that booking's record");
        }

        damageReportDao.delete(eventId);
        auditService.record(DAMAGE, eventId, "DELETE", actorUserId, "Resolved report removed");
    }

    // ==================== Insurance Claims ====================

    /**
     * A claim is made against a specific repair bill, so it cannot exceed what
     * that repair is estimated to cost, and the same damage cannot be claimed
     * for twice.
     */
    @Override
    @Transactional
    public ClaimResponse createClaim(CreateClaimRequest request, int actorUserId) {

        DamageReport report = loadReport(request.getDamageReportId());

        List<InsuranceClaim> existing = insuranceClaimDao.findByDamageReport(report.getEventId());

        boolean openClaim = existing.stream().anyMatch(c -> c.getStatus() == ClaimStatus.SUBMITTED);
        if (openClaim) {
            throw new InvalidStatusTransitionException(
                "There is already an open claim for this damage report");
        }

        BigDecimal alreadyApproved = existing.stream()
                .filter(c -> c.getStatus() == ClaimStatus.APPROVED)
                .map(c -> c.getClaimAmount() == null ? BigDecimal.ZERO : c.getClaimAmount())
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal repairCost = report.getEstimatedRepairCost();
        if (repairCost == null || repairCost.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException(
                "This damage report has no repair estimate, so there is nothing to claim for");
        }
        BigDecimal claimable = repairCost.subtract(alreadyApproved);
        if (claimable.compareTo(BigDecimal.ZERO) <= 0) {
            throw new InvalidStatusTransitionException(
                "The full repair cost of " + repairCost + " has already been claimed");
        }
        if (request.getClaimAmount().compareTo(claimable) > 0) {
            throw new IllegalArgumentException(
                "A claim cannot exceed the outstanding repair cost of " + claimable);
        }

        InsuranceClaim c = new InsuranceClaim();
        c.setDamageReportId(request.getDamageReportId());
        c.setInsuranceProvider(request.getInsuranceProvider());
        c.setClaimAmount(request.getClaimAmount());
        c.setSubmittedDate(AppClock.today());
        c.setStatus(ClaimStatus.SUBMITTED);
        insuranceClaimDao.save(c);

        auditService.record(CLAIM, c.getClaimId(), "CREATE", actorUserId,
            "Claim of " + request.getClaimAmount() + " against damage report #" + report.getEventId());
        return toClaimResponse(c);
    }

    @Override
    public ClaimResponse findClaimById(int claimId) {
        return toClaimResponse(loadClaim(claimId));
    }

    @Override
    public List<ClaimResponse> listAllClaims() {
        return insuranceClaimDao.findAll().stream()
                .map(this::toClaimResponse)
                .collect(Collectors.toList());
    }

    @Override
    public List<ClaimResponse> listClaimsByStatus(String status) {
        ClaimStatus s = ClaimStatus.valueOf(status.toUpperCase());  // validates
        return insuranceClaimDao.findByStatus(s.name()).stream()
                .map(this::toClaimResponse)
                .collect(Collectors.toList());
    }

    @Override
    public List<ClaimResponse> listClaimsForDamageReport(int damageReportId) {
        return insuranceClaimDao.findByDamageReport(damageReportId).stream()
                .map(this::toClaimResponse)
                .collect(Collectors.toList());
    }

    /**
     * Only a submitted claim can be decided, and the decision is final.
     * When insurance pays, the customer stops owing that part of the repair —
     * which is the whole reason a claim is worth making.
     */
    @Override
    @Transactional
    public void updateClaimStatus(int claimId, String newStatus, int actorUserId, String reason) {
        ClaimStatus target = ClaimStatus.valueOf(newStatus.toUpperCase());
        InsuranceClaim c = loadClaim(claimId);

        if (target == c.getStatus()) {
            return;
        }
        if (c.getStatus() != ClaimStatus.SUBMITTED) {
            throw new InvalidStatusTransitionException(
                "Claim is already " + c.getStatus() + " and cannot change to " + target);
        }
        if (target == ClaimStatus.SUBMITTED) {
            throw new InvalidStatusTransitionException("A claim cannot be moved back to SUBMITTED");
        }

        insuranceClaimDao.updateStatus(claimId, target.name());
        auditService.recordStatusChange(CLAIM, claimId, c.getStatus().name(), target.name(),
                                        actorUserId, reason);

        if (target == ClaimStatus.APPROVED) {
            creditCustomerForApprovedClaim(c, actorUserId);
        }
    }

    @Override
    @Transactional
    public void deleteCancelledClaim(int claimId, int actorUserId) {
        InsuranceClaim c = loadClaim(claimId);

        if (c.getStatus() != ClaimStatus.REJECTED && c.getStatus() != ClaimStatus.CANCELLED) {
            throw new InvalidStatusTransitionException(
                    "Only REJECTED or CANCELLED claims can be deleted. Current status: " + c.getStatus());
        }
        insuranceClaimDao.delete(claimId);
        auditService.record(CLAIM, claimId, "DELETE", actorUserId, "Closed claim removed");
    }

    // ---------- Helpers ----------

    /**
     * The customer was charged the repair estimate when the vehicle came back.
     * Whatever the insurer pays comes straight off that bill.
     */
    private void creditCustomerForApprovedClaim(InsuranceClaim c, int actorUserId) {
        DamageReport report = damageReportDao.findById(c.getDamageReportId()).orElse(null);
        if (report == null) {
            return;
        }
        Optional<Booking> maybeBooking = findBookingForReport(report);
        if (maybeBooking.isEmpty()) {
            return;     // damage not tied to a rental — nobody to credit
        }
        Booking b = maybeBooking.get();
        if (b.getFinalCost() == null) {
            return;
        }

        BigDecimal credited = CostCalculator.applyCredit(b.getFinalCost(), c.getClaimAmount());
        if (credited.compareTo(b.getFinalCost()) == 0) {
            return;
        }

        bookingDao.updateFinalCost(b.getBookingId(), credited);
        paymentService.settleFinalAmount(b.getBookingId(), credited, actorUserId,
            "Insurance claim #" + c.getClaimId() + " approved for " + c.getClaimAmount());

        auditService.record("BOOKING", b.getBookingId(), "UPDATE", actorUserId,
            "Bill reduced from " + b.getFinalCost() + " to " + credited
            + " after insurance claim #" + c.getClaimId());
        notificationService.safeSend(b.getCustomerId(), "DAMAGE_CHARGE_CREDITED",
            "Insurance has covered " + c.getClaimAmount() + " of the repair for booking #"
            + b.getBookingId() + ". Your total is now " + credited + ".");
    }

    /** Walk damage report -> handover -> booking, when the damage came from a rental. */
    private Optional<Booking> findBookingForReport(DamageReport report) {
        if (report.getHandoverId() == null) {
            return Optional.empty();
        }
        Optional<Handover> handover = handoverDao.findById(report.getHandoverId());
        if (handover.isEmpty()) {
            return Optional.empty();
        }
        return bookingDao.findById(handover.get().getBookingId());
    }

    /** A resolved report only frees the vehicle if nothing else is holding it. */
    private void releaseVehicleIfFree(int vehicleId, int excludeReportId, int actorUserId) {
        Vehicle v = vehicleDao.findById(vehicleId).orElse(null);
        if (v == null || v.getStatus() != VehicleStatus.UNDER_MAINTENANCE) {
            return;
        }

        boolean otherSeriousDamage = damageReportDao.findOpenByVehicle(vehicleId).stream()
                .filter(d -> d.getEventId() != excludeReportId)
                .anyMatch(d -> RentalPolicy.blocksVehicle(d.getDamageSeverity()));
        if (otherSeriousDamage) {
            return;
        }
        boolean inWorkshop = !maintenanceDao
                .findBlockingToday(vehicleId, AppClock.today(), null).isEmpty();
        if (inWorkshop) {
            return;
        }

        vehicleDao.updateStatus(vehicleId, VehicleStatus.AVAILABLE.name());
        auditService.recordStatusChange("VEHICLE", vehicleId, VehicleStatus.UNDER_MAINTENANCE.name(),
                                        VehicleStatus.AVAILABLE.name(), actorUserId,
                                        "Damage report #" + excludeReportId + " resolved");
        availabilityService.refreshReservationStatus(vehicleId);
        notificationService.safeSendToBranchStaff(v.getBranchId(), "VEHICLE_BACK_ON_ROAD",
            v.getModel() + " (" + v.getPlateNumber() + ") is back in service.");
    }

    private DamageReport loadReport(int eventId) {
        return damageReportDao.findById(eventId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Damage report not found: " + eventId));
    }

    private InsuranceClaim loadClaim(int claimId) {
        return insuranceClaimDao.findById(claimId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Insurance claim not found: " + claimId));
    }

    private DamageReportResponse toDamageResponse(DamageReport d) {
        DamageReportResponse r = new DamageReportResponse();
        r.setEventId(d.getEventId());
        r.setVehicleId(d.getVehicleId());
        r.setHandoverId(d.getHandoverId());
        r.setReportedBy(d.getReportedBy());
        r.setEventDate(d.getEventDate());
        r.setDamageDate(d.getDamageDate());
        r.setDescription(d.getDescription());
        r.setDamageSeverity(d.getDamageSeverity());
        r.setEstimatedRepairCost(d.getEstimatedRepairCost());
        r.setStatus(d.getStatus());
        return r;
    }

    private ClaimResponse toClaimResponse(InsuranceClaim c) {
        ClaimResponse r = new ClaimResponse();
        r.setClaimId(c.getClaimId());
        r.setDamageReportId(c.getDamageReportId());
        r.setInsuranceProvider(c.getInsuranceProvider());
        r.setClaimAmount(c.getClaimAmount());
        r.setSubmittedDate(c.getSubmittedDate());
        r.setStatus(c.getStatus().name());
        return r;
    }
}
