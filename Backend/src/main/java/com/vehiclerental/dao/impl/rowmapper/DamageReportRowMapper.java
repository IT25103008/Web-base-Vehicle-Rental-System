package com.vehiclerental.dao.impl.rowmapper;

import com.vehiclerental.dao.RowMapper;
import com.vehiclerental.model.DamageReport;

import java.math.BigDecimal;
import java.sql.Date;
import java.sql.ResultSet;
import java.sql.SQLException;

// Column -> field:
//   damage_report_id -> eventId   (inherited from VehicleEvent)
//   reported_date    -> eventDate (inherited from VehicleEvent)
public class DamageReportRowMapper implements RowMapper<DamageReport> {

    @Override
    public DamageReport map(ResultSet rs) throws SQLException {
        DamageReport d = new DamageReport();
        d.setEventId(rs.getInt("damage_report_id"));
        d.setVehicleId(rs.getInt("vehicle_id"));

        Date reportedDate = rs.getDate("reported_date");
        if (reportedDate != null) {
            d.setEventDate(reportedDate.toLocalDate());
        }

        d.setStatus(rs.getString("status"));
        d.setDescription(rs.getString("description"));

        int handoverId = rs.getInt("handover_id");
        if (!rs.wasNull()) {
            d.setHandoverId(handoverId);
        }

        int reportedBy = rs.getInt("reported_by");
        if (!rs.wasNull()) {
            d.setReportedBy(reportedBy);
        }

        d.setDamageSeverity(rs.getString("damage_severity"));

        Date damageDate = rs.getDate("damage_date");
        if (damageDate != null) {
            d.setDamageDate(damageDate.toLocalDate());
        }

        BigDecimal repairCost = rs.getBigDecimal("estimated_repair_cost");
        if (repairCost != null) {
            d.setEstimatedRepairCost(repairCost);
        }

        return d;
    }
}
