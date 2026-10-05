package com.vehiclerental.dto.response;

import java.time.LocalDate;
import java.time.LocalDateTime;

public class UserResponse {

    private int userId;
    private String email;
    private String fullName;
    private String role;
    private boolean active;

    // Customer-only (null for staff and administrators)
    private Boolean licenseVerified;
    private String drivingLicenseNumber;
    private LocalDate licenseExpiryDate;

    // Staff-only
    private Integer branchId;
    private String employeeCode;
    private String position;

    public UserResponse() {}

    public UserResponse(int userId, String email, String fullName, String role) {
        this.userId = userId;
        this.email = email;
        this.fullName = fullName;
        this.role = role;
        this.active = true;
    }

    public int getUserId() { return userId; }
    public void setUserId(int userId) { this.userId = userId; }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    public String getFullName() { return fullName; }
    public void setFullName(String fullName) { this.fullName = fullName; }

    public String getRole() { return role; }
    public void setRole(String role) { this.role = role; }

    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }

    public Boolean getLicenseVerified() { return licenseVerified; }
    public void setLicenseVerified(Boolean licenseVerified) { this.licenseVerified = licenseVerified; }

    public String getDrivingLicenseNumber() { return drivingLicenseNumber; }
    public void setDrivingLicenseNumber(String drivingLicenseNumber) { this.drivingLicenseNumber = drivingLicenseNumber; }

    public LocalDate getLicenseExpiryDate() { return licenseExpiryDate; }
    public void setLicenseExpiryDate(LocalDate licenseExpiryDate) { this.licenseExpiryDate = licenseExpiryDate; }

    public Integer getBranchId() { return branchId; }
    public void setBranchId(Integer branchId) { this.branchId = branchId; }

    public String getEmployeeCode() { return employeeCode; }
    public void setEmployeeCode(String employeeCode) { this.employeeCode = employeeCode; }

    public String getPosition() { return position; }
    public void setPosition(String position) { this.position = position; }

    // Personal details: filled in for the person themselves (GET /api/auth/me,
    // /api/users/me) and for a single staff lookup; left null in lists.
    private String firstName;
    private String lastName;
    private String phoneNumber;
    private String address;
    private LocalDate dateOfBirth;
    private LocalDateTime registrationDate;
    private boolean emailVerified = true;
    private boolean totpEnabled;
    private LocalDateTime privacyConsentAt;

    public String getFirstName() { return firstName; }
    public void setFirstName(String firstName) { this.firstName = firstName; }

    public String getLastName() { return lastName; }
    public void setLastName(String lastName) { this.lastName = lastName; }

    public String getPhoneNumber() { return phoneNumber; }
    public void setPhoneNumber(String phoneNumber) { this.phoneNumber = phoneNumber; }

    public String getAddress() { return address; }
    public void setAddress(String address) { this.address = address; }

    public LocalDate getDateOfBirth() { return dateOfBirth; }
    public void setDateOfBirth(LocalDate dateOfBirth) { this.dateOfBirth = dateOfBirth; }

    public LocalDateTime getRegistrationDate() { return registrationDate; }
    public void setRegistrationDate(LocalDateTime registrationDate) { this.registrationDate = registrationDate; }

    public boolean isEmailVerified() { return emailVerified; }
    public void setEmailVerified(boolean emailVerified) { this.emailVerified = emailVerified; }

    public boolean isTotpEnabled() { return totpEnabled; }
    public void setTotpEnabled(boolean totpEnabled) { this.totpEnabled = totpEnabled; }

    public LocalDateTime getPrivacyConsentAt() { return privacyConsentAt; }
    public void setPrivacyConsentAt(LocalDateTime privacyConsentAt) { this.privacyConsentAt = privacyConsentAt; }
}
