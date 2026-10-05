package com.vehiclerental.dto.request;

import com.vehiclerental.model.InsurancePolicy;
import com.vehiclerental.validation.Rules;
import jakarta.validation.constraints.*;

import java.time.LocalDate;

/**
 * Add or edit an insurance policy. Overlapping cover and cover that a live
 * booking relies on are checked in InsurancePolicyServiceImpl.
 */
public class InsurancePolicyRequest {

    @NotNull(message = "Choose a vehicle")
    @Positive(message = "Choose a vehicle")
    private Integer vehicleId;

    @NotBlank(message = "Policy number is required")
    @Pattern(regexp = Rules.POLICY_PATTERN, message = "Policy number: " + Rules.POLICY_MSG)
    private String policyNumber;

    @NotBlank(message = "Provider is required")
    @Size(max = Rules.PROVIDER, message = "Provider can be at most " + Rules.PROVIDER + " characters")
    private String provider;

    @NotNull(message = "Start date is required")
    private LocalDate startDate;

    @NotNull(message = "Expiry date is required")
    private LocalDate expiryDate;

    @Pattern(regexp = "^(ACTIVE|EXPIRED|CANCELLED)$", message = "Status must be ACTIVE, EXPIRED or CANCELLED")
    private String status;

    /** Reported against "expiryDate", next to the box that needs changing. */
    @AssertTrue(message = "Expiry date must be after the start date")
    public boolean isExpiryAfterStart() {
        return startDate == null || expiryDate == null || expiryDate.isAfter(startDate);
    }

    public InsurancePolicy toModel() {
        InsurancePolicy p = new InsurancePolicy();
        p.setVehicleId(vehicleId == null ? 0 : vehicleId);
        p.setPolicyNumber(policyNumber);
        p.setProvider(provider);
        p.setStartDate(startDate);
        p.setExpiryDate(expiryDate);
        p.setStatus(status);
        return p;
    }

    public Integer getVehicleId() { return vehicleId; }
    public void setVehicleId(Integer vehicleId) { this.vehicleId = vehicleId; }
    public String getPolicyNumber() { return policyNumber; }
    public void setPolicyNumber(String policyNumber) { this.policyNumber = Rules.upper(policyNumber); }
    public String getProvider() { return provider; }
    public void setProvider(String provider) { this.provider = Rules.clean(provider); }
    public LocalDate getStartDate() { return startDate; }
    public void setStartDate(LocalDate startDate) { this.startDate = startDate; }
    public LocalDate getExpiryDate() { return expiryDate; }
    public void setExpiryDate(LocalDate expiryDate) { this.expiryDate = expiryDate; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = Rules.upper(status); }
}
