package com.vehiclerental.dto.request;

import com.vehiclerental.validation.Rules;
import jakarta.validation.constraints.FutureOrPresent;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;

/**
 * The rest of the booking rules (return after pick-up, 30-day maximum,
 * 180 days ahead, clashes) need other data and live in BookingServiceImpl.
 */
public class CreateBookingRequest {

    @NotNull(message = "Choose a vehicle")
    @Positive(message = "Choose a vehicle")
    private Integer vehicleId;

    @NotNull(message = "Choose a pick-up branch")
    @Positive(message = "Choose a pick-up branch")
    private Integer pickupBranchId;

    @NotNull(message = "Pick-up date is required")
    @FutureOrPresent(message = "Pick-up date cannot be in the past")
    private LocalDate pickupDate;

    @NotNull(message = "Return date is required")
    @FutureOrPresent(message = "Return date cannot be in the past")
    private LocalDate returnDate;

    @Size(max = Rules.NOTES, message = "Special requests can be at most " + Rules.NOTES + " characters")
    private String specialRequests;

    public Integer getVehicleId() { return vehicleId; }
    public void setVehicleId(Integer vehicleId) { this.vehicleId = vehicleId; }
    public Integer getPickupBranchId() { return pickupBranchId; }
    public void setPickupBranchId(Integer pickupBranchId) { this.pickupBranchId = pickupBranchId; }
    public LocalDate getPickupDate() { return pickupDate; }
    public void setPickupDate(LocalDate pickupDate) { this.pickupDate = pickupDate; }
    public LocalDate getReturnDate() { return returnDate; }
    public void setReturnDate(LocalDate returnDate) { this.returnDate = returnDate; }
    public String getSpecialRequests() { return specialRequests; }
    public void setSpecialRequests(String specialRequests) { this.specialRequests = Rules.clean(specialRequests); }
}
