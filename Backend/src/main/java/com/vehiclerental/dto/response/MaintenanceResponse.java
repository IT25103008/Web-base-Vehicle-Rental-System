package com.vehiclerental.dto.response;

import java.math.BigDecimal;
import java.time.LocalDate;

public class MaintenanceResponse {

    private int eventId;
    private int vehicleId;
    private LocalDate eventDate;
    private String status;
    private String description;
    private Integer handledByStaffId;
    private String repairType;
    private BigDecimal cost;
    private String serviceProvider;
    private LocalDate nextServiceDate;
    private LocalDate expectedEndDate;

    public MaintenanceResponse() { }

    public int getEventId() { return eventId; }
    public void setEventId(int eventId) { this.eventId = eventId; }

    public int getVehicleId() { return vehicleId; }
    public void setVehicleId(int vehicleId) { this.vehicleId = vehicleId; }

    public LocalDate getEventDate() { return eventDate; }
    public void setEventDate(LocalDate eventDate) { this.eventDate = eventDate; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public Integer getHandledByStaffId() { return handledByStaffId; }
    public void setHandledByStaffId(Integer handledByStaffId) { this.handledByStaffId = handledByStaffId; }

    public String getRepairType() { return repairType; }
    public void setRepairType(String repairType) { this.repairType = repairType; }

    public BigDecimal getCost() { return cost; }
    public void setCost(BigDecimal cost) { this.cost = cost; }

    public String getServiceProvider() { return serviceProvider; }
    public void setServiceProvider(String serviceProvider) { this.serviceProvider = serviceProvider; }

    public LocalDate getNextServiceDate() { return nextServiceDate; }
    public void setNextServiceDate(LocalDate nextServiceDate) { this.nextServiceDate = nextServiceDate; }

    public LocalDate getExpectedEndDate() { return expectedEndDate; }
    public void setExpectedEndDate(LocalDate expectedEndDate) { this.expectedEndDate = expectedEndDate; }
}
