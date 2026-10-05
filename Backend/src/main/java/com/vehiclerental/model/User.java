package com.vehiclerental.model;

import com.vehiclerental.enums.Role;
import java.time.LocalDate;
import java.time.LocalDateTime;

// Abstract base for every user in the system.
// Cannot be instantiated directly — you always create a Customer, Staff,
// or Administrator (see the three subclasses).
public abstract class User {

    private int userId;
    private String email;
    private String passwordHash;
    private String firstName;
    private String lastName;
    private String phoneNumber;
    private String address;
    private Role role;                       // CUSTOMER / STAFF / ADMINISTRATOR
    private boolean active = true;           // a disabled account cannot sign in
    private LocalDate dateOfBirth;
    private LocalDateTime registrationDate;
    private boolean emailVerified = true;    // new self-registrations start false
    private LocalDateTime privacyConsentAt;  // when the privacy notice was accepted
    private String totpSecret;               // two-factor (staff and administrators)
    private boolean totpEnabled;
    private LocalDateTime anonymisedAt;      // set when the person asked to be erased

    // -- constructors --
    protected User() {
    }

    protected User(int userId, String email, String passwordHash,
                   String firstName, String lastName, Role role) {
        this.userId = userId;
        this.email = email;
        this.passwordHash = passwordHash;
        this.firstName = firstName;
        this.lastName = lastName;
        this.role = role;
    }

    // -- shared behaviour (subclasses may override) --
    public String getFullName() {
        return firstName + " " + lastName;
    }

    // Overridden in Customer to also check license_verified.
    public boolean canBookVehicle() {
        return false;
    }

    // -- getters and setters --
    public int getUserId() { return userId; }
    public void setUserId(int userId) { this.userId = userId; }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    public String getPasswordHash() { return passwordHash; }
    public void setPasswordHash(String passwordHash) { this.passwordHash = passwordHash; }

    public String getFirstName() { return firstName; }
    public void setFirstName(String firstName) { this.firstName = firstName; }

    public String getLastName() { return lastName; }
    public void setLastName(String lastName) { this.lastName = lastName; }

    public String getPhoneNumber() { return phoneNumber; }
    public void setPhoneNumber(String phoneNumber) { this.phoneNumber = phoneNumber; }

    public String getAddress() { return address; }
    public void setAddress(String address) { this.address = address; }

    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }

    public Role getRole() { return role; }
    public void setRole(Role role) { this.role = role; }

    public LocalDate getDateOfBirth() { return dateOfBirth; }
    public void setDateOfBirth(LocalDate dateOfBirth) { this.dateOfBirth = dateOfBirth; }

    public LocalDateTime getRegistrationDate() { return registrationDate; }
    public void setRegistrationDate(LocalDateTime registrationDate) { this.registrationDate = registrationDate; }

    public boolean isEmailVerified() { return emailVerified; }
    public void setEmailVerified(boolean emailVerified) { this.emailVerified = emailVerified; }

    public LocalDateTime getPrivacyConsentAt() { return privacyConsentAt; }
    public void setPrivacyConsentAt(LocalDateTime privacyConsentAt) { this.privacyConsentAt = privacyConsentAt; }

    public String getTotpSecret() { return totpSecret; }
    public void setTotpSecret(String totpSecret) { this.totpSecret = totpSecret; }

    public boolean isTotpEnabled() { return totpEnabled; }
    public void setTotpEnabled(boolean totpEnabled) { this.totpEnabled = totpEnabled; }

    public LocalDateTime getAnonymisedAt() { return anonymisedAt; }
    public void setAnonymisedAt(LocalDateTime anonymisedAt) { this.anonymisedAt = anonymisedAt; }
}
