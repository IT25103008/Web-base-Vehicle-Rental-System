package com.vehiclerental.dto.request;

import com.vehiclerental.validation.Rules;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;

public class UpdateProfileRequest {

    @NotBlank(message = "First name is required")
    @Size(max = Rules.NAME, message = "First name can be at most " + Rules.NAME + " characters")
    @Pattern(regexp = Rules.NAME_PATTERN, message = Rules.NAME_MSG)
    private String firstName;

    @NotBlank(message = "Last name is required")
    @Size(max = Rules.NAME, message = "Last name can be at most " + Rules.NAME + " characters")
    @Pattern(regexp = Rules.NAME_PATTERN, message = Rules.NAME_MSG)
    private String lastName;

    @Size(max = Rules.PHONE, message = "Phone number can be at most " + Rules.PHONE + " characters")
    @Pattern(regexp = Rules.PHONE_PATTERN, message = Rules.PHONE_MSG)
    private String phoneNumber;

    @Size(max = Rules.ADDRESS, message = "Address can be at most " + Rules.ADDRESS + " characters")
    private String address;

    // Null leaves the licence as it is; the service rejects a blank one.
    @Size(max = Rules.LICENCE, message = "Licence number can be at most " + Rules.LICENCE + " characters")
    @Pattern(regexp = Rules.LICENCE_PATTERN, message = Rules.LICENCE_MSG)
    private String drivingLicenseNumber;

    private LocalDate licenseExpiryDate;

    public String getFirstName() { return firstName; }
    public void setFirstName(String firstName) { this.firstName = Rules.name(firstName); }
    public String getLastName() { return lastName; }
    public void setLastName(String lastName) { this.lastName = Rules.name(lastName); }
    public String getPhoneNumber() { return phoneNumber; }
    public void setPhoneNumber(String phoneNumber) { this.phoneNumber = Rules.clean(phoneNumber); }
    public String getAddress() { return address; }
    public void setAddress(String address) { this.address = Rules.clean(address); }
    public String getDrivingLicenseNumber() { return drivingLicenseNumber; }
    public void setDrivingLicenseNumber(String s) {
        // Trimmed and upper-cased, but a blank stays blank (not null), so the
        // service still sees "the customer cleared it" rather than "unchanged".
        this.drivingLicenseNumber = s == null ? null : s.trim().toUpperCase(java.util.Locale.ROOT);
    }
    public LocalDate getLicenseExpiryDate() { return licenseExpiryDate; }
    public void setLicenseExpiryDate(LocalDate licenseExpiryDate) { this.licenseExpiryDate = licenseExpiryDate; }
}
