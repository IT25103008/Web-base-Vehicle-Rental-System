package com.vehiclerental.dao.impl;

import com.vehiclerental.dao.AbstractJdbcDao;
import com.vehiclerental.dao.DamageReportDao;
import com.vehiclerental.dao.impl.rowmapper.DamageReportRowMapper;
import com.vehiclerental.model.DamageReport;
import org.springframework.stereotype.Repository;

import javax.sql.DataSource;
import java.sql.Date;
import java.util.List;
import java.util.Optional;

@Repository
public class DamageReportDaoImpl extends AbstractJdbcDao<DamageReport, Integer>
        implements DamageReportDao {

    private static final DamageReportRowMapper MAPPER = new DamageReportRowMapper();

    public DamageReportDaoImpl(DataSource dataSource) {
        super(dataSource);
    }

    @Override
    public DamageReport save(DamageReport d) {
        String sql = "INSERT INTO damage_reports " +
                "(vehicle_id, reported_date, status, description, handover_id, " +
                " reported_by, damage_severity, estimated_repair_cost, damage_date) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)";

        Date reportedDate = d.getEventDate() != null ? Date.valueOf(d.getEventDate()) : null;

        int newId = executeInsertReturnId(sql,
                d.getVehicleId(),
                reportedDate,
                d.getStatus(),
                d.getDescription(),
                d.getHandoverId(),
                d.getReportedBy(),
                d.getDamageSeverity(),
                d.getEstimatedRepairCost(),
                d.getDamageDate() != null ? Date.valueOf(d.getDamageDate()) : reportedDate
        );
        d.setEventId(newId);
        return d;
    }

    @Override
    public Optional<DamageReport> findById(int eventId) {
        String sql = "SELECT * FROM damage_reports WHERE damage_report_id = ?";
        return queryOne(sql, MAPPER, eventId);
    }

    @Override
    public List<DamageReport> findAll() {
        String sql = "SELECT * FROM damage_reports ORDER BY reported_date DESC";
        return queryList(sql, MAPPER);
    }

    @Override
    public List<DamageReport> findByVehicle(int vehicleId) {
        String sql = "SELECT * FROM damage_reports WHERE vehicle_id = ? ORDER BY reported_date DESC";
        return queryList(sql, MAPPER, vehicleId);
    }

    @Override
    public List<DamageReport> findByStatus(String status) {
        String sql = "SELECT * FROM damage_reports WHERE status = ? ORDER BY reported_date DESC";
        return queryList(sql, MAPPER, status);
    }

    @Override
    public List<DamageReport> findByHandover(int handoverId) {
        String sql = "SELECT * FROM damage_reports WHERE handover_id = ?";
        return queryList(sql, MAPPER, handoverId);
    }

    @Override
    public void updateStatus(int eventId, String newStatus) {
        String sql = "UPDATE damage_reports SET status = ? WHERE damage_report_id = ?";
        executeUpdate(sql, newStatus, eventId);
    }

    @Override
    public void delete(int eventId) {
        String sql = "DELETE FROM damage_reports WHERE damage_report_id = ?";
        executeUpdate(sql, eventId);
    }

    @Override
    public List<DamageReport> findOpenByVehicle(int vehicleId) {
        String sql = "SELECT * FROM damage_reports WHERE vehicle_id = ? AND status = 'UNDER_REVIEW' " +
                "ORDER BY reported_date DESC";
        return queryList(sql, MAPPER, vehicleId);
    }
}
