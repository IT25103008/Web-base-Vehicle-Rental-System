package com.vehiclerental.model;

import java.math.BigDecimal;
import java.time.LocalDate;

// eventId   -> damage_reports.damage_report_id
// eventDate -> damage_reports.reported_date
public class DamageReport extends VehicleEvent {

    private Integer handoverId;
    private Integer reportedBy;
    private String damageSeverity;          // MINOR / MODERATE / SEVERE
    private BigDecimal estimatedRepairCost;
    private LocalDate damageDate;           // when the damage happened (eventDate = when it was reported)

    public DamageReport() {
    }

    @Override
    public String applyEffect() {
        // A damage report flags the vehicle for a possible insurance claim.
        return "Vehicle flagged for possible insurance claim";
    }

    public Integer getHandoverId() { return handoverId; }
    public void setHandoverId(Integer handoverId) { this.handoverId = handoverId; }

    public Integer getReportedBy() { return reportedBy; }
    public void setReportedBy(Integer reportedBy) { this.reportedBy = reportedBy; }

    public String getDamageSeverity() { return damageSeverity; }
    public void setDamageSeverity(String damageSeverity) { this.damageSeverity = damageSeverity; }

    public LocalDate getDamageDate() { return damageDate; }
    public void setDamageDate(LocalDate damageDate) { this.damageDate = damageDate; }

    public BigDecimal getEstimatedRepairCost() { return estimatedRepairCost; }
    public void setEstimatedRepairCost(BigDecimal estimatedRepairCost) { this.estimatedRepairCost = estimatedRepairCost; }
}
