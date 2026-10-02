package com.vehiclerental.dao;

import com.vehiclerental.model.VehicleImage;

import java.util.List;
import java.util.Optional;

/** The extra photographs of a vehicle (the cover photo stays on the vehicle row). */
public interface VehicleImageDao {

    record Entry(int imageId, int vehicleId, String contentType, int sortOrder) { }

    List<Entry> findByVehicle(int vehicleId);
    Optional<VehicleImage> findData(int vehicleId, int imageId);
    int add(int vehicleId, byte[] data, String contentType);
    void delete(int vehicleId, int imageId);
    int countByVehicle(int vehicleId);
}
