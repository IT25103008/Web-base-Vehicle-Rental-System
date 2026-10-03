package com.vehiclerental.model;

import java.time.LocalTime;

public class Branch {

    private int branchId;
    private String name;
    private String street;
    private String city;
    private String district;
    private String contactNumber;
    private LocalTime openTime;
    private LocalTime closeTime;
    private String status;    // ACTIVE / INACTIVE

    public Branch() {
    }

    public Branch(String name, String street, String city, String district,
                  String contactNumber, LocalTime openTime, LocalTime closeTime) {
        this.name = name;
        this.street = street;
        this.city = city;
        this.district = district;
        this.contactNumber = contactNumber;
        this.openTime = openTime;
        this.closeTime = closeTime;
        this.status = "ACTIVE";
    }

    public int getBranchId() { return branchId; }
    public void setBranchId(int branchId) { this.branchId = branchId; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getStreet() { return street; }
    public void setStreet(String street) { this.street = street; }

    public String getCity() { return city; }
    public void setCity(String city) { this.city = city; }

    public String getDistrict() { return district; }
    public void setDistrict(String district) { this.district = district; }

    public String getContactNumber() { return contactNumber; }
    public void setContactNumber(String contactNumber) { this.contactNumber = contactNumber; }

    public LocalTime getOpenTime() { return openTime; }
    public void setOpenTime(LocalTime openTime) { this.openTime = openTime; }

    public LocalTime getCloseTime() { return closeTime; }
    public void setCloseTime(LocalTime closeTime) { this.closeTime = closeTime; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
}
