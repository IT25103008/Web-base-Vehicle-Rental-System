package com.vehiclerental.dto.request;

import com.vehiclerental.validation.Rules;
import jakarta.validation.constraints.*;

import java.time.LocalDate;

public class RegisterCustomerRequest {

    @NotBlank(message = "Email is required")
    @Email(message = "Enter a valid email address")
    @Size(max = Rules.EMAIL, message = "Email can be at most " + Rules.EMAIL + " characters")
    private String email;

    @NotBlank(message = "Password is required")
    @Size(min = Rules.PASSWORD_MIN, max = Rules.PASSWORD_MAX,
          message = "Password must be " + Rules.PASSWORD_MIN + " to " + Rules.PASSWORD_MAX + " characters")
    private String password;

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

    @NotNull(message = "Date of birth is required")
    @Past(message = "Date of birth must be in the past")
    private LocalDate dateOfBirth;

    @NotBlank(message = "Driving licence number is required")
    @Size(max = Rules.LICENCE, message = "Licence number can be at most " + Rules.LICENCE + " characters")
    @Pattern(regexp = Rules.LICENCE_PATTERN, message = Rules.LICENCE_MSG)
    private String drivingLicenseNumber;

    @NotNull(message = "Driving licence expiry date is required")
    @Future(message = "Your driving licence has already expired")
    private LocalDate licenseExpiryDate;

    /** The privacy notice was shown and accepted (stored with a timestamp). */
    private boolean privacyConsent;

    public boolean isPrivacyConsent() { return privacyConsent; }
    public void setPrivacyConsent(boolean privacyConsent) { this.privacyConsent = privacyConsent; }

    // Setters tidy the input (trim, collapse spaces, upper-case codes) before validation runs.
    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = Rules.clean(email); }
    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }
    public String getFirstName() { return firstName; }
    public void setFirstName(String firstName) { this.firstName = Rules.name(firstName); }
    public String getLastName() { return lastName; }
    public void setLastName(String lastName) { this.lastName = Rules.name(lastName); }
    public String getPhoneNumber() { return phoneNumber; }
    public void setPhoneNumber(String phoneNumber) { this.phoneNumber = Rules.clean(phoneNumber); }
    public String getAddress() { return address; }
    public void setAddress(String address) { this.address = Rules.clean(address); }
    public LocalDate getDateOfBirth() { return dateOfBirth; }
    public void setDateOfBirth(LocalDate dateOfBirth) { this.dateOfBirth = dateOfBirth; }
    public String getDrivingLicenseNumber() { return drivingLicenseNumber; }
    public void setDrivingLicenseNumber(String s) { this.drivingLicenseNumber = Rules.upper(s); }
    public LocalDate getLicenseExpiryDate() { return licenseExpiryDate; }
    public void setLicenseExpiryDate(LocalDate d) { this.licenseExpiryDate = d; }
}
