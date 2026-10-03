package com.vehiclerental.dto.request;

import com.vehiclerental.validation.Rules;
import jakarta.validation.constraints.FutureOrPresent;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

public class TransferRequest {

    @NotNull(message = "Choose a vehicle")
    @Positive(message = "Choose a vehicle")
    private Integer vehicleId;

    @NotNull(message = "Choose the destination branch")
    @Positive(message = "Choose the destination branch")
    private Integer toBranchId;

    @NotNull(message = "Transfer date is required")
    @FutureOrPresent(message = "Transfer date cannot be in the past")
    private LocalDate transferDate;

    @Size(max = Rules.REASON, message = "Reason can be at most " + Rules.REASON + " characters")
    private String reason;

    public Integer getVehicleId() { return vehicleId; }
    public void setVehicleId(Integer vehicleId) { this.vehicleId = vehicleId; }
    public Integer getToBranchId() { return toBranchId; }
    public void setToBranchId(Integer toBranchId) { this.toBranchId = toBranchId; }
    public LocalDate getTransferDate() { return transferDate; }
    public void setTransferDate(LocalDate transferDate) { this.transferDate = transferDate; }
    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = Rules.clean(reason); }
}
