package com.vehiclerental.dao;

import com.vehiclerental.model.DamageReport;

import java.util.List;
import java.util.Optional;

public interface DamageReportDao {
    DamageReport save(DamageReport report);
    Optional<DamageReport> findById(int eventId);
    List<DamageReport> findAll();
    List<DamageReport> findByVehicle(int vehicleId);
    List<DamageReport> findByStatus(String status);
    /** Unresolved reports for a vehicle. */
    List<DamageReport> findOpenByVehicle(int vehicleId);
    List<DamageReport> findByHandover(int handoverId);
    void updateStatus(int eventId, String newStatus);
    void delete(int eventId);
}
