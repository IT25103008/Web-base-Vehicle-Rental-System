package com.vehiclerental.service.impl;

import com.vehiclerental.dao.*;
import com.vehiclerental.dto.request.PickupRequest;
import com.vehiclerental.dto.request.ReturnVehicleRequest;
import com.vehiclerental.enums.*;
import com.vehiclerental.exception.InvalidStatusTransitionException;
import com.vehiclerental.exception.ResourceNotFoundException;
import com.vehiclerental.exception.UnauthorizedActionException;
import com.vehiclerental.exception.VehicleNotAvailableException;
import com.vehiclerental.model.*;
import com.vehiclerental.service.*;
import com.vehiclerental.util.AppClock;
import com.vehiclerental.util.CostCalculator;
import com.vehiclerental.util.RentalPolicy;
import com.vehiclerental.util.SeverityValidator;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;
import java.util.List;

@Service
public class HandoverServiceImpl implements HandoverService {

    private static final String ENTITY = "BOOKING";

    /** A single jump larger than this is almost certainly a typing mistake. */
    private static final int MAX_PLAUSIBLE_MILEAGE_JUMP = 20_000;

    private final HandoverDao handoverDao;
    private final BookingDao bookingDao;
    private final VehicleDao vehicleDao;
    private final DamageReportDao damageReportDao;
    private final MaintenanceDao maintenanceDao;
    private final BranchDao branchDao;
    private final UserDao userDao;
    private final NotificationService notificationService;
    private final PaymentService paymentService;
    private final UserService userService;
    private final AvailabilityService availabilityService;
    private final AuditService auditService;

    public HandoverServiceImpl(HandoverDao handoverDao, BookingDao bookingDao,
                               VehicleDao vehicleDao, DamageReportDao damageReportDao,
                               MaintenanceDao maintenanceDao, BranchDao branchDao, UserDao userDao,
                               NotificationService notificationService, PaymentService paymentService,
                               UserService userService, AvailabilityService availabilityService,
                               AuditService auditService) {
        this.handoverDao = handoverDao;
        this.bookingDao = bookingDao;
        this.vehicleDao = vehicleDao;
        this.damageReportDao = damageReportDao;
        this.maintenanceDao = maintenanceDao;
        this.branchDao = branchDao;
        this.userDao = userDao;
        this.notificationService = notificationService;
        this.paymentService = paymentService;
        this.userService = userService;
        this.availabilityService = availabilityService;
        this.auditService = auditService;
    }

    // ============================================================
    // Pickup
    // ============================================================
    @Override
    @Transactional
    public Handover confirmPickup(int bookingId, int staffId, PickupRequest request) {

        Booking b = loadBooking(bookingId);
        if (b.getStatus() != BookingStatus.APPROVED) {
            throw new InvalidStatusTransitionException(
                "Only APPROVED bookings can go to pickup (current: " + b.getStatus() + ")");
        }
        if (handoverDao.findByBookingAndType(bookingId, HandoverType.PICKUP.name()).isPresent()) {
            throw new InvalidStatusTransitionException("This booking has already been picked up");
        }

        LocalDate today = AppClock.today();

        // 1. A vehicle cannot leave before the day it was booked for, and a
        //    booking nobody collected for days is a no-show, not a late pickup.
        if (today.isBefore(b.getPickupDate())) {
            throw new InvalidStatusTransitionException(
                "This booking starts on " + b.getPickupDate() + " and cannot be collected yet");
        }
        long daysLate = ChronoUnit.DAYS.between(b.getPickupDate(), today);
        if (daysLate > RentalPolicy.MAX_LATE_PICKUP_DAYS) {
            throw new InvalidStatusTransitionException(
                "The pickup date was " + b.getPickupDate() + ", " + daysLate + " days ago. "
                + "Cancel this booking and create a new one.");
        }

        // 2. The person driving away must still be allowed to.
        userService.requireEligibleToBook(b.getCustomerId(), b.getPickupDate(), b.getReturnDate());

        // 3. The rental must be paid for before the keys change hands.
        if (!paymentService.isSettled(bookingId)) {
            throw new InvalidStatusTransitionException(
                "Booking #" + bookingId + " has not been paid. Collect payment before handing over.");
        }

        // 4. The handover has to happen at the branch the customer booked, during opening hours.
        Branch branch = requireBranch(b.getPickupBranchId());
        requireStaffAtBranch(staffId, branch, "hand this vehicle over");
        requireWithinOpeningHours(branch);

        // 5. The vehicle has to be on the lot and roadworthy.
        Vehicle v = loadVehicle(b.getVehicleId());
        if (v.getStatus() != VehicleStatus.AVAILABLE && v.getStatus() != VehicleStatus.RESERVED) {
            throw new VehicleNotAvailableException("Vehicle cannot be handed over, it is " + v.getStatus());
        }
        if (!maintenanceDao.findBlockingToday(v.getVehicleId(), today, null).isEmpty()) {
            throw new VehicleNotAvailableException("This vehicle is in the workshop today");
        }
        boolean seriousDamage = damageReportDao.findOpenByVehicle(v.getVehicleId()).stream()
            .anyMatch(d -> RentalPolicy.blocksVehicle(d.getDamageSeverity()));
        if (seriousDamage) {
            throw new VehicleNotAvailableException(
                "This vehicle has an unresolved damage report and cannot be handed over");
        }

        int mileage = request.getMileageAtEvent();
        checkMileage(mileage, v.getMileage(), "Pickup");

        // 6. Record it.
        Handover h = new Handover();
        h.setBookingId(bookingId);
        h.setHandoverType(HandoverType.PICKUP);
        h.setProcessedByStaffId(staffId);
        h.setMileageAtEvent(mileage);
        h.setFuelLevel(request.getFuelLevel());
        h.setConditionNotes(request.getConditionNotes());
        h.setStatus(HandoverStatus.COMPLETED);
        h.setHandoverDate(AppClock.now());
        Handover saved = handoverDao.save(h);

        bookingDao.updateStatus(bookingId, BookingStatus.ACTIVE_RENTAL.name());
        v.setMileage(mileage);
        v.setStatus(VehicleStatus.RENTED);
        vehicleDao.update(v);

        auditService.recordStatusChange(ENTITY, bookingId, BookingStatus.APPROVED.name(),
                                        BookingStatus.ACTIVE_RENTAL.name(), staffId,
                                        "Vehicle handed over at " + branch.getName());
        notificationService.safeSend(b.getCustomerId(), "VEHICLE_PICKED_UP",
            "Vehicle for booking #" + bookingId + " has been handed over to you. "
            + "Please return it by " + b.getReturnDate() + ". Enjoy your trip!");

        return handoverDao.findById(saved.getHandoverId()).orElse(saved);
    }

    // ============================================================
    // Return
    // ============================================================
    @Override
    @Transactional
    public Handover recordReturn(int bookingId, int staffId, ReturnVehicleRequest request) {

        Booking b = loadBooking(bookingId);
        if (b.getStatus() != BookingStatus.ACTIVE_RENTAL) {
            throw new InvalidStatusTransitionException(
                "Only ACTIVE_RENTAL bookings can be returned (current: " + b.getStatus() + ")");
        }
        if (handoverDao.findByBookingAndType(bookingId, HandoverType.RETURN.name()).isPresent()) {
            throw new InvalidStatusTransitionException("This booking has already been returned");
        }

        Branch branch = requireBranch(b.getPickupBranchId());
        requireStaffAtBranch(staffId, branch, "take this vehicle back");
        requireWithinOpeningHours(branch);

        Vehicle v = loadVehicle(b.getVehicleId());
        int returnMileage = request.getReturnMileage();
        checkMileage(returnMileage, v.getMileage(), "Return");

        // ---- damage, if any ----
        boolean damageFound = request.hasDamage();
        String severity = null;
        BigDecimal damageCharge = BigDecimal.ZERO;
        if (damageFound) {
            severity = SeverityValidator.normalise(
                request.getDamageSeverity() == null ? "MINOR" : request.getDamageSeverity());
            damageCharge = request.getDamageEstimatedCost() == null
                ? BigDecimal.ZERO : request.getDamageEstimatedCost();
            if (damageCharge.compareTo(BigDecimal.ZERO) < 0) {
                throw new IllegalArgumentException("Repair cost cannot be negative");
            }
        }

        LocalDate today = AppClock.today();

        // ---- what the trip really cost ----
        long lateDays = CostCalculator.lateDays(b.getReturnDate(), today);
        BigDecimal lateFee = CostCalculator.lateFee(v.getRentalPricePerDay(), lateDays);
        BigDecimal finalCost = CostCalculator.finalCost(b.getEstimatedCost(), lateFee, damageCharge);

        // 1. RETURN handover row
        Handover h = new Handover();
        h.setBookingId(bookingId);
        h.setHandoverType(HandoverType.RETURN);
        h.setProcessedByStaffId(staffId);
        h.setMileageAtEvent(returnMileage);
        h.setFuelLevel(request.getFuelLevel());
        h.setConditionNotes(request.getConditionNotes());
        h.setStatus(HandoverStatus.COMPLETED);
        h.setHandoverDate(AppClock.now());
        Handover saved = handoverDao.save(h);

        // 2. Update the odometer
        v.setMileage(returnMileage);

        // 3. File the damage report. Light scratches do not take a car off the
        //    road; anything worse holds it until the report is resolved.
        if (damageFound) {
            DamageReport dr = new DamageReport();
            dr.setVehicleId(v.getVehicleId());
            dr.setHandoverId(saved.getHandoverId());
            dr.setReportedBy(staffId);
            dr.setDescription(request.getDamageDescription());
            dr.setDamageSeverity(severity);
            dr.setEstimatedRepairCost(damageCharge);
            dr.setDamageDate(request.getDamageDate() == null ? today : request.getDamageDate());
            dr.setEventDate(today);                       // reported_date
            dr.setStatus(DamageStatus.UNDER_REVIEW.name());
            damageReportDao.save(dr);
        }

        if (damageFound && RentalPolicy.blocksVehicle(severity)) {
            v.setStatus(VehicleStatus.UNDER_MAINTENANCE);
        } else {
            v.setStatus(VehicleStatus.AVAILABLE);
        }
        vehicleDao.update(v);

        // 4. Close the booking and settle the bill
        bookingDao.updateStatus(bookingId, BookingStatus.COMPLETED.name());
        bookingDao.updateReturnOutcome(bookingId, today, finalCost);
        paymentService.settleFinalAmount(bookingId, finalCost, staffId,
            "Returned " + today + (lateDays > 0 ? ", " + lateDays + " day(s) late" : " on time")
            + (damageFound ? ", damage charge " + damageCharge : ""));

        // 5. If the vehicle stayed on the road, it may be due out again straight away
        availabilityService.refreshReservationStatus(v.getVehicleId());

        auditService.recordStatusChange(ENTITY, bookingId, BookingStatus.ACTIVE_RENTAL.name(),
                                        BookingStatus.COMPLETED.name(), staffId,
                                        "Returned at " + branch.getName() + "; total " + finalCost);

        StringBuilder message = new StringBuilder("Thank you! The vehicle for booking #")
            .append(bookingId).append(" has been returned.");
        if (lateDays > 0) {
            message.append(" It came back ").append(lateDays).append(" day(s) late, so a late fee of ")
                   .append(lateFee).append(" was added.");
        }
        if (damageFound) {
            message.append(" A damage report was filed");
            if (damageCharge.compareTo(BigDecimal.ZERO) > 0) {
                message.append(" and a repair charge of ").append(damageCharge).append(" applied");
            }
            message.append(".");
        }
        message.append(" Total: ").append(finalCost).append(".");
        notificationService.safeSend(b.getCustomerId(), "VEHICLE_RETURNED", message.toString());

        if (damageFound && RentalPolicy.blocksVehicle(severity)) {
            notificationService.safeSendToBranchStaff(v.getBranchId(), "VEHICLE_OFF_ROAD",
                v.getModel() + " (" + v.getPlateNumber() + ") is off the road until its damage "
                + "report is resolved.");
        }

        return handoverDao.findById(saved.getHandoverId()).orElse(saved);
    }

    @Override
    public List<Handover> listActive() {
        return handoverDao.findActive();
    }

    @Override
    public List<Handover> listByBooking(int bookingId) {
        loadBooking(bookingId);
        return handoverDao.findByBooking(bookingId);
    }

    // ============================================================
    // Shared checks
    // ============================================================
    private Booking loadBooking(int bookingId) {
        return bookingDao.findById(bookingId)
            .orElseThrow(() -> new ResourceNotFoundException("Booking not found: " + bookingId));
    }

    private Vehicle loadVehicle(int vehicleId) {
        return vehicleDao.findById(vehicleId)
            .orElseThrow(() -> new ResourceNotFoundException("Vehicle not found: " + vehicleId));
    }

    private Branch requireBranch(int branchId) {
        return branchDao.findById(branchId)
            .orElseThrow(() -> new ResourceNotFoundException("Branch not found: " + branchId));
    }

    /**
     * Counter staff work at one branch and can only process handovers there.
     * Administrators are not tied to a branch, so they can act anywhere.
     */
    private void requireStaffAtBranch(int staffId, Branch branch, String action) {
        User u = userDao.findById(staffId)
            .orElseThrow(() -> new ResourceNotFoundException("Staff member not found: " + staffId));

        if (u.getRole() == Role.ADMINISTRATOR) {
            return;
        }
        if (!(u instanceof Staff)) {
            throw new UnauthorizedActionException("Only staff can " + action);
        }
        Integer staffBranch = ((Staff) u).getBranchId();
        if (staffBranch == null || staffBranch != branch.getBranchId()) {
            throw new UnauthorizedActionException(
                "This booking belongs to " + branch.getName() + ". Only staff at that branch can " + action + ".");
        }
    }

    /** Vehicles change hands at the counter, so the counter has to be open. */
    private void requireWithinOpeningHours(Branch branch) {
        if (branch.getOpenTime() == null || branch.getCloseTime() == null) {
            return;   // branch has no hours recorded — nothing to enforce
        }
        LocalTime now = AppClock.now().toLocalTime();
        boolean open = !now.isBefore(branch.getOpenTime()) && !now.isAfter(branch.getCloseTime());
        if (!open) {
            throw new InvalidStatusTransitionException(
                branch.getName() + " is closed right now (open " + branch.getOpenTime()
                + " to " + branch.getCloseTime() + ")");
        }
    }

    /**
     * An odometer only goes forwards, and it does not jump 100,000 km in one
     * rental — that is a typing slip, and it used to be written into the vehicle
     * record permanently.
     */
    private void checkMileage(int reading, int recorded, String label) {
        if (reading < recorded) {
            throw new IllegalArgumentException(
                label + " mileage (" + reading + ") is lower than the recorded mileage (" + recorded + ")");
        }
        if (reading - recorded > MAX_PLAUSIBLE_MILEAGE_JUMP) {
            throw new IllegalArgumentException(
                label + " mileage (" + reading + ") is " + (reading - recorded)
                + " km above the last reading (" + recorded + "). Please check the number.");
        }
    }
}
