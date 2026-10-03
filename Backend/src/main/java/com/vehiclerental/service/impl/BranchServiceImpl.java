package com.vehiclerental.service.impl;

import com.vehiclerental.dao.*;
import com.vehiclerental.enums.TransferStatus;
import com.vehiclerental.enums.VehicleStatus;
import com.vehiclerental.exception.InvalidStatusTransitionException;
import com.vehiclerental.exception.ResourceNotFoundException;
import com.vehiclerental.model.Booking;
import com.vehiclerental.model.Branch;
import com.vehiclerental.model.Vehicle;
import com.vehiclerental.model.VehicleTransfer;
import com.vehiclerental.service.AuditService;
import com.vehiclerental.service.BranchService;
import com.vehiclerental.service.NotificationService;
import com.vehiclerental.util.AppClock;
import com.vehiclerental.util.RentalPolicy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.Set;

@Service
public class BranchServiceImpl implements BranchService {

    private static final String BRANCH = "BRANCH";
    private static final String TRANSFER = "VEHICLE_TRANSFER";
    private static final Set<String> BRANCH_STATUSES = Set.of("ACTIVE", "INACTIVE");

    private final BranchDao branchDao;
    private final VehicleTransferDao transferDao;
    private final VehicleDao vehicleDao;
    private final BookingDao bookingDao;
    private final UserDao userDao;
    private final NotificationService notificationService;
    private final AuditService auditService;

    public BranchServiceImpl(BranchDao branchDao,
                             VehicleTransferDao transferDao,
                             VehicleDao vehicleDao,
                             BookingDao bookingDao,
                             UserDao userDao,
                             NotificationService notificationService,
                             AuditService auditService) {
        this.branchDao = branchDao;
        this.transferDao = transferDao;
        this.vehicleDao = vehicleDao;
        this.bookingDao = bookingDao;
        this.userDao = userDao;
        this.notificationService = notificationService;
        this.auditService = auditService;
    }

    @Override
    @Transactional
    public Branch create(Branch b, int actorUserId) {
        validateDetails(b);
        b.setStatus(normaliseStatus(b.getStatus() == null ? "ACTIVE" : b.getStatus()));
        Branch saved = branchDao.save(b);
        auditService.record(BRANCH, saved.getBranchId(), "CREATE", actorUserId,
                            "Branch " + saved.getName() + " opened");
        return saved;
    }

    @Override
    public Branch findById(int branchId) {
        return branchDao.findById(branchId)
            .orElseThrow(() -> new ResourceNotFoundException("Branch not found: " + branchId));
    }

    @Override
    public List<Branch> findAll() {
        return branchDao.findAll();
    }

    @Override
    @Transactional
    public Branch update(int branchId, Branch updates, int actorUserId) {
        Branch existing = findById(branchId);
        validateDetails(updates);

        existing.setName(updates.getName());
        existing.setStreet(updates.getStreet());
        existing.setCity(updates.getCity());
        existing.setDistrict(updates.getDistrict());
        existing.setContactNumber(updates.getContactNumber());
        existing.setOpenTime(updates.getOpenTime());
        existing.setCloseTime(updates.getCloseTime());

        // Status is not a free-text field. A typo here used to block every
        // booking at the branch, because the code compares against "ACTIVE".
        if (updates.getStatus() != null) {
            String target = normaliseStatus(updates.getStatus());
            if (!target.equals(existing.getStatus())) {
                if ("INACTIVE".equals(target)) {
                    requireNothingOutstanding(branchId);
                }
                auditService.recordStatusChange(BRANCH, branchId, existing.getStatus(), target,
                                                actorUserId, "Changed while editing the branch");
                existing.setStatus(target);
            }
        }

        branchDao.update(existing);
        auditService.record(BRANCH, branchId, "UPDATE", actorUserId, "Branch details edited");
        return existing;
    }

    /**
     * Closing a branch is not just a flag: customers may be due to collect cars
     * there and staff may be assigned to it.
     */
    @Override
    @Transactional
    public void deactivate(int branchId, int actorUserId) {
        Branch b = findById(branchId);
        if ("INACTIVE".equals(b.getStatus())) {
            return;
        }
        requireNothingOutstanding(branchId);

        branchDao.updateStatus(branchId, "INACTIVE");
        auditService.recordStatusChange(BRANCH, branchId, "ACTIVE", "INACTIVE", actorUserId,
                                        "Branch closed to new business");
        notificationService.safeSendToAdministrators("BRANCH_CLOSED",
            "Branch " + b.getName() + " has been closed to new business.");
    }

    @Override
    @Transactional
    public void reactivate(int branchId, int actorUserId) {
        Branch b = findById(branchId);
        if ("ACTIVE".equals(b.getStatus())) {
            return;
        }
        branchDao.updateStatus(branchId, "ACTIVE");
        auditService.recordStatusChange(BRANCH, branchId, "INACTIVE", "ACTIVE", actorUserId,
                                        "Branch re-opened");
    }

    @Override
    @Transactional
    public void delete(int branchId, int actorUserId) {
        Branch b = findById(branchId);

        if (!vehicleDao.findByBranch(branchId).isEmpty()) {
            throw new InvalidStatusTransitionException(
                "This branch still holds vehicles. Transfer them elsewhere before deleting it.");
        }
        if (!userDao.findStaffByBranch(branchId).isEmpty()) {
            throw new InvalidStatusTransitionException(
                "Staff are still assigned to this branch. Move them before deleting it.");
        }
        if (!branchBookings(branchId).isEmpty()) {
            throw new InvalidStatusTransitionException(
                "This branch has booking history and cannot be deleted. Close it instead.");
        }

        branchDao.delete(branchId);
        auditService.record(BRANCH, branchId, "DELETE", actorUserId, "Branch " + b.getName() + " deleted");
    }

    // ==================== Vehicle transfers ====================

    /**
     * Moving a vehicle to another branch invalidates every booking that was
     * going to collect it from the old one, so those have to be dealt with
     * first. Statuses alone were never enough — a car sitting AVAILABLE today
     * can still be promised to someone next week.
     */
    @Override
    @Transactional
    public VehicleTransfer requestTransfer(int vehicleId, int toBranchId, LocalDate transferDate,
                                           String reason, int actorUserId) {

        Vehicle vehicle = vehicleDao.findById(vehicleId)
            .orElseThrow(() -> new ResourceNotFoundException("Vehicle not found: " + vehicleId));

        if (transferDate == null) {
            throw new IllegalArgumentException("Transfer date is required");
        }
        if (transferDate.isBefore(AppClock.today())) {
            throw new IllegalArgumentException("Transfer date cannot be in the past");
        }

        Branch target = findById(toBranchId);
        if (!"ACTIVE".equals(target.getStatus())) {
            throw new IllegalArgumentException("Cannot transfer a vehicle to a closed branch");
        }
        if (vehicle.getBranchId() == toBranchId) {
            throw new IllegalArgumentException("Vehicle is already at branch " + toBranchId);
        }
        if (!transferDao.findPendingByVehicle(vehicleId).isEmpty()) {
            throw new InvalidStatusTransitionException(
                "This vehicle already has a transfer pending");
        }
        if (vehicle.getStatus() == VehicleStatus.RENTED) {
            throw new InvalidStatusTransitionException(
                "Cannot transfer a vehicle that is currently out with a customer");
        }

        // Any live booking that is still running on or after the transfer date
        // would be collected from the wrong branch.
        List<Booking> affected = bookingDao.findLiveByVehicleInWindow(
            vehicleId, transferDate, transferDate.plusDays(RentalPolicy.MAX_ADVANCE_DAYS));
        if (!affected.isEmpty()) {
            Booking b = affected.get(0);
            throw new InvalidStatusTransitionException(
                "Booking #" + b.getBookingId() + " (" + b.getPickupDate() + " to " + b.getReturnDate()
                + ") collects this vehicle from its current branch. Cancel it before transferring.");
        }

        VehicleTransfer t = new VehicleTransfer();
        t.setVehicleId(vehicleId);
        t.setFromBranchId(vehicle.getBranchId());
        t.setToBranchId(toBranchId);
        t.setTransferDate(transferDate);
        t.setStatus(TransferStatus.PENDING);
        t.setReason(reason);
        VehicleTransfer saved = transferDao.save(t);

        auditService.record(TRANSFER, saved.getTransferId(), "CREATE", actorUserId,
            vehicle.getPlateNumber() + " scheduled to move to " + target.getName() + " on " + transferDate);
        notificationService.safeSendToBranchStaff(vehicle.getBranchId(), "VEHICLE_TRANSFER_SCHEDULED",
            vehicle.getModel() + " (" + vehicle.getPlateNumber() + ") leaves for "
            + target.getName() + " on " + transferDate + ".");
        return saved;
    }

    @Override
    @Transactional
    public void completeTransfer(int transferId, int actorUserId) {
        VehicleTransfer t = loadTransfer(transferId);
        if (t.getStatus() != TransferStatus.PENDING) {
            throw new InvalidStatusTransitionException("Only PENDING transfers can be completed");
        }
        if (t.getTransferDate().isAfter(AppClock.today())) {
            throw new InvalidStatusTransitionException(
                "This transfer is planned for " + t.getTransferDate() + " and cannot be completed early");
        }

        Vehicle vehicle = vehicleDao.findById(t.getVehicleId())
            .orElseThrow(() -> new ResourceNotFoundException("Vehicle not found: " + t.getVehicleId()));
        if (vehicle.getStatus() == VehicleStatus.RENTED) {
            throw new InvalidStatusTransitionException(
                "Cannot complete the transfer while the vehicle is out with a customer");
        }

        Branch target = findById(t.getToBranchId());
        if (!"ACTIVE".equals(target.getStatus())) {
            throw new InvalidStatusTransitionException(
                "The destination branch has closed. Cancel this transfer and raise a new one.");
        }

        // Change the vehicle's branch and mark the transfer completed — two writes in one transaction.
        vehicleDao.updateBranch(t.getVehicleId(), t.getToBranchId());
        transferDao.updateStatus(transferId, TransferStatus.COMPLETED.name());

        auditService.recordStatusChange(TRANSFER, transferId, TransferStatus.PENDING.name(),
                                        TransferStatus.COMPLETED.name(), actorUserId,
                                        vehicle.getPlateNumber() + " now at " + target.getName());
        notificationService.safeSendToBranchStaff(t.getToBranchId(), "VEHICLE_ARRIVED",
            vehicle.getModel() + " (" + vehicle.getPlateNumber() + ") has arrived at your branch.");
    }

    @Override
    @Transactional
    public void cancelTransfer(int transferId, int actorUserId) {
        VehicleTransfer t = loadTransfer(transferId);
        if (t.getStatus() != TransferStatus.PENDING) {
            throw new InvalidStatusTransitionException("Only PENDING transfers can be cancelled");
        }
        transferDao.updateStatus(transferId, TransferStatus.CANCELLED.name());
        auditService.recordStatusChange(TRANSFER, transferId, TransferStatus.PENDING.name(),
                                        TransferStatus.CANCELLED.name(), actorUserId, null);
    }

    @Override
    public List<VehicleTransfer> listPendingTransfers() {
        return transferDao.findPending();
    }

    @Override
    public List<VehicleTransfer> listAllTransfers() {
        return transferDao.findAll();
    }

    // ==================== Helpers ====================

    private void requireNothingOutstanding(int branchId) {
        List<Booking> live = branchBookings(branchId).stream()
            .filter(b -> b.getStatus().name().equals("PENDING_APPROVAL")
                      || b.getStatus().name().equals("APPROVED")
                      || b.getStatus().name().equals("ACTIVE_RENTAL"))
            .toList();
        if (!live.isEmpty()) {
            Booking b = live.get(0);
            throw new InvalidStatusTransitionException(
                "Booking #" + b.getBookingId() + " (" + b.getStatus() + ") still collects from this branch. "
                + "Deal with it before closing the branch.");
        }

        boolean vehiclesOut = vehicleDao.findByBranch(branchId).stream()
            .anyMatch(v -> v.getStatus() == VehicleStatus.RENTED || v.getStatus() == VehicleStatus.RESERVED);
        if (vehiclesOut) {
            throw new InvalidStatusTransitionException(
                "Vehicles from this branch are still out with customers");
        }
    }

    /** Every booking whose pickup branch is this one. */
    private List<Booking> branchBookings(int branchId) {
        return bookingDao.findAll().stream()
            .filter(b -> b.getPickupBranchId() == branchId)
            .toList();
    }

    private VehicleTransfer loadTransfer(int transferId) {
        return transferDao.findById(transferId)
            .orElseThrow(() -> new ResourceNotFoundException("Transfer not found: " + transferId));
    }

    private void validateDetails(Branch b) {
        if (b.getName() == null || b.getName().isBlank()) {
            throw new IllegalArgumentException("Branch name is required");
        }
        if (b.getOpenTime() != null && b.getCloseTime() != null
                && !b.getCloseTime().isAfter(b.getOpenTime())) {
            throw new IllegalArgumentException("Closing time must be after opening time");
        }
    }

    private String normaliseStatus(String status) {
        String s = status == null ? "" : status.trim().toUpperCase();
        if (!BRANCH_STATUSES.contains(s)) {
            throw new IllegalArgumentException("Branch status must be ACTIVE or INACTIVE");
        }
        return s;
    }
}
