package com.vehiclerental.dao.impl.rowmapper;

import com.vehiclerental.dao.RowMapper;
import com.vehiclerental.enums.HandoverStatus;
import com.vehiclerental.enums.HandoverType;
import com.vehiclerental.model.Handover;

import java.sql.ResultSet;
import java.sql.SQLException;

public class HandoverRowMapper implements RowMapper<Handover> {

    @Override
    public Handover map(ResultSet rs) throws SQLException {
        Handover h = new Handover();
        h.setHandoverId(rs.getInt("handover_id"));
        h.setBookingId(rs.getInt("booking_id"));
        h.setHandoverType(HandoverType.valueOf(rs.getString("handover_type")));
        h.setHandoverDate(rs.getTimestamp("handover_date").toLocalDateTime());

        int staff = rs.getInt("processed_by_staff_id");
        if (!rs.wasNull()) h.setProcessedByStaffId(staff);

        int mileage = rs.getInt("mileage_at_event");
        if (!rs.wasNull()) h.setMileageAtEvent(mileage);

        h.setFuelLevel(rs.getString("fuel_level"));
        h.setConditionNotes(rs.getString("condition_notes"));

        String status = rs.getString("status");
        if (status != null) h.setStatus(HandoverStatus.valueOf(status));
        return h;
    }
}
