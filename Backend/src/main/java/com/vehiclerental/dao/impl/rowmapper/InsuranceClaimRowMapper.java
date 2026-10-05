package com.vehiclerental.dao.impl.rowmapper;

import com.vehiclerental.dao.RowMapper;
import com.vehiclerental.enums.ClaimStatus;
import com.vehiclerental.model.InsuranceClaim;

import java.math.BigDecimal;
import java.sql.Date;
import java.sql.ResultSet;
import java.sql.SQLException;

public class InsuranceClaimRowMapper implements RowMapper<InsuranceClaim> {

    @Override
    public InsuranceClaim map(ResultSet rs) throws SQLException {
        InsuranceClaim c = new InsuranceClaim();
        c.setClaimId(rs.getInt("claim_id"));
        c.setDamageReportId(rs.getInt("damage_report_id"));
        c.setInsuranceProvider(rs.getString("insurance_provider"));

        BigDecimal amount = rs.getBigDecimal("claim_amount");
        if (amount != null) {
            c.setClaimAmount(amount);
        }

        Date submitted = rs.getDate("submitted_date");
        if (submitted != null) {
            c.setSubmittedDate(submitted.toLocalDate());
        }

        c.setStatus(ClaimStatus.valueOf(rs.getString("status")));
        return c;
    }
}
