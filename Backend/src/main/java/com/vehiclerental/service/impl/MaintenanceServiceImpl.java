package com.vehiclerental.service.impl;

import com.vehiclerental.dao.DamageReportDao;
import com.vehiclerental.dao.MaintenanceDao;
import com.vehiclerental.dao.VehicleDao;
import com.vehiclerental.dto.request.CreateMaintenanceRequest;
import com.vehiclerental.dto.response.MaintenanceResponse;
import com.vehiclerental.enums.MaintenanceStatus;
import com.vehiclerental.enums.VehicleStatus;
import com.vehiclerental.exception.InvalidStatusTransitionException;
import com.vehiclerental.exception.ResourceNotFoundException;
import com.vehiclerental.model.MaintenanceRecord;
import com.vehiclerental.model.Vehicle;
import com.vehiclerental.service.AuditService;
import com.vehiclerental.service.AvailabilityService;
import com.vehiclerental.service.MaintenanceService;
import com.vehiclerental.service.NotificationService;
import com.vehiclerental.util.AppClock;
import com.vehiclerental.util.RentalPolicy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class MaintenanceServiceImpl implements MaintenanceService {

    private static final String ENTITY = "MAINTENANCE";

    private final MaintenanceDao maintenanceDao;
    private final VehicleDao vehicleDao;
    private final DamageReportDao damageReportDao;
    private final AvailabilityService availabilityService;
    private final NotificationService notificationService;
    private final AuditService auditService;

    public MaintenanceServiceImpl(MaintenanceDao maintenanceDao, VehicleDao vehicleDao,
                                  DamageReportDao damageReportDao,
                                  AvailabilityService availabilityService,
                                  NotificationService notificationService,
                                  AuditService auditService) {
        this.maintenanceDao = maintenanceDao;
        this.vehicleDao = vehicleDao;
        this.damageReportDao = damageReportDao;
        this.availabilityService = availabilityService;
        this.notificationService = notificationService;
        this.auditService = auditService;
    }

    /**
     * Booking a vehicle into the workshop is itself a reservation of that
     * vehicle, for a window of dates. It has to respect the same calendar the
     * customers book against, in both directions:
     *   - work cannot be scheduled over a rental that is already promised
     *   - once scheduled, the window keeps customers out (see VehicleDao.findAvailable)
     */
    @Override
    @Transactional
    public MaintenanceResponse create(int staffId, CreateMaintenanceRequest request) {

        Vehicle vehicle = vehicleDao.findById(request.getVehicleId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Vehicle not found: " + request.getVehicleId()));

        LocalDate today = AppClock.today();
        LocalDate start = request.getEventDate();
        LocalDate end = request.blockedUntil();

        if (start == null) {
            throw new IllegalArgumentException("Service date is required");
        }
        if (start.isBefore(today)) {
            throw new IllegalArgumentException("Service date cannot be in the past");
        }
        if (end.isBefore(start)) {
            throw new IllegalArgumentException("The expected end date cannot be before the service date");
        }
        if (request.getNextServiceDate() != null && !request.getNextServiceDate().isAfter(end)) {
            throw new IllegalArgumentException(
                "The next service reminder must fall after this service finishes");
        }
        if (request.isStartNow() && !start.isEqual(today)) {
            throw new IllegalArgumentException(
                "Work can only be started immediately when the service date is today");
        }

        // The workshop cannot have the same car booked in twice.
        List<MaintenanceRecord> clashes = maintenanceDao.findBlocking(
            vehicle.getVehicleId(), start, end, null);
        if (!clashes.isEmpty()) {
            MaintenanceRecord m = clashes.get(0);
            throw new InvalidStatusTransitionException(
                "This vehicle is already booked into the workshop from " + m.getEventDate()
                + " to " + m.blockedUntil());
        }

        // And it cannot be booked in over a rental that has been promised.
        availabilityService.requireNoLiveBookings(vehicle.getVehicleId(), start, end,
                                                  "schedule this maintenance");

        MaintenanceRecord m = new MaintenanceRecord();
        m.setVehicleId(request.getVehicleId());
        m.setEventDate(start);
        m.setExpectedEndDate(request.getExpectedEndDate());
        m.setStatus(request.isStartNow()
                    ? MaintenanceStatus.IN_PROGRESS.name()
                    : MaintenanceStatus.SCHEDULED.name());
        m.setDescription(request.getDescription());
        m.setHandledByStaffId(staffId);
        m.setRepairType(request.getRepairType());
        m.setCost(request.getCost());
        m.setServiceProvider(request.getServiceProvider());
        m.setNextServiceDate(request.getNextServiceDate());
        maintenanceDao.save(m);


        // Scheduling a service for next month no longer strands the car now.
        if (coversToday(m, today)) {
            blockVehicle(vehicle, m);
        }

        auditService.record(ENTITY, m.getEventId(), "CREATE", staffId,
            "Workshop booking for vehicle " + vehicle.getPlateNumber() + " " + start + ".." + end);
        return toResponse(m);
    }

    @Override
    public MaintenanceResponse findById(int eventId) {
        return toResponse(loadOrThrow(eventId));
    }

    @Override
    public List<MaintenanceResponse> listAll() {
        return maintenanceDao.findAll().stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    @Override
    public List<MaintenanceResponse> listByVehicle(int vehicleId) {
        return maintenanceDao.findByVehicle(vehicleId).stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    @Override
    public List<MaintenanceResponse> listUpcoming(int daysAhead) {
        if (daysAhead <= 0) {
            daysAhead = 30;
        }
        return maintenanceDao.findUpcoming(daysAhead).stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    /**
     * SCHEDULED -> IN_PROGRESS -> COMPLETED, with CANCELLED available until the
     * work is finished. Anything else is a mistake, and a finished record is final.
     */
    @Override
    @Transactional
    public void updateStatus(int eventId, String newStatus, int actorUserId) {
        MaintenanceStatus target = MaintenanceStatus.valueOf(newStatus.toUpperCase());
        MaintenanceRecord m = loadOrThrow(eventId);
        MaintenanceStatus current = MaintenanceStatus.valueOf(m.getStatus());

        if (target == current) {
            return;
        }
        if (current == MaintenanceStatus.COMPLETED || current == MaintenanceStatus.CANCELLED) {
            throw new InvalidStatusTransitionException(
                "This maintenance record is " + current + " and can no longer be changed");
        }
        if (target == MaintenanceStatus.SCHEDULED) {
            throw new InvalidStatusTransitionException("Work that has started cannot go back to SCHEDULED");
        }
        if (target == MaintenanceStatus.COMPLETED) {
            completeMaintenance(eventId, actorUserId);
            return;
        }

        maintenanceDao.updateStatus(eventId, target.name());
        auditService.recordStatusChange(ENTITY, eventId, current.name(), target.name(), actorUserId, null);

        Vehicle vehicle = loadVehicle(m.getVehicleId());
        if (target == MaintenanceStatus.IN_PROGRESS) {
            blockVehicle(vehicle, m);
        } else if (target == MaintenanceStatus.CANCELLED) {
            releaseVehicleIfFree(vehicle, eventId);
        }
    }

    /**
     * When the work finishes the vehicle goes back on the road — but only if
     * nothing else is holding it there (another workshop booking, or an
     * unresolved damage report).
     */
    @Override
    @Transactional
    public void completeMaintenance(int eventId, int actorUserId) {
        MaintenanceRecord m = loadOrThrow(eventId);
        MaintenanceStatus current = MaintenanceStatus.valueOf(m.getStatus());

        if (current == MaintenanceStatus.COMPLETED) {
            throw new InvalidStatusTransitionException("Maintenance record is already COMPLETED");
        }
        if (current == MaintenanceStatus.CANCELLED) {
            throw new InvalidStatusTransitionException("A cancelled maintenance record cannot be completed");
        }

        maintenanceDao.updateStatus(eventId, MaintenanceStatus.COMPLETED.name());
        auditService.recordStatusChange(ENTITY, eventId, current.name(),
                                        MaintenanceStatus.COMPLETED.name(), actorUserId, null);

        Vehicle vehicle = loadVehicle(m.getVehicleId());
        releaseVehicleIfFree(vehicle, eventId);
    }

    /**
     * Completed work is part of a vehicle's service history and stays. Only
     * plans that never happened can be removed.
     */
    @Override
    @Transactional
    public void delete(int eventId, int actorUserId) {
        MaintenanceRecord m = loadOrThrow(eventId);
        MaintenanceStatus current = MaintenanceStatus.valueOf(m.getStatus());

        if (current == MaintenanceStatus.COMPLETED) {
            throw new InvalidStatusTransitionException(
                "Completed maintenance is part of the vehicle's service history and cannot be deleted");
        }
        if (current == MaintenanceStatus.IN_PROGRESS) {
            throw new InvalidStatusTransitionException(
                "Work in progress cannot be deleted. Cancel it first.");
        }

        maintenanceDao.delete(eventId);
        auditService.record(ENTITY, eventId, "DELETE", actorUserId, "Scheduled maintenance removed");

        loadVehicleOptional(m.getVehicleId())
            .ifPresent(v -> releaseVehicleIfFree(v, eventId));
    }


    // Helpers

    private boolean coversToday(MaintenanceRecord m, LocalDate today) {
        return !m.getEventDate().isAfter(today) && !m.blockedUntil().isBefore(today);
    }

    private void blockVehicle(Vehicle vehicle, MaintenanceRecord m) {
        if (vehicle.getStatus() == VehicleStatus.UNDER_MAINTENANCE) {
            return;
        }
        if (vehicle.getStatus() == VehicleStatus.RENTED) {
            // Should be impossible (requireNoLiveBookings runs first), but if a
            // car really is out with a customer we must not pretend otherwise.
            throw new InvalidStatusTransitionException(
                "This vehicle is currently out with a customer and cannot be taken into the workshop");
        }
        vehicleDao.updateStatus(vehicle.getVehicleId(), VehicleStatus.UNDER_MAINTENANCE.name());
        auditService.recordStatusChange("VEHICLE", vehicle.getVehicleId(), vehicle.getStatus().name(),
                                        VehicleStatus.UNDER_MAINTENANCE.name(), m.getHandledByStaffId(),
                                        "Taken into the workshop (maintenance #" + m.getEventId() + ")");
        notificationService.safeSendToBranchStaff(vehicle.getBranchId(), "VEHICLE_IN_WORKSHOP",
            vehicle.getModel() + " (" + vehicle.getPlateNumber() + ") is in the workshop.");
    }

    private void releaseVehicleIfFree(Vehicle vehicle, int excludeEventId) {
        if (vehicle.getStatus() != VehicleStatus.UNDER_MAINTENANCE) {
            return;
        }
        LocalDate today = AppClock.today();

        boolean stillInWorkshop = !maintenanceDao
            .findBlockingToday(vehicle.getVehicleId(), today, excludeEventId).isEmpty();
        if (stillInWorkshop) {
            return;
        }
        boolean openSeriousDamage = damageReportDao.findOpenByVehicle(vehicle.getVehicleId()).stream()
            .anyMatch(d -> RentalPolicy.blocksVehicle(d.getDamageSeverity()));
        if (openSeriousDamage) {
            return;
        }

        vehicleDao.updateStatus(vehicle.getVehicleId(), VehicleStatus.AVAILABLE.name());
        auditService.recordStatusChange("VEHICLE", vehicle.getVehicleId(),
                                        VehicleStatus.UNDER_MAINTENANCE.name(),
                                        VehicleStatus.AVAILABLE.name(), null,
                                        "Back on the road after maintenance");
        availabilityService.refreshReservationStatus(vehicle.getVehicleId());
    }

    private Vehicle loadVehicle(int vehicleId) {
        return vehicleDao.findById(vehicleId)
                .orElseThrow(() -> new ResourceNotFoundException("Vehicle not found: " + vehicleId));
    }

    private java.util.Optional<Vehicle> loadVehicleOptional(int vehicleId) {
        return vehicleDao.findById(vehicleId);
    }

    private MaintenanceRecord loadOrThrow(int eventId) {
        return maintenanceDao.findById(eventId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Maintenance record not found: " + eventId));
    }

    //Mapper
    private MaintenanceResponse toResponse(MaintenanceRecord m) {
        MaintenanceResponse r = new MaintenanceResponse();
        r.setEventId(m.getEventId());
        r.setVehicleId(m.getVehicleId());
        r.setEventDate(m.getEventDate());
        r.setExpectedEndDate(m.getExpectedEndDate());
        r.setStatus(m.getStatus());
        r.setDescription(m.getDescription());
        r.setHandledByStaffId(m.getHandledByStaffId());
        r.setRepairType(m.getRepairType());
        r.setCost(m.getCost());
        r.setServiceProvider(m.getServiceProvider());
        r.setNextServiceDate(m.getNextServiceDate());
        return r;
    }
}
