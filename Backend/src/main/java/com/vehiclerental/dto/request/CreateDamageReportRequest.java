package com.vehiclerental.dto.request;

import com.vehiclerental.validation.Rules;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

public class CreateDamageReportRequest {

    @NotNull(message = "Choose a vehicle")
    @Positive(message = "Choose a vehicle")
    private Integer vehicleId;

    // Optional: set if this damage was found during a specific handover.
    @Positive(message = "Handover number must be a positive number")
    private Integer handoverId;

    @NotBlank(message = "Describe the damage")
    @Size(max = Rules.NOTES, message = "Description can be at most " + Rules.NOTES + " characters")
    private String description;

    @NotBlank(message = "Damage severity is required")
    @Pattern(regexp = Rules.SEVERITY_PATTERN, message = Rules.SEVERITY_MSG)
    private String damageSeverity;

    @NotNull(message = "Estimated repair cost is required")
    @DecimalMin(value = "0.0", inclusive = true, message = "Cost cannot be negative")
    @DecimalMax(value = Rules.MONEY_MAX, message = "Cost is too large")
    private BigDecimal estimatedRepairCost;

    // When the damage actually happened. Defaults to today when left empty.
    @PastOrPresent(message = "Damage date cannot be in the future")
    private java.time.LocalDate damageDate;

    public CreateDamageReportRequest() { }

    public Integer getVehicleId() { return vehicleId; }
    public void setVehicleId(Integer vehicleId) { this.vehicleId = vehicleId; }

    public Integer getHandoverId() { return handoverId; }
    public void setHandoverId(Integer handoverId) { this.handoverId = handoverId; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = Rules.clean(description); }

    public String getDamageSeverity() { return damageSeverity; }
    public void setDamageSeverity(String damageSeverity) { this.damageSeverity = Rules.upper(damageSeverity); }

    public BigDecimal getEstimatedRepairCost() { return estimatedRepairCost; }
    public void setEstimatedRepairCost(BigDecimal estimatedRepairCost) {
        this.estimatedRepairCost = estimatedRepairCost;
    }

    public java.time.LocalDate getDamageDate() { return damageDate; }
    public void setDamageDate(java.time.LocalDate damageDate) { this.damageDate = damageDate; }
}
