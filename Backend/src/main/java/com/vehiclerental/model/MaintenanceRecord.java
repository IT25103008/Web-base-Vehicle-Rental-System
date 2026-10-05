package com.vehiclerental.model;

import java.math.BigDecimal;
import java.time.LocalDate;

// eventId   -> maintenance_records.maintenance_id
// eventDate -> maintenance_records.service_date
public class MaintenanceRecord extends VehicleEvent {

    private Integer handledByStaffId;
    private String repairType;
    private BigDecimal cost;
    private String serviceProvider;
    private LocalDate nextServiceDate;
    private LocalDate expectedEndDate;   // last day the vehicle is in the workshop

    public MaintenanceRecord() {
    }

    @Override
    public String applyEffect() {
        // Once a maintenance record is COMPLETED, the vehicle can be marked AVAILABLE again.
        return "Vehicle available after maintenance";
    }

    public Integer getHandledByStaffId() { return handledByStaffId; }
    public void setHandledByStaffId(Integer handledByStaffId) { this.handledByStaffId = handledByStaffId; }

    public String getRepairType() { return repairType; }
    public void setRepairType(String repairType) { this.repairType = repairType; }

    public BigDecimal getCost() { return cost; }
    public void setCost(BigDecimal cost) { this.cost = cost; }

    public String getServiceProvider() { return serviceProvider; }
    public void setServiceProvider(String serviceProvider) { this.serviceProvider = serviceProvider; }

    public LocalDate getExpectedEndDate() { return expectedEndDate; }
    public void setExpectedEndDate(LocalDate expectedEndDate) { this.expectedEndDate = expectedEndDate; }

    /** Last day this record keeps the vehicle off the road. */
    public LocalDate blockedUntil() {
        return expectedEndDate != null ? expectedEndDate : getEventDate();
    }

    public LocalDate getNextServiceDate() { return nextServiceDate; }
    public void setNextServiceDate(LocalDate nextServiceDate) { this.nextServiceDate = nextServiceDate; }
}
