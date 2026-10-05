package com.vehiclerental.dao.impl.rowmapper;

import com.vehiclerental.dao.RowMapper;
import com.vehiclerental.enums.PaymentStatus;
import com.vehiclerental.model.Payment;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;

public class PaymentRowMapper implements RowMapper<Payment> {

    @Override
    public Payment map(ResultSet rs) throws SQLException {
        Payment p = new Payment();
        p.setPaymentId(rs.getInt("payment_id"));
        p.setBookingId(rs.getInt("booking_id"));

        BigDecimal amount = rs.getBigDecimal("amount");
        if (amount != null) {
            p.setAmount(amount);
        }

        p.setStatus(PaymentStatus.valueOf(rs.getString("status")));
        p.setRefundAmount(rs.getBigDecimal("refund_amount"));
        p.setNote(rs.getString("note"));

        int updatedBy = rs.getInt("updated_by");
        if (!rs.wasNull()) {
            p.setUpdatedBy(updatedBy);
        }

        Timestamp ts = rs.getTimestamp("updated_at");
        if (ts != null) {
            p.setUpdatedAt(ts.toLocalDateTime());
        }
        return p;
    }
}
