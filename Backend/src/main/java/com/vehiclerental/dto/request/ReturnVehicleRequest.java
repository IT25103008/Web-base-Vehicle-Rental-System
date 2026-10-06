package com.vehiclerental.dto.request;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.vehiclerental.validation.Rules;
import jakarta.validation.constraints.*;

import java.math.BigDecimal;
import java.time.LocalDate;

public class ReturnVehicleRequest {

    @NotNull(message = "Odometer reading is required")
    @Min(value = 0, message = "Odometer reading cannot be negative")
    @Max(value = Rules.MILEAGE_MAX, message = "Odometer reading looks too large")
    @JsonAlias("mileageAtEvent")
    private Integer returnMileage;

    @NotBlank(message = "Fuel level is required")
    @Pattern(regexp = Rules.FUEL_LEVEL_PATTERN, message = Rules.FUEL_LEVEL_MSG)
    private String fuelLevel;

    @Size(max = Rules.NOTES, message = "Condition notes can be at most " + Rules.NOTES + " characters")
    private String conditionNotes;

    @Size(max = Rules.NOTES, message = "Damage description can be at most " + Rules.NOTES + " characters")
    private String damageDescription;    // null/blank means "no damage"

    @Pattern(regexp = Rules.SEVERITY_PATTERN, message = Rules.SEVERITY_MSG)
    private String damageSeverity;       // MINOR / MODERATE / SEVERE

    @DecimalMin(value = "0.0", inclusive = true, message = "Repair cost cannot be negative")
    @DecimalMax(value = Rules.MONEY_MAX, message = "Repair cost is too large")
    private BigDecimal damageEstimatedCost;   // charged to the customer's final bill

    @PastOrPresent(message = "Damage date cannot be in the future")
    private LocalDate damageDate;             // when the damage happened (defaults to today)

    public Integer getReturnMileage() { return returnMileage; }
    public void setReturnMileage(Integer returnMileage) { this.returnMileage = returnMileage; }
    public String getFuelLevel() { return fuelLevel; }
    public void setFuelLevel(String fuelLevel) { this.fuelLevel = Rules.clean(fuelLevel); }
    public String getConditionNotes() { return conditionNotes; }
    public void setConditionNotes(String conditionNotes) { this.conditionNotes = Rules.clean(conditionNotes); }
    public String getDamageDescription() { return damageDescription; }
    public void setDamageDescription(String damageDescription) { this.damageDescription = Rules.clean(damageDescription); }
    public String getDamageSeverity() { return damageSeverity; }
    public void setDamageSeverity(String damageSeverity) { this.damageSeverity = Rules.upper(damageSeverity); }
    public BigDecimal getDamageEstimatedCost() { return damageEstimatedCost; }
    public void setDamageEstimatedCost(BigDecimal damageEstimatedCost) { this.damageEstimatedCost = damageEstimatedCost; }
    public LocalDate getDamageDate() { return damageDate; }
    public void setDamageDate(LocalDate damageDate) { this.damageDate = damageDate; }

    public boolean hasDamage() {
        return damageDescription != null && !damageDescription.isBlank();
    }
}
