package com.vehiclerental.dto.request;

import com.vehiclerental.validation.Rules;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * The date order (end on or after the service date, next service after the
 * end) is checked in MaintenanceServiceImpl, alongside the booking clashes.
 */
public class CreateMaintenanceRequest {

    @NotNull(message = "Choose a vehicle")
    @Positive(message = "Choose a vehicle")
    private Integer vehicleId;

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

    // Last day the vehicle is expected to stay in the workshop.
    // Left empty, the work is treated as a single-day service.
    private LocalDate expectedEndDate;

    // Start the work immediately rather than just scheduling it.
    // Only allowed when the service date is today.
    private boolean startNow;

    public CreateMaintenanceRequest() { }

    public Integer getVehicleId() { return vehicleId; }
    public void setVehicleId(Integer vehicleId) { this.vehicleId = vehicleId; }

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

    public boolean isStartNow() { return startNow; }
    public void setStartNow(boolean startNow) { this.startNow = startNow; }

    /** Last day this job keeps the vehicle off the road. */
    public LocalDate blockedUntil() {
        return expectedEndDate != null ? expectedEndDate : eventDate;
    }
}
