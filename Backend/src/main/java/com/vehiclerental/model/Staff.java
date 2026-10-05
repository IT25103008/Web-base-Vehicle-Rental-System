package com.vehiclerental.model;

import com.vehiclerental.enums.Role;
import java.time.LocalDate;

public class Staff extends User {

    private String employeeCode;
    private LocalDate hireDate;
    private String position;
    private Integer branchId;         // Integer (nullable) — a new staff row may not yet be assigned
    private Integer supervisorId;     // recursive: staff supervises staff

    public Staff() {
        setRole(Role.STAFF);
    }

    public String getEmployeeCode() { return employeeCode; }
    public void setEmployeeCode(String employeeCode) { this.employeeCode = employeeCode; }

    public LocalDate getHireDate() { return hireDate; }
    public void setHireDate(LocalDate hireDate) { this.hireDate = hireDate; }

    public String getPosition() { return position; }
    public void setPosition(String position) { this.position = position; }

    public Integer getBranchId() { return branchId; }
    public void setBranchId(Integer branchId) { this.branchId = branchId; }

    public Integer getSupervisorId() { return supervisorId; }
    public void setSupervisorId(Integer supervisorId) { this.supervisorId = supervisorId; }
}
