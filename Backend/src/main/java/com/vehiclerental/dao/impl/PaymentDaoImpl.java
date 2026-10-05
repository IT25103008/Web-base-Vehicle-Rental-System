package com.vehiclerental.dao.impl;

import com.vehiclerental.dao.AbstractJdbcDao;
import com.vehiclerental.dao.PaymentDao;
import com.vehiclerental.dao.impl.rowmapper.PaymentRowMapper;
import com.vehiclerental.model.Payment;
import com.vehiclerental.util.AppClock;
import org.springframework.stereotype.Repository;

import javax.sql.DataSource;
import java.math.BigDecimal;
import java.sql.Timestamp;
import java.util.List;
import java.util.Optional;

@Repository
public class PaymentDaoImpl extends AbstractJdbcDao<Payment, Integer> implements PaymentDao {

    private static final PaymentRowMapper MAPPER = new PaymentRowMapper();

    public PaymentDaoImpl(DataSource dataSource) {
        super(dataSource);
    }

    @Override
    public Payment save(Payment p) {
        String sql = "INSERT INTO payments (booking_id, amount, status, updated_by, updated_at) " +
                "VALUES (?, ?, ?, ?, ?)";

        Timestamp when = p.getUpdatedAt() != null
                ? Timestamp.valueOf(p.getUpdatedAt())
                : Timestamp.valueOf(AppClock.now());

        int newId = executeInsertReturnId(sql,
                p.getBookingId(),
                p.getAmount(),
                p.getStatus().name(),
                p.getUpdatedBy(),
                when
        );
        p.setPaymentId(newId);
        return p;
    }

    @Override
    public Optional<Payment> findById(int paymentId) {
        String sql = "SELECT * FROM payments WHERE payment_id = ?";
        return queryOne(sql, MAPPER, paymentId);
    }

    @Override
    public Optional<Payment> findByBooking(int bookingId) {
        String sql = "SELECT * FROM payments WHERE booking_id = ?";
        return queryOne(sql, MAPPER, bookingId);
    }

    @Override
    public List<Payment> findByStatus(String status) {
        String sql = "SELECT * FROM payments WHERE status = ? ORDER BY updated_at DESC";
        return queryList(sql, MAPPER, status);
    }

    @Override
    public List<Payment> findAll() {
        String sql = "SELECT * FROM payments ORDER BY updated_at DESC";
        return queryList(sql, MAPPER);
    }

    @Override
    public void updateStatus(int paymentId, String newStatus, Integer updatedByStaffId) {
        String sql = "UPDATE payments SET status = ?, updated_by = ?, updated_at = ? " +
                "WHERE payment_id = ?";
        executeUpdate(sql,
                newStatus,
                updatedByStaffId,
                Timestamp.valueOf(AppClock.now()),
                paymentId);
    }

    @Override
    public void updateAmount(int paymentId, BigDecimal newAmount) {
        String sql = "UPDATE payments SET amount = ?, updated_at = ? WHERE payment_id = ?";
        executeUpdate(sql, newAmount, Timestamp.valueOf(AppClock.now()), paymentId);
    }

    @Override
    public void updateRefund(int paymentId, BigDecimal refundAmount, String note) {
        executeUpdate("UPDATE payments SET refund_amount = ?, note = ?, updated_at = ? WHERE payment_id = ?",
                      refundAmount, note, Timestamp.valueOf(AppClock.now()), paymentId);
    }

    @Override
    public void updateNote(int paymentId, String note) {
        executeUpdate("UPDATE payments SET note = ?, updated_at = ? WHERE payment_id = ?",
                      note, Timestamp.valueOf(AppClock.now()), paymentId);
    }

    @Override
    public List<Payment> findPage(String status, Integer branchId, String text, int offset, int limit) {
        StringBuilder sql = new StringBuilder(
            "SELECT p.* FROM payments p JOIN bookings b ON b.booking_id = p.booking_id WHERE 1=1 ");
        List<Object> params = new java.util.ArrayList<>();
        pageFilter(sql, params, status, branchId, text);
        sql.append("ORDER BY p.updated_at DESC, p.payment_id DESC LIMIT ? OFFSET ?");
        params.add(limit);
        params.add(offset);
        return queryList(sql.toString(), MAPPER, params.toArray());
    }

    @Override
    public long count(String status, Integer branchId, String text) {
        StringBuilder sql = new StringBuilder(
            "SELECT COUNT(*) FROM payments p JOIN bookings b ON b.booking_id = p.booking_id WHERE 1=1 ");
        List<Object> params = new java.util.ArrayList<>();
        pageFilter(sql, params, status, branchId, text);
        return queryCount(sql.toString(), params.toArray());
    }

    private static void pageFilter(StringBuilder sql, List<Object> params, String status, Integer branchId, String text) {
        if (status != null && !status.isBlank()) {
            sql.append("AND p.status = ? ");
            params.add(status);
        }
        if (branchId != null) {
            sql.append("AND b.pickup_branch_id = ? ");
            params.add(branchId);
        }
        if (text != null && !text.isBlank()) {
            String id = text.trim().replace("#", "");
            sql.append("AND (CAST(p.booking_id AS CHAR) = ? OR CAST(p.payment_id AS CHAR) = ?) ");
            params.add(id);
            params.add(id);
        }
    }
}
