package com.vehiclerental.service.impl;

import com.vehiclerental.dao.*;
import com.vehiclerental.enums.BookingStatus;
import com.vehiclerental.enums.VehicleStatus;
import com.vehiclerental.exception.InvalidStatusTransitionException;
import com.vehiclerental.exception.ResourceNotFoundException;
import com.vehiclerental.model.Booking;
import com.vehiclerental.model.Branch;
import com.vehiclerental.model.Vehicle;
import com.vehiclerental.service.AuditService;
import com.vehiclerental.service.AvailabilityService;
import com.vehiclerental.service.VehicleService;
import com.vehiclerental.util.AppClock;
import com.vehiclerental.util.DateRangeValidator;
import com.vehiclerental.util.RentalPolicy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.Year;
import java.util.List;
import java.util.Optional;
import java.util.Set;

@Service
public class VehicleServiceImpl implements VehicleService {

    private static final String ENTITY = "VEHICLE";

    /** The categories the fleet is built from — also what the web app draws. */
    private static final Set<String> CATEGORIES =
        Set.of("CAR", "SUV", "VAN", "BUS", "LUXURY", "PICKUP");

    private final VehicleDao vehicleDao;
    private final BranchDao branchDao;
    private final BookingDao bookingDao;
    private final MaintenanceDao maintenanceDao;
    private final DamageReportDao damageReportDao;
    private final InsurancePolicyDao insurancePolicyDao;
    private final VehicleTransferDao transferDao;
    private final AvailabilityService availabilityService;
    private final AuditService auditService;

    public VehicleServiceImpl(VehicleDao vehicleDao, BranchDao branchDao, BookingDao bookingDao,
                              MaintenanceDao maintenanceDao, DamageReportDao damageReportDao,
                              InsurancePolicyDao insurancePolicyDao, VehicleTransferDao transferDao,
                              AvailabilityService availabilityService, AuditService auditService) {
        this.vehicleDao = vehicleDao;
        this.branchDao = branchDao;
        this.bookingDao = bookingDao;
        this.maintenanceDao = maintenanceDao;
        this.damageReportDao = damageReportDao;
        this.insurancePolicyDao = insurancePolicyDao;
        this.transferDao = transferDao;
        this.availabilityService = availabilityService;
        this.auditService = auditService;
    }

    @Override
    @Transactional
    public Vehicle create(Vehicle v, int actorUserId) {
        Branch branch = branchDao.findById(v.getBranchId())
            .orElseThrow(() -> new ResourceNotFoundException("Branch not found: " + v.getBranchId()));
        if (!"ACTIVE".equals(branch.getStatus())) {
            throw new IllegalArgumentException("Cannot add a vehicle to a closed branch");
        }

        v.setPlateNumber(normalisePlate(v.getPlateNumber()));
        if (vehicleDao.findByPlate(v.getPlateNumber()).isPresent()) {
            throw new IllegalArgumentException(
                "A vehicle with plate number " + v.getPlateNumber() + " is already registered");
        }

        // A new vehicle joins the fleet available; it cannot be created already
        // rented or reserved, because those states are owned by the booking flow.
        if (v.getStatus() == null || v.getStatus() == VehicleStatus.RENTED
                || v.getStatus() == VehicleStatus.RESERVED) {
            v.setStatus(VehicleStatus.AVAILABLE);
        }

        validate(v);
        Vehicle saved = vehicleDao.save(v);
        auditService.record(ENTITY, saved.getVehicleId(), "CREATE", actorUserId,
                            saved.getModel() + " (" + saved.getPlateNumber() + ") added to branch "
                            + branch.getName());
        return saved;
    }

    @Override
    public Vehicle findById(int vehicleId) {
        return vehicleDao.findById(vehicleId)
            .orElseThrow(() -> new ResourceNotFoundException("Vehicle not found: " + vehicleId));
    }

    @Override
    public List<Vehicle> findAll(Integer branchId, VehicleStatus status) {
        if (branchId != null && status != null) {
            return vehicleDao.findByBranch(branchId).stream()
                .filter(v -> v.getStatus() == status)
                .toList();
        }
        if (branchId != null) {
            return vehicleDao.findByBranch(branchId);
        }
        if (status != null) {
            return vehicleDao.findByStatus(status.name());
        }
        return vehicleDao.findAll();
    }

    @Override
    public List<Vehicle> findAvailable(LocalDate pickup, LocalDate returnDate, Integer branchId) {
        DateRangeValidator.validateBookingWindow(pickup, returnDate);
        return vehicleDao.findAvailable(pickup, returnDate, branchId);
    }

    /**
     * Editing the details of a vehicle. Two things are deliberately NOT editable
     * here, because they belong to other workflows:
     *   - branch_id, which only changes through a completed branch transfer
     *   - status, which only changes through changeStatus or a handover
     * Silently ignoring them (the old behaviour) hid mistakes, so we say so.
     */
    @Override
    @Transactional
    public Vehicle update(int vehicleId, Vehicle upd, int actorUserId) {
        Vehicle v = findById(vehicleId);

        if (upd.getBranchId() != 0 && upd.getBranchId() != v.getBranchId()) {
            throw new IllegalArgumentException(
                "A vehicle changes branch through a branch transfer, not by editing it");
        }
        if (upd.getStatus() != null && upd.getStatus() != v.getStatus()) {
            throw new IllegalArgumentException(
                "Use the status action to change a vehicle's status");
        }

        String newPlate = normalisePlate(upd.getPlateNumber());
        if (newPlate != null && !newPlate.equals(v.getPlateNumber())) {
            Optional<Vehicle> clash = vehicleDao.findByPlate(newPlate);
            if (clash.isPresent() && clash.get().getVehicleId() != vehicleId) {
                throw new IllegalArgumentException(
                    "A vehicle with plate number " + newPlate + " is already registered");
            }
            v.setPlateNumber(newPlate);
        }

        BigDecimal oldPrice = v.getRentalPricePerDay();

        v.setModel(upd.getModel());
        v.setCategory(upd.getCategory());
        v.setManufactureYear(upd.getManufactureYear());
        v.setRentalPricePerDay(upd.getRentalPricePerDay());
        v.setPassengerCapacity(upd.getPassengerCapacity());
        v.setFuelType(upd.getFuelType());
        v.setImageUrl(upd.getImageUrl());

        // The odometer is owned by the handover process. Allow a correction, but
        // never let an edit silently rewind a vehicle's recorded distance.
        if (upd.getMileage() != 0 && upd.getMileage() != v.getMileage()) {
            if (upd.getMileage() < v.getMileage()) {
                throw new IllegalArgumentException(
                    "Mileage cannot be reduced (recorded: " + v.getMileage() + ")");
            }
            v.setMileage(upd.getMileage());
        }

        validate(v);
        vehicleDao.update(v);

        if (oldPrice != null && v.getRentalPricePerDay().compareTo(oldPrice) != 0) {
            // Existing quotes are honoured: a booking keeps the price it was given.
            auditService.record(ENTITY, vehicleId, "UPDATE", actorUserId,
                "Daily rate changed from " + oldPrice + " to " + v.getRentalPricePerDay()
                + " (existing bookings keep their original quote)");
        } else {
            auditService.record(ENTITY, vehicleId, "UPDATE", actorUserId, "Vehicle details edited");
        }
        return v;
    }

    /**
     * The fleet status of a vehicle is not free-form. RENTED and RESERVED belong
     * to the booking flow, and a vehicle that is promised to a customer cannot be
     * quietly taken off the road.
     */
    @Override
    @Transactional
    public void changeStatus(int vehicleId, VehicleStatus newStatus, int actorUserId, String reason) {
        Vehicle v = findById(vehicleId);
        VehicleStatus current = v.getStatus();

        if (current == newStatus) {
            return;
        }
        if (newStatus == VehicleStatus.RENTED) {
            throw new InvalidStatusTransitionException(
                "A vehicle becomes RENTED by recording a pickup, not by editing its status");
        }
        if (newStatus == VehicleStatus.RESERVED) {
            throw new InvalidStatusTransitionException(
                "RESERVED is set automatically when an approved pickup is due");
        }

        if (current == VehicleStatus.RENTED) {
            // Somebody is driving it. Letting it go back in the pool would let a
            // second customer book a car that is not there.
            List<Booking> active = bookingDao.findLiveByVehicle(vehicleId).stream()
                .filter(b -> b.getStatus() == BookingStatus.ACTIVE_RENTAL)
                .toList();
            if (!active.isEmpty()) {
                throw new InvalidStatusTransitionException(
                    "This vehicle is out on booking #" + active.get(0).getBookingId()
                    + ". Record the return before changing its status.");
            }
        }

        if (newStatus == VehicleStatus.UNDER_MAINTENANCE || newStatus == VehicleStatus.UNAVAILABLE) {
            // Nothing may be promised to a customer between now and the end of
            // the booking horizon.
            availabilityService.requireNoLiveBookings(vehicleId, AppClock.today(),
                AppClock.today().plusDays(RentalPolicy.MAX_ADVANCE_DAYS),
                "take this vehicle off the road");
        }

        if (newStatus == VehicleStatus.AVAILABLE) {
            LocalDate today = AppClock.today();
            if (!maintenanceDao.findBlockingToday(vehicleId, today, null).isEmpty()) {
                throw new InvalidStatusTransitionException(
                    "This vehicle is still booked into the workshop today. Complete the maintenance record first.");
            }
            boolean openSeriousDamage = damageReportDao.findOpenByVehicle(vehicleId).stream()
                .anyMatch(d -> RentalPolicy.blocksVehicle(d.getDamageSeverity()));
            if (openSeriousDamage) {
                throw new InvalidStatusTransitionException(
                    "This vehicle has an unresolved damage report. Resolve it before putting the car back on the road.");
            }
        }

        vehicleDao.updateStatus(vehicleId, newStatus.name());
        auditService.recordStatusChange(ENTITY, vehicleId, current.name(), newStatus.name(),
                                        actorUserId, reason);

        if (newStatus == VehicleStatus.AVAILABLE) {
            availabilityService.refreshReservationStatus(vehicleId);
        }
    }

    /**
     * A vehicle with history is part of the record and must not vanish — the
     * bookings, damage reports and invoices that point at it would lose their
     * meaning. Retiring one means marking it UNAVAILABLE.
     */
    @Override
    @Transactional
    public void delete(int vehicleId, int actorUserId) {
        Vehicle v = findById(vehicleId);

        if (!bookingDao.findByVehicle(vehicleId).isEmpty()) {
            throw new InvalidStatusTransitionException(
                "This vehicle has rental history and cannot be deleted. Set it to UNAVAILABLE to retire it.");
        }
        if (!maintenanceDao.findByVehicle(vehicleId).isEmpty()) {
            throw new InvalidStatusTransitionException(
                "This vehicle has maintenance records and cannot be deleted. Set it to UNAVAILABLE to retire it.");
        }
        if (!damageReportDao.findByVehicle(vehicleId).isEmpty()) {
            throw new InvalidStatusTransitionException(
                "This vehicle has damage reports and cannot be deleted. Set it to UNAVAILABLE to retire it.");
        }
        if (!insurancePolicyDao.findByVehicle(vehicleId).isEmpty()) {
            throw new InvalidStatusTransitionException(
                "Remove this vehicle's insurance policies before deleting it.");
        }
        if (!transferDao.findPendingByVehicle(vehicleId).isEmpty()) {
            throw new InvalidStatusTransitionException(
                "This vehicle has a pending branch transfer. Cancel it before deleting the vehicle.");
        }

        vehicleDao.delete(vehicleId);
        auditService.record(ENTITY, vehicleId, "DELETE", actorUserId,
                            v.getModel() + " (" + v.getPlateNumber() + ") removed from the fleet");
    }

    // ============================================================
    // Validation
    // ============================================================
    private void validate(Vehicle v) {
        if (v.getPlateNumber() == null || v.getPlateNumber().isBlank()) {
            throw new IllegalArgumentException("Plate number is required");
        }
        if (v.getModel() == null || v.getModel().isBlank()) {
            throw new IllegalArgumentException("Model is required");
        }
        if (v.getRentalPricePerDay() == null || v.getRentalPricePerDay().compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Rental price per day must be greater than 0");
        }
        if (v.getMileage() < 0) {
            throw new IllegalArgumentException("Mileage cannot be negative");
        }
        if (v.getPassengerCapacity() != null && v.getPassengerCapacity() <= 0) {
            throw new IllegalArgumentException("Passenger capacity must be at least 1");
        }
        if (v.getManufactureYear() != null) {
            int thisYear = Year.now().getValue();
            if (v.getManufactureYear() < 1950 || v.getManufactureYear() > thisYear + 1) {
                throw new IllegalArgumentException(
                    "Manufacture year must be between 1950 and " + (thisYear + 1));
            }
        }
        if (v.getCategory() == null || v.getCategory().isBlank()) {
            throw new IllegalArgumentException(
                "Category is required (" + String.join(", ", CATEGORIES) + ")");
        }
        v.setCategory(v.getCategory().trim().toUpperCase());
        if (!CATEGORIES.contains(v.getCategory())) {
            throw new IllegalArgumentException(
                "Category must be one of " + String.join(", ", CATEGORIES));
        }
    }

    private String normalisePlate(String plate) {
        return plate == null ? null : plate.trim().toUpperCase();
    }
}
