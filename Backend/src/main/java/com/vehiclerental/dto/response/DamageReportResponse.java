package com.vehiclerental.dto.response;

import java.math.BigDecimal;
import java.time.LocalDate;

public class DamageReportResponse {

    private int eventId;
    private int vehicleId;
    private Integer handoverId;
    private Integer reportedBy;
    private LocalDate eventDate;
    private String description;
    private String damageSeverity;
    private BigDecimal estimatedRepairCost;
    private java.time.LocalDate damageDate;
    private String status;

    public DamageReportResponse() { }

    public int getEventId() { return eventId; }
    public void setEventId(int eventId) { this.eventId = eventId; }

    public int getVehicleId() { return vehicleId; }
    public void setVehicleId(int vehicleId) { this.vehicleId = vehicleId; }

    public Integer getHandoverId() { return handoverId; }
    public void setHandoverId(Integer handoverId) { this.handoverId = handoverId; }

    public Integer getReportedBy() { return reportedBy; }
    public void setReportedBy(Integer reportedBy) { this.reportedBy = reportedBy; }

    public LocalDate getEventDate() { return eventDate; }
    public void setEventDate(LocalDate eventDate) { this.eventDate = eventDate; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public String getDamageSeverity() { return damageSeverity; }
    public void setDamageSeverity(String damageSeverity) { this.damageSeverity = damageSeverity; }

    public BigDecimal getEstimatedRepairCost() { return estimatedRepairCost; }
    public void setEstimatedRepairCost(BigDecimal estimatedRepairCost) {
        this.estimatedRepairCost = estimatedRepairCost;
    }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public java.time.LocalDate getDamageDate() { return damageDate; }
    public void setDamageDate(java.time.LocalDate damageDate) { this.damageDate = damageDate; }
}
