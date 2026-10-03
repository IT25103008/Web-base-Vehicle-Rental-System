package com.vehiclerental.dao;

import com.vehiclerental.model.VehicleTransfer;

import java.util.List;
import java.util.Optional;

public interface VehicleTransferDao {

    VehicleTransfer save(VehicleTransfer t);
    Optional<VehicleTransfer> findById(int transferId);
    List<VehicleTransfer> findAll();
    List<VehicleTransfer> findPending();
    List<VehicleTransfer> findPendingByVehicle(int vehicleId);
    void updateStatus(int transferId, String status);
}
