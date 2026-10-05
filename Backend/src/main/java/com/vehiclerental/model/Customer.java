package com.vehiclerental.model;

import com.vehiclerental.enums.Role;
import java.time.LocalDate;

public class Customer extends User {

    private String drivingLicenseNumber;
    private boolean licenseVerified;
    private LocalDate licenseExpiryDate;

    public Customer() {
        setRole(Role.CUSTOMER);
    }

    // Override: a customer can only book if their license has been verified.
    // (polymorphism — overriding)
    @Override
    public boolean canBookVehicle() {
        return licenseVerified;
    }

    public String getDrivingLicenseNumber() { return drivingLicenseNumber; }
    public void setDrivingLicenseNumber(String drivingLicenseNumber) { this.drivingLicenseNumber = drivingLicenseNumber; }

    public boolean isLicenseVerified() { return licenseVerified; }
    public void setLicenseVerified(boolean licenseVerified) { this.licenseVerified = licenseVerified; }

    public LocalDate getLicenseExpiryDate() { return licenseExpiryDate; }
    public void setLicenseExpiryDate(LocalDate licenseExpiryDate) { this.licenseExpiryDate = licenseExpiryDate; }
}
