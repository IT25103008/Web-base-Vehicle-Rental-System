package com.vehiclerental.dao.impl.rowmapper;

import com.vehiclerental.dao.RowMapper;
import com.vehiclerental.enums.BookingStatus;
import com.vehiclerental.model.Booking;

import java.sql.ResultSet;
import java.sql.SQLException;

public class BookingRowMapper implements RowMapper<Booking> {

    @Override
    public Booking map(ResultSet rs) throws SQLException {
        Booking b = new Booking();
        b.setBookingId(rs.getInt("booking_id"));
        b.setCustomerId(rs.getInt("customer_id"));
        b.setVehicleId(rs.getInt("vehicle_id"));
        b.setPickupBranchId(rs.getInt("pickup_branch_id"));

        int approver = rs.getInt("approved_by");
        if (!rs.wasNull()) b.setApprovedBy(approver);

        b.setPickupDate(rs.getDate("pickup_date").toLocalDate());
        b.setReturnDate(rs.getDate("return_date").toLocalDate());
        b.setSpecialRequests(rs.getString("special_requests"));
        b.setEstimatedCost(rs.getBigDecimal("estimated_cost"));
        b.setFinalCost(rs.getBigDecimal("final_cost"));

        java.sql.Date actualReturn = rs.getDate("actual_return_date");
        if (actualReturn != null) b.setActualReturnDate(actualReturn.toLocalDate());

        b.setStatus(BookingStatus.valueOf(rs.getString("status")));
        b.setSubmittedDate(rs.getTimestamp("submitted_date").toLocalDateTime());
        return b;
    }
}
