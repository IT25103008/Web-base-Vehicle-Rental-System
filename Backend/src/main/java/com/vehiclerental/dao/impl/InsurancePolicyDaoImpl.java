package com.vehiclerental.dao.impl;

import com.vehiclerental.dao.AbstractJdbcDao;
import com.vehiclerental.dao.InsurancePolicyDao;
import com.vehiclerental.dao.impl.rowmapper.InsurancePolicyRowMapper;
import com.vehiclerental.model.InsurancePolicy;
import org.springframework.stereotype.Repository;

import javax.sql.DataSource;
import java.sql.Date;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public class InsurancePolicyDaoImpl extends AbstractJdbcDao<InsurancePolicy, Integer>
        implements InsurancePolicyDao {

    private static final InsurancePolicyRowMapper MAPPER = new InsurancePolicyRowMapper();

    public InsurancePolicyDaoImpl(DataSource dataSource) {
        super(dataSource);
    }

    @Override
    public InsurancePolicy save(InsurancePolicy p) {
        String sql = "INSERT INTO insurance_policies " +
                "(vehicle_id, policy_number, provider, start_date, expiry_date, status) " +
                "VALUES (?, ?, ?, ?, ?, ?)";

        Date start = p.getStartDate() != null ? Date.valueOf(p.getStartDate()) : null;
        Date expiry = p.getExpiryDate() != null ? Date.valueOf(p.getExpiryDate()) : null;

        int newId = executeInsertReturnId(sql,
                p.getVehicleId(),
                p.getPolicyNumber(),
                p.getProvider(),
                start,
                expiry,
                p.getStatus()
        );
        p.setPolicyId(newId);
        return p;
    }

    @Override
    public Optional<InsurancePolicy> findById(int policyId) {
        String sql = "SELECT * FROM insurance_policies WHERE policy_id = ?";
        return queryOne(sql, MAPPER, policyId);
    }

    @Override
    public Optional<InsurancePolicy> findActiveByVehicle(int vehicleId) {
        String sql = "SELECT * FROM insurance_policies " +
                "WHERE vehicle_id = ? AND status = 'ACTIVE' " +
                "ORDER BY expiry_date DESC LIMIT 1";
        return queryOne(sql, MAPPER, vehicleId);
    }

    @Override
    public List<InsurancePolicy> findAll() {
        String sql = "SELECT * FROM insurance_policies ORDER BY expiry_date";
        return queryList(sql, MAPPER);
    }

    @Override
    public List<InsurancePolicy> findExpiringWithinDays(int daysAhead) {
        LocalDate today = com.vehiclerental.util.AppClock.today();
        String sql = "SELECT * FROM insurance_policies " +
                "WHERE status = 'ACTIVE' " +
                "AND expiry_date >= ? " +
                "AND expiry_date <= ? " +
                "ORDER BY expiry_date";
        return queryList(sql, MAPPER, Date.valueOf(today), Date.valueOf(today.plusDays(daysAhead)));
    }

    @Override
    public void update(InsurancePolicy p) {
        String sql = "UPDATE insurance_policies SET " +
                "vehicle_id = ?, policy_number = ?, provider = ?, start_date = ?, expiry_date = ?, status = ? " +
                "WHERE policy_id = ?";

        Date start = p.getStartDate() != null ? Date.valueOf(p.getStartDate()) : null;
        Date expiry = p.getExpiryDate() != null ? Date.valueOf(p.getExpiryDate()) : null;

        executeUpdate(sql,
                p.getVehicleId(),
                p.getPolicyNumber(),
                p.getProvider(),
                start,
                expiry,
                p.getStatus(),
                p.getPolicyId()
        );
    }

    @Override
    public void delete(int policyId) {
        String sql = "DELETE FROM insurance_policies WHERE policy_id = ?";
        executeUpdate(sql, policyId);
    }

    @Override
    public List<InsurancePolicy> findByVehicle(int vehicleId) {
        String sql = "SELECT * FROM insurance_policies WHERE vehicle_id = ? ORDER BY expiry_date DESC";
        return queryList(sql, MAPPER, vehicleId);
    }

    @Override
    public Optional<InsurancePolicy> findCovering(int vehicleId, LocalDate from, LocalDate to) {
        String sql = "SELECT * FROM insurance_policies " +
                "WHERE vehicle_id = ? AND status = 'ACTIVE' " +
                "AND start_date <= ? AND expiry_date >= ? " +
                "ORDER BY expiry_date DESC LIMIT 1";
        return queryOne(sql, MAPPER, vehicleId, Date.valueOf(from), Date.valueOf(to));
    }

    @Override
    public List<InsurancePolicy> findActiveOverlapping(int vehicleId, LocalDate from, LocalDate to,
                                                       Integer excludePolicyId) {
        String sql = "SELECT * FROM insurance_policies " +
                "WHERE vehicle_id = ? AND status = 'ACTIVE' " +
                "AND start_date <= ? AND ? <= expiry_date " +
                (excludePolicyId != null ? "AND policy_id <> ? " : "") +
                "ORDER BY start_date";
        if (excludePolicyId != null) {
            return queryList(sql, MAPPER, vehicleId, Date.valueOf(to), Date.valueOf(from), excludePolicyId);
        }
        return queryList(sql, MAPPER, vehicleId, Date.valueOf(to), Date.valueOf(from));
    }

    @Override
    public int expirePoliciesBefore(LocalDate today) {
        String sql = "UPDATE insurance_policies SET status = 'EXPIRED' " +
                "WHERE status = 'ACTIVE' AND expiry_date < ?";
        return executeUpdate(sql, Date.valueOf(today));
    }
}
