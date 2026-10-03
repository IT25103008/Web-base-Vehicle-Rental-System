package com.vehiclerental.dto.request;

import com.vehiclerental.model.Branch;
import com.vehiclerental.validation.Rules;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.LocalTime;

/** Open or edit a branch. Used to be the Branch model, checked only for a name. */
public class BranchRequest {

    @NotBlank(message = "Branch name is required")
    @Size(max = Rules.BRANCH_NAME, message = "Branch name can be at most " + Rules.BRANCH_NAME + " characters")
    private String name;

    @Size(max = Rules.STREET, message = "Street can be at most " + Rules.STREET + " characters")
    private String street;

    @Size(max = Rules.CITY, message = "City can be at most " + Rules.CITY + " characters")
    private String city;

    private String district;

    @Size(max = Rules.PHONE, message = "Contact number can be at most " + Rules.PHONE + " characters")
    @Pattern(regexp = Rules.PHONE_PATTERN, message = Rules.PHONE_MSG)
    private String contactNumber;

    private LocalTime openTime;
    private LocalTime closeTime;

    @Pattern(regexp = "^(ACTIVE|INACTIVE)$", message = "Status must be ACTIVE or INACTIVE")
    private String status;

    /** Reported against "district" (see GlobalExceptionHandler), so the form can point at the right box. */
    @AssertTrue(message = "Choose one of Sri Lanka's 25 districts")
    public boolean isDistrictValid() {
        return district == null || Rules.DISTRICTS.contains(district);
    }

    @AssertTrue(message = "Closing time must be after opening time")
    public boolean isHoursValid() {
        return openTime == null || closeTime == null || closeTime.isAfter(openTime);
    }

    public Branch toModel() {
        Branch b = new Branch();
        b.setName(name);
        b.setStreet(street);
        b.setCity(city);
        b.setDistrict(district);
        b.setContactNumber(contactNumber);
        b.setOpenTime(openTime);
        b.setCloseTime(closeTime);
        b.setStatus(status);
        return b;
    }

    public String getName() { return name; }
    public void setName(String name) { this.name = Rules.name(name); }
    public String getStreet() { return street; }
    public void setStreet(String street) { this.street = Rules.clean(street); }
    public String getCity() { return city; }
    public void setCity(String city) { this.city = Rules.name(city); }
    public String getDistrict() { return district; }
    public void setDistrict(String district) {
        // Match the list whatever the case the user typed it in.
        String d = Rules.name(district);
        this.district = d == null ? null : Rules.DISTRICTS.stream()
            .filter(x -> x.equalsIgnoreCase(d)).findFirst().orElse(d);
    }
    public String getContactNumber() { return contactNumber; }
    public void setContactNumber(String contactNumber) { this.contactNumber = Rules.clean(contactNumber); }
    public LocalTime getOpenTime() { return openTime; }
    public void setOpenTime(LocalTime openTime) { this.openTime = openTime; }
    public LocalTime getCloseTime() { return closeTime; }
    public void setCloseTime(LocalTime closeTime) { this.closeTime = closeTime; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = Rules.upper(status); }
}
