package com.vehiclerental.dao;

import com.vehiclerental.model.Vehicle;
import com.vehiclerental.model.VehicleImage;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface VehicleDao {
    /** SELECT ... FOR UPDATE: serialises booking changes for one vehicle (see BookingServiceImpl). */
    void lockForUpdate(int vehicleId);
    /** Every vehicle a customer could book at some point (the public catalogue). */
    List<Vehicle> findCatalogue(LocalDate today);

    Vehicle save(Vehicle vehicle);
    Optional<Vehicle> findById(int vehicleId);
    Optional<Vehicle> findByPlate(String plateNumber);
    List<Vehicle> findAll();
    List<Vehicle> findByBranch(int branchId);
    List<Vehicle> findByStatus(String status);

    /**
     * Vehicles that are AVAILABLE and don't have any overlapping booking
     * for the given date range at the given branch.
     *
     * Overloaded (polymorphism - overloading):
     *   - two args = across all branches
     *   - three args = at one specific branch
     */
    List<Vehicle> findAvailable(LocalDate pickup, LocalDate returnDate);
    List<Vehicle> findAvailable(LocalDate pickup, LocalDate returnDate, Integer branchId);

    void update(Vehicle vehicle);
    void updateStatus(int vehicleId, String status);

    /**
     * Narrow update so setting a photo never re-validates the whole record.
     * Pass nulls to clear the photo.
     */
    void updateImage(int vehicleId, byte[] data, String contentType, String imageUrl);

    /** The stored photo bytes. Only ever called when serving the image itself. */
    Optional<VehicleImage> findImage(int vehicleId);
    void updateBranch(int vehicleId, int branchId);
    void delete(int vehicleId);
}
