package com.vehiclerental.dao.impl.rowmapper;

import com.vehiclerental.dao.RowMapper;
import com.vehiclerental.model.MaintenanceRecord;

import java.math.BigDecimal;
import java.sql.Date;
import java.sql.ResultSet;
import java.sql.SQLException;

// Column -> field:
//   maintenance_id -> eventId
//   service_date   -> eventDate
public class MaintenanceRowMapper implements RowMapper<MaintenanceRecord> {

    @Override
    public MaintenanceRecord map(ResultSet rs) throws SQLException {
        MaintenanceRecord m = new MaintenanceRecord();
        m.setEventId(rs.getInt("maintenance_id"));
        m.setVehicleId(rs.getInt("vehicle_id"));

        Date serviceDate = rs.getDate("service_date");
        if (serviceDate != null) {
            m.setEventDate(serviceDate.toLocalDate());
        }

        m.setStatus(rs.getString("status"));
        m.setDescription(rs.getString("description"));

        int staffId = rs.getInt("handled_by_staff_id");
        if (!rs.wasNull()) {
            m.setHandledByStaffId(staffId);
        }

        m.setRepairType(rs.getString("repair_type"));

        BigDecimal cost = rs.getBigDecimal("cost");
        if (cost != null) {
            m.setCost(cost);
        }

        m.setServiceProvider(rs.getString("service_provider"));

        Date end = rs.getDate("expected_end_date");
        if (end != null) {
            m.setExpectedEndDate(end.toLocalDate());
        }

        Date next = rs.getDate("next_service_date");
        if (next != null) {
            m.setNextServiceDate(next.toLocalDate());
        }
        return m;
    }
}
