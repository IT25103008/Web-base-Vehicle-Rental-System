package com.vehiclerental.dto.request;

import com.vehiclerental.validation.Rules;
import jakarta.validation.constraints.*;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Editing a service record. It carries every editable field, so a save
 * replaces them all. The vehicle is deliberately absent: a record belongs to
 * the car it was written for. Which fields may actually change depends on the
 * record's status and is decided in MaintenanceServiceImpl.update.
 */
public class UpdateMaintenanceRequest {

    @NotNull(message = "Service date is required")
    private LocalDate eventDate;

    @NotBlank(message = "Repair type is required")
    @Size(max = Rules.PROVIDER, message = "Repair type can be at most " + Rules.PROVIDER + " characters")
    private String repairType;

    @NotNull(message = "Cost is required")
    @DecimalMin(value = "0.0", inclusive = true, message = "Cost cannot be negative")
    @DecimalMax(value = Rules.MONEY_MAX, message = "Cost is too large")
    private BigDecimal cost;

    @NotBlank(message = "Service provider is required")
    @Size(max = Rules.PROVIDER, message = "Service provider can be at most " + Rules.PROVIDER + " characters")
    private String serviceProvider;

    private LocalDate nextServiceDate;

    @Size(max = Rules.NOTES, message = "Description can be at most " + Rules.NOTES + " characters")
    private String description;

    /** Last day the vehicle stays in the workshop; empty means a single-day service. */
    private LocalDate expectedEndDate;

    public UpdateMaintenanceRequest() { }

    public LocalDate getEventDate() { return eventDate; }
    public void setEventDate(LocalDate eventDate) { this.eventDate = eventDate; }

    public String getRepairType() { return repairType; }
    public void setRepairType(String repairType) { this.repairType = Rules.clean(repairType); }

    public BigDecimal getCost() { return cost; }
    public void setCost(BigDecimal cost) { this.cost = cost; }

    public String getServiceProvider() { return serviceProvider; }
    public void setServiceProvider(String serviceProvider) { this.serviceProvider = Rules.clean(serviceProvider); }

    public LocalDate getNextServiceDate() { return nextServiceDate; }
    public void setNextServiceDate(LocalDate nextServiceDate) { this.nextServiceDate = nextServiceDate; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = Rules.clean(description); }

    public LocalDate getExpectedEndDate() { return expectedEndDate; }
    public void setExpectedEndDate(LocalDate expectedEndDate) { this.expectedEndDate = expectedEndDate; }
}
