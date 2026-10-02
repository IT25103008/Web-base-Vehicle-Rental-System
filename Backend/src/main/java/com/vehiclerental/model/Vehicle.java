package com.vehiclerental.model;

import com.vehiclerental.enums.VehicleStatus;
import java.math.BigDecimal;
import java.time.Year;

public class Vehicle {

    private int vehicleId;
    private int branchId;
    private String plateNumber;
    private String model;
    private String category;
    private Integer manufactureYear;
    private BigDecimal rentalPricePerDay;
    private Integer passengerCapacity;
    private String fuelType;
    private String imageUrl;
    private VehicleStatus status;
    private int mileage;

    public Vehicle() {
    }

    // Derived attribute (matches VehicleAge in the EER diagram)
    public int getVehicleAge() {
        if (manufactureYear == null) {
            return 0;
        }
        return Year.now().getValue() - manufactureYear;
    }

    public int getVehicleId() { return vehicleId; }
    public void setVehicleId(int vehicleId) { this.vehicleId = vehicleId; }

    public int getBranchId() { return branchId; }
    public void setBranchId(int branchId) { this.branchId = branchId; }

    public String getPlateNumber() { return plateNumber; }
    public void setPlateNumber(String plateNumber) { this.plateNumber = plateNumber; }

    public String getModel() { return model; }
    public void setModel(String model) { this.model = model; }

    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }

    public Integer getManufactureYear() { return manufactureYear; }
    public void setManufactureYear(Integer manufactureYear) { this.manufactureYear = manufactureYear; }

    public BigDecimal getRentalPricePerDay() { return rentalPricePerDay; }
    public void setRentalPricePerDay(BigDecimal rentalPricePerDay) { this.rentalPricePerDay = rentalPricePerDay; }

    public Integer getPassengerCapacity() { return passengerCapacity; }
    public void setPassengerCapacity(Integer passengerCapacity) { this.passengerCapacity = passengerCapacity; }

    public String getFuelType() { return fuelType; }
    public void setFuelType(String fuelType) { this.fuelType = fuelType; }

    public String getImageUrl() { return imageUrl; }
    public void setImageUrl(String imageUrl) { this.imageUrl = imageUrl; }

    public VehicleStatus getStatus() { return status; }
    public void setStatus(VehicleStatus status) { this.status = status; }

    public int getMileage() { return mileage; }
    public void setMileage(int mileage) { this.mileage = mileage; }
}
