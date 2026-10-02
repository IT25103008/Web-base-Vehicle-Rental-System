package com.vehiclerental.dto.request;

import com.vehiclerental.enums.VehicleStatus;
import com.vehiclerental.model.Vehicle;
import com.vehiclerental.validation.Rules;
import jakarta.validation.constraints.*;

import java.math.BigDecimal;

/**
 * Add or edit a vehicle. Used to be the Vehicle model itself, which meant
 * nothing was checked until the service - and a too-long model name reached
 * the database. The category list, the model-year window and the plate being
 * unique are still the service's job; they need the clock or the database.
 *
 * On an edit, a missing branchId or mileage means "leave it as it is", which
 * is how VehicleServiceImpl.update already reads a 0.
 */
public class VehicleRequest {

    @Positive(message = "Choose a home branch")
    private Integer branchId;

    @NotBlank(message = "Plate number is required")
    @Size(max = Rules.PLATE, message = "Plate number can be at most " + Rules.PLATE + " characters")
    @Pattern(regexp = Rules.PLATE_PATTERN, message = Rules.PLATE_MSG)
    private String plateNumber;

    @NotBlank(message = "Make and model are required")
    @Size(max = Rules.MODEL, message = "Make and model can be at most " + Rules.MODEL + " characters")
    private String model;

    @NotBlank(message = "Category is required")
    @Size(max = 40, message = "Category is too long")
    private String category;

    @Min(value = 1950, message = "Model year must be 1950 or later")
    @Max(value = 2100, message = "Model year looks wrong")
    private Integer manufactureYear;

    @NotNull(message = "Daily rate is required")
    @DecimalMin(value = "0.01", message = "Daily rate must be more than zero")
    @DecimalMax(value = Rules.MONEY_MAX, message = "Daily rate is too large")
    private BigDecimal rentalPricePerDay;

    @Min(value = 1, message = "Seats must be at least 1")
    @Max(value = 60, message = "Seats can be at most 60")
    private Integer passengerCapacity;

    @Size(max = Rules.FUEL_TYPE, message = "Fuel type is too long")
    private String fuelType;

    @Size(max = 255, message = "Photo address is too long")
    private String imageUrl;

    private VehicleStatus status;

    @Min(value = 0, message = "Odometer reading cannot be negative")
    @Max(value = Rules.MILEAGE_MAX, message = "Odometer reading looks too large")
    private Integer mileage;

    /** The model the services work with. Missing numbers become 0 ("unchanged" on an edit). */
    public Vehicle toModel() {
        Vehicle v = new Vehicle();
        v.setBranchId(branchId == null ? 0 : branchId);
        v.setPlateNumber(plateNumber);
        v.setModel(model);
        v.setCategory(category);
        v.setManufactureYear(manufactureYear);
        v.setRentalPricePerDay(rentalPricePerDay);
        v.setPassengerCapacity(passengerCapacity);
        v.setFuelType(fuelType);
        v.setImageUrl(imageUrl);
        v.setStatus(status);
        v.setMileage(mileage == null ? 0 : mileage);
        return v;
    }

    public Integer getBranchId() { return branchId; }
    public void setBranchId(Integer branchId) { this.branchId = branchId; }
    public String getPlateNumber() { return plateNumber; }
    public void setPlateNumber(String plateNumber) {
        String p = Rules.upper(plateNumber);
        this.plateNumber = p == null ? null : p.replaceAll("\\s+", " ");
    }
    public String getModel() { return model; }
    public void setModel(String model) { this.model = Rules.name(model); }
    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = Rules.upper(category); }
    public Integer getManufactureYear() { return manufactureYear; }
    public void setManufactureYear(Integer manufactureYear) { this.manufactureYear = manufactureYear; }
    public BigDecimal getRentalPricePerDay() { return rentalPricePerDay; }
    public void setRentalPricePerDay(BigDecimal rentalPricePerDay) { this.rentalPricePerDay = rentalPricePerDay; }
    public Integer getPassengerCapacity() { return passengerCapacity; }
    public void setPassengerCapacity(Integer passengerCapacity) { this.passengerCapacity = passengerCapacity; }
    public String getFuelType() { return fuelType; }
    public void setFuelType(String fuelType) { this.fuelType = Rules.clean(fuelType); }
    public String getImageUrl() { return imageUrl; }
    public void setImageUrl(String imageUrl) { this.imageUrl = Rules.clean(imageUrl); }
    public VehicleStatus getStatus() { return status; }
    public void setStatus(VehicleStatus status) { this.status = status; }
    public Integer getMileage() { return mileage; }
    public void setMileage(Integer mileage) { this.mileage = mileage; }
}
