package com.vehiclerental.model;

import com.vehiclerental.enums.Role;
import java.time.LocalDate;

public class Administrator extends User {

    private String adminCode;
    private LocalDate dateOfAppointment;
    private String adminLevel;

    public Administrator() {
        setRole(Role.ADMINISTRATOR);
    }

    public String getAdminCode() { return adminCode; }
    public void setAdminCode(String adminCode) { this.adminCode = adminCode; }

    public LocalDate getDateOfAppointment() { return dateOfAppointment; }
    public void setDateOfAppointment(LocalDate dateOfAppointment) { this.dateOfAppointment = dateOfAppointment; }

    public String getAdminLevel() { return adminLevel; }
    public void setAdminLevel(String adminLevel) { this.adminLevel = adminLevel; }
}
