package com.vehiclerental.dto.request;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.vehiclerental.validation.Rules;
import jakarta.validation.constraints.*;

public class PickupRequest {

    @NotNull(message = "Odometer reading is required")
    @Min(value = 0, message = "Odometer reading cannot be negative")
    @Max(value = Rules.MILEAGE_MAX, message = "Odometer reading looks too large")
    @JsonAlias("mileage")
    private Integer mileageAtEvent;

    @NotBlank(message = "Fuel level is required")
    @Pattern(regexp = Rules.FUEL_LEVEL_PATTERN, message = Rules.FUEL_LEVEL_MSG)
    private String fuelLevel;

    @Size(max = Rules.NOTES, message = "Condition notes can be at most " + Rules.NOTES + " characters")
    private String conditionNotes;

    public Integer getMileageAtEvent() { return mileageAtEvent; }
    public void setMileageAtEvent(Integer mileageAtEvent) { this.mileageAtEvent = mileageAtEvent; }
    public String getFuelLevel() { return fuelLevel; }
    public void setFuelLevel(String fuelLevel) { this.fuelLevel = Rules.clean(fuelLevel); }
    public String getConditionNotes() { return conditionNotes; }
    public void setConditionNotes(String conditionNotes) { this.conditionNotes = Rules.clean(conditionNotes); }
}
