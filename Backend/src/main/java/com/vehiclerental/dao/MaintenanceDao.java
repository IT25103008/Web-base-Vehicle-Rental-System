package com.vehiclerental.dao;

import com.vehiclerental.model.MaintenanceRecord;

import java.util.List;
import java.util.Optional;

public interface MaintenanceDao {
    MaintenanceRecord save(MaintenanceRecord record);
    Optional<MaintenanceRecord> findById(int eventId);
    List<MaintenanceRecord> findAll();
    List<MaintenanceRecord> findByVehicle(int vehicleId);
    List<MaintenanceRecord> findUpcoming(int daysAhead);

    /** Scheduled or in-progress work whose workshop window overlaps [from, to]. */
    List<MaintenanceRecord> findBlocking(int vehicleId, java.time.LocalDate from, java.time.LocalDate to,
                                         Integer excludeEventId);

    /** Scheduled or in-progress work that covers today — the vehicle is in the workshop now. */
    List<MaintenanceRecord> findBlockingToday(int vehicleId, java.time.LocalDate today, Integer excludeEventId);

    /** Work that should have started by now but is still SCHEDULED. */
    List<MaintenanceRecord> findDueToStart(java.time.LocalDate asOf);
    void updateStatus(int eventId, String newStatus);

    /** Rewrites the editable fields of a record; its vehicle, staff member and status are untouched. */
    void update(MaintenanceRecord record);
    void delete(int eventId);
}
