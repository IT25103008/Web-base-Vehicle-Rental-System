package com.vehiclerental.dao.impl;

import com.vehiclerental.dao.AbstractJdbcDao;
import com.vehiclerental.dao.InsuranceClaimDao;
import com.vehiclerental.dao.impl.rowmapper.InsuranceClaimRowMapper;
import com.vehiclerental.model.InsuranceClaim;
import org.springframework.stereotype.Repository;

import javax.sql.DataSource;
import java.sql.Date;
import java.util.List;
import java.util.Optional;

@Repository
public class InsuranceClaimDaoImpl extends AbstractJdbcDao<InsuranceClaim, Integer>
        implements InsuranceClaimDao {

    private static final InsuranceClaimRowMapper MAPPER = new InsuranceClaimRowMapper();

    public InsuranceClaimDaoImpl(DataSource dataSource) {
        super(dataSource);
    }

    @Override
    public InsuranceClaim save(InsuranceClaim c) {
        String sql = "INSERT INTO insurance_claims " +
                "(damage_report_id, insurance_provider, claim_amount, submitted_date, status) " +
                "VALUES (?, ?, ?, ?, ?)";

        Date submitted = c.getSubmittedDate() != null ? Date.valueOf(c.getSubmittedDate()) : null;

        int newId = executeInsertReturnId(sql,
                c.getDamageReportId(),
                c.getInsuranceProvider(),
                c.getClaimAmount(),
                submitted,
                c.getStatus().name()
        );
        c.setClaimId(newId);
        return c;
    }

    @Override
    public Optional<InsuranceClaim> findById(int claimId) {
        String sql = "SELECT * FROM insurance_claims WHERE claim_id = ?";
        return queryOne(sql, MAPPER, claimId);
    }

    @Override
    public List<InsuranceClaim> findAll() {
        String sql = "SELECT * FROM insurance_claims ORDER BY submitted_date DESC";
        return queryList(sql, MAPPER);
    }

    @Override
    public List<InsuranceClaim> findByStatus(String status) {
        String sql = "SELECT * FROM insurance_claims WHERE status = ? ORDER BY submitted_date DESC";
        return queryList(sql, MAPPER, status);
    }

    @Override
    public List<InsuranceClaim> findByDamageReport(int damageReportId) {
        String sql = "SELECT * FROM insurance_claims WHERE damage_report_id = ? ORDER BY submitted_date DESC";
        return queryList(sql, MAPPER, damageReportId);
    }

    @Override
    public void updateStatus(int claimId, String newStatus) {
        String sql = "UPDATE insurance_claims SET status = ? WHERE claim_id = ?";
        executeUpdate(sql, newStatus, claimId);
    }

    @Override
    public void delete(int claimId) {
        String sql = "DELETE FROM insurance_claims WHERE claim_id = ?";
        executeUpdate(sql, claimId);
    }
}
