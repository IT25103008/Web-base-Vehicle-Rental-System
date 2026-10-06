package com.vehiclerental.model;

import com.vehiclerental.enums.TransferStatus;

import java.time.LocalDate;

public class VehicleTransfer {

    private int transferId;
    private int vehicleId;
    private int fromBranchId;
    private int toBranchId;
    private LocalDate transferDate;
    private TransferStatus status;
    private String reason;

    public VehicleTransfer() {
    }

    public int getTransferId() { return transferId; }
    public void setTransferId(int transferId) { this.transferId = transferId; }

    public int getVehicleId() { return vehicleId; }
    public void setVehicleId(int vehicleId) { this.vehicleId = vehicleId; }

    public int getFromBranchId() { return fromBranchId; }
    public void setFromBranchId(int fromBranchId) { this.fromBranchId = fromBranchId; }

    public int getToBranchId() { return toBranchId; }
    public void setToBranchId(int toBranchId) { this.toBranchId = toBranchId; }

    public LocalDate getTransferDate() { return transferDate; }
    public void setTransferDate(LocalDate transferDate) { this.transferDate = transferDate; }

    public TransferStatus getStatus() { return status; }
    public void setStatus(TransferStatus status) { this.status = status; }

    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }
}
