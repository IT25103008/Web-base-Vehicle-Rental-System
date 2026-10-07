package com.vehiclerental.dao.impl;

import com.vehiclerental.dao.AbstractJdbcDao;
import com.vehiclerental.dao.MaintenanceDao;
import com.vehiclerental.dao.impl.rowmapper.MaintenanceRowMapper;
import com.vehiclerental.model.MaintenanceRecord;
import org.springframework.stereotype.Repository;

import javax.sql.DataSource;
import java.sql.Date;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public class MaintenanceDaoImpl extends AbstractJdbcDao<MaintenanceRecord, Integer>
        implements MaintenanceDao {

    private static final MaintenanceRowMapper MAPPER = new MaintenanceRowMapper();

    public MaintenanceDaoImpl(DataSource dataSource) {
        super(dataSource);
    }

    @Override
    public MaintenanceRecord save(MaintenanceRecord m) {
        String sql = "INSERT INTO maintenance_records " +
                "(vehicle_id, service_date, status, description, handled_by_staff_id, " +
                " repair_type, cost, service_provider, next_service_date, expected_end_date) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";

        Date serviceDate = m.getEventDate() != null ? Date.valueOf(m.getEventDate()) : null;
        Date next = m.getNextServiceDate() != null ? Date.valueOf(m.getNextServiceDate()) : null;

        int newId = executeInsertReturnId(sql,
                m.getVehicleId(),
                serviceDate,
                m.getStatus(),
                m.getDescription(),
                m.getHandledByStaffId(),
                m.getRepairType(),
                m.getCost(),
                m.getServiceProvider(),
                next,
                m.getExpectedEndDate() != null ? Date.valueOf(m.getExpectedEndDate()) : null
        );
        m.setEventId(newId);
        return m;
    }

    @Override
    public Optional<MaintenanceRecord> findById(int eventId) {
        String sql = "SELECT * FROM maintenance_records WHERE maintenance_id = ?";
        return queryOne(sql, MAPPER, eventId);
    }

    @Override
    public List<MaintenanceRecord> findAll() {
        String sql = "SELECT * FROM maintenance_records ORDER BY service_date DESC";
        return queryList(sql, MAPPER);
    }

    @Override
    public List<MaintenanceRecord> findByVehicle(int vehicleId) {
        String sql = "SELECT * FROM maintenance_records WHERE vehicle_id = ? ORDER BY service_date DESC";
        return queryList(sql, MAPPER, vehicleId);
    }

    @Override
    public List<MaintenanceRecord> findUpcoming(int daysAhead) {
        // Any maintenance record whose next_service_date falls within the next N days.
        // The date window is worked out in Java so the SQL stays simple.
        LocalDate today = com.vehiclerental.util.AppClock.today();
        String sql = "SELECT * FROM maintenance_records " +
                "WHERE next_service_date IS NOT NULL " +
                "AND next_service_date >= ? " +
                "AND next_service_date <= ? " +
                "ORDER BY next_service_date";
        return queryList(sql, MAPPER, Date.valueOf(today), Date.valueOf(today.plusDays(daysAhead)));
    }

    @Override
    public void updateStatus(int eventId, String newStatus) {
        String sql = "UPDATE maintenance_records SET status = ? WHERE maintenance_id = ?";
        executeUpdate(sql, newStatus, eventId);
    }

    @Override
    public void update(MaintenanceRecord m) {
        String sql = "UPDATE maintenance_records SET service_date = ?, expected_end_date = ?, repair_type = ?, " +
                "cost = ?, service_provider = ?, next_service_date = ?, description = ? " +
                "WHERE maintenance_id = ?";
        executeUpdate(sql,
                Date.valueOf(m.getEventDate()),
                m.getExpectedEndDate() != null ? Date.valueOf(m.getExpectedEndDate()) : null,
                m.getRepairType(),
                m.getCost(),
                m.getServiceProvider(),
                m.getNextServiceDate() != null ? Date.valueOf(m.getNextServiceDate()) : null,
                m.getDescription(),
                m.getEventId());
    }

    @Override
    public void delete(int eventId) {
        String sql = "DELETE FROM maintenance_records WHERE maintenance_id = ?";
        executeUpdate(sql, eventId);
    }

    /**
     * Work that keeps the vehicle off the road at some point inside [from, to].
     * The workshop window is service_date .. expected_end_date (a null end date
     * means a single-day service).
     */
    @Override
    public List<MaintenanceRecord> findBlocking(int vehicleId, LocalDate from, LocalDate to,
                                                Integer excludeEventId) {
        String sql = "SELECT * FROM maintenance_records " +
                "WHERE vehicle_id = ? " +
                "AND status IN ('SCHEDULED','IN_PROGRESS') " +
                "AND service_date <= ? " +
                "AND ? <= COALESCE(expected_end_date, service_date) " +
                (excludeEventId != null ? "AND maintenance_id <> ? " : "") +
                "ORDER BY service_date";

        if (excludeEventId != null) {
            return queryList(sql, MAPPER, vehicleId, Date.valueOf(to), Date.valueOf(from), excludeEventId);
        }
        return queryList(sql, MAPPER, vehicleId, Date.valueOf(to), Date.valueOf(from));
    }

    @Override
    public List<MaintenanceRecord> findBlockingToday(int vehicleId, LocalDate today, Integer excludeEventId) {
        return findBlocking(vehicleId, today, today, excludeEventId);
    }

    @Override
    public List<MaintenanceRecord> findDueToStart(LocalDate asOf) {
        String sql = "SELECT * FROM maintenance_records " +
                "WHERE status = 'SCHEDULED' AND service_date <= ? ORDER BY service_date";
        return queryList(sql, MAPPER, Date.valueOf(asOf));
    }
}
