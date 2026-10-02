package com.vehiclerental.service;

import com.vehiclerental.enums.VehicleStatus;
import com.vehiclerental.model.Vehicle;

import java.time.LocalDate;
import java.util.List;

public interface VehicleService {

    Vehicle create(Vehicle vehicle, int actorUserId);

    Vehicle findById(int vehicleId);

    List<Vehicle> findAll(Integer branchId, VehicleStatus status);

    /** Vehicles that can actually be rented for the whole of [pickup, returnDate]. */
    List<Vehicle> findAvailable(LocalDate pickup, LocalDate returnDate, Integer branchId);

    Vehicle update(int vehicleId, Vehicle updates, int actorUserId);

    /** Manual status change by staff. Refuses anything that would contradict a live booking. */
    void changeStatus(int vehicleId, VehicleStatus newStatus, int actorUserId, String reason);

    /** Permanent delete. Refused once the vehicle has any history — deactivate instead. */
    void delete(int vehicleId, int actorUserId);
}
