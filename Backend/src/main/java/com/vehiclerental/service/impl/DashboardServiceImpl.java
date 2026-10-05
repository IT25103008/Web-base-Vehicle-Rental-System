package com.vehiclerental.service.impl;

import com.vehiclerental.dto.response.DashboardResponse;
import com.vehiclerental.exception.DataAccessException;
import com.vehiclerental.service.DashboardService;
import com.vehiclerental.service.InsurancePolicyService;
import com.vehiclerental.util.AppClock;
import org.springframework.stereotype.Service;

import javax.sql.DataSource;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;

// The dashboard only reads aggregate numbers, so it runs its queries
// directly on the DataSource (plain JDBC) instead of through a DAO.
//
// Every figure can be limited to one branch (C6): bookings by where they are
// collected, vehicles by where they are kept, and damage, policies and
// payments through the vehicle or booking they belong to. Staff see their own
// branch; an administrator sees the company or picks a branch.
@Service
public class DashboardServiceImpl implements DashboardService {

    private final DataSource dataSource;
    private final InsurancePolicyService insurancePolicyService;

    public DashboardServiceImpl(DataSource dataSource, InsurancePolicyService insurancePolicyService) {
        this.dataSource = dataSource;
        this.insurancePolicyService = insurancePolicyService;
    }

    @Override
    public DashboardResponse getDashboard() {
        return getDashboard(null);
    }

    @Override
    public DashboardResponse getDashboard(Integer branchId) {
        // Policies that ran out since the last look are marked expired first, so
        // the number on the dashboard is the truth rather than a stale flag.
        insurancePolicyService.expireLapsedPolicies();

        LocalDate today = AppClock.today();
        Date sqlToday = Date.valueOf(today);

        // Branch conditions, one per table the figures come from.
        String bk = branchId == null ? "" : " AND b.pickup_branch_id = " + branchId;
        String vh = branchId == null ? "" : " AND v.branch_id = " + branchId;

        DashboardResponse d = new DashboardResponse();
        d.setBranchId(branchId);

        d.setTotalBookings(countRows("SELECT COUNT(*) FROM bookings b WHERE 1=1" + bk));
        d.setActiveRentals(countRows(
                "SELECT COUNT(*) FROM bookings b WHERE b.status = 'ACTIVE_RENTAL'" + bk));
        d.setPendingApprovals(countRows(
                "SELECT COUNT(*) FROM bookings b WHERE b.status = 'PENDING_APPROVAL'" + bk));
        d.setAvailableVehicles(countRows(
                "SELECT COUNT(*) FROM vehicles v WHERE v.status = 'AVAILABLE'" + vh));
        d.setVehiclesUnderMaintenance(countRows(
                "SELECT COUNT(*) FROM vehicles v WHERE v.status = 'UNDER_MAINTENANCE'" + vh));
        d.setExpiredInsurancePolicies(countRows(
                "SELECT COUNT(*) FROM insurance_policies p JOIN vehicles v ON v.vehicle_id = p.vehicle_id "
                + "WHERE p.expiry_date < ?" + vh, sqlToday));

        // --- the numbers somebody actually has to act on today ---
        d.setOverdueReturns(countRows(
                "SELECT COUNT(*) FROM bookings b WHERE b.status = 'ACTIVE_RENTAL' AND b.return_date < ?" + bk,
                sqlToday));
        d.setMissedPickups(countRows(
                "SELECT COUNT(*) FROM bookings b WHERE b.status = 'APPROVED' AND b.pickup_date < ?" + bk,
                sqlToday));
        d.setUnpaidReadyForPickup(countRows(
                "SELECT COUNT(*) FROM bookings b JOIN payments p ON p.booking_id = b.booking_id " +
                "WHERE b.status = 'APPROVED' AND b.pickup_date <= ? AND p.status = 'PENDING'" + bk,
                Date.valueOf(today.plusDays(1))));
        d.setOpenDamageReports(countRows(
                "SELECT COUNT(*) FROM damage_reports dr JOIN vehicles v ON v.vehicle_id = dr.vehicle_id "
                + "WHERE dr.status = 'UNDER_REVIEW'" + vh));
        d.setPoliciesExpiringSoon(countRows(
                "SELECT COUNT(*) FROM insurance_policies p JOIN vehicles v ON v.vehicle_id = p.vehicle_id "
                + "WHERE p.status = 'ACTIVE' AND p.expiry_date >= ? AND p.expiry_date <= ?" + vh,
                sqlToday, Date.valueOf(today.plusDays(30))));
        d.setUninsuredVehicles(countRows(
                "SELECT COUNT(*) FROM vehicles v WHERE NOT EXISTS ( " +
                "  SELECT 1 FROM insurance_policies p WHERE p.vehicle_id = v.vehicle_id " +
                "  AND p.status = 'ACTIVE' AND p.start_date <= ? AND p.expiry_date >= ? )" + vh,
                sqlToday, sqlToday));

        d.setTotalRevenuePaid(sumMoney(
                "SELECT COALESCE(SUM(p.amount), 0) FROM payments p JOIN bookings b ON b.booking_id = p.booking_id "
                + "WHERE p.status = 'PAID'" + bk));
        d.setOutstandingPayments(sumMoney(
                "SELECT COALESCE(SUM(p.amount), 0) FROM payments p " +
                "JOIN bookings b ON b.booking_id = p.booking_id " +
                "WHERE p.status = 'PENDING' AND b.status IN " +
                "('PENDING_APPROVAL','APPROVED','ACTIVE_RENTAL','COMPLETED','CANCELLED','NO_SHOW')" + bk));

        d.setVehiclesByStatus(groupCount(
                "SELECT v.status, COUNT(*) FROM vehicles v WHERE 1=1" + vh + " GROUP BY v.status"));
        d.setBookingsByStatus(groupCount(
                "SELECT b.status, COUNT(*) FROM bookings b WHERE 1=1" + bk + " GROUP BY b.status"));

        return d;
    }

    // ---------- Helper methods (plain JDBC) ----------
    // The branch id is an Integer from our own code, never text from the
    // request, so appending it to the SQL cannot inject anything.

    private long countRows(String sql, Object... params) {
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            bind(ps, params);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getLong(1);
                }
                return 0L;
            }
        } catch (SQLException e) {
            throw new DataAccessException("Count query failed: " + sql, e);
        }
    }

    private BigDecimal sumMoney(String sql) {
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            if (rs.next()) {
                BigDecimal total = rs.getBigDecimal(1);
                return total != null ? total : BigDecimal.ZERO;
            }
            return BigDecimal.ZERO;
        } catch (SQLException e) {
            throw new DataAccessException("Money sum failed: " + sql, e);
        }
    }

    private Map<String, Long> groupCount(String sql) {
        Map<String, Long> results = new LinkedHashMap<>();
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                results.put(rs.getString(1), rs.getLong(2));
            }
        } catch (SQLException e) {
            throw new DataAccessException("Group-count query failed: " + sql, e);
        }
        return results;
    }

    private void bind(PreparedStatement ps, Object... params) throws SQLException {
        for (int i = 0; i < params.length; i++) {
            ps.setObject(i + 1, params[i]);
        }
    }
}
