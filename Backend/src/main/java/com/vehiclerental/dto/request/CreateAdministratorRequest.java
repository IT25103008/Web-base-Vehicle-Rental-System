package com.vehiclerental.dto.request;

import com.vehiclerental.validation.Rules;
import jakarta.validation.constraints.*;

import java.time.LocalDate;

public class CreateAdministratorRequest {

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

    @NotBlank(message = "Admin code is required")
    @Pattern(regexp = Rules.CODE_PATTERN, message = "Admin code: " + Rules.CODE_MSG)
    private String adminCode;

    @Pattern(regexp = "^(STANDARD|SENIOR)$", message = "Level must be STANDARD or SENIOR")
    private String adminLevel;              // SENIOR / STANDARD

    @PastOrPresent(message = "Appointment date cannot be in the future")
    private LocalDate dateOfAppointment;

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

    public String getAdminCode() { return adminCode; }
    public void setAdminCode(String adminCode) { this.adminCode = Rules.clean(adminCode); }

    public String getAdminLevel() { return adminLevel; }
    public void setAdminLevel(String adminLevel) { this.adminLevel = Rules.upper(adminLevel); }

    public LocalDate getDateOfAppointment() { return dateOfAppointment; }
    public void setDateOfAppointment(LocalDate dateOfAppointment) { this.dateOfAppointment = dateOfAppointment; }
}
