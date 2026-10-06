package com.vehiclerental.dao.impl.rowmapper;

import com.vehiclerental.dao.RowMapper;
import com.vehiclerental.model.InsurancePolicy;

import java.sql.Date;
import java.sql.ResultSet;
import java.sql.SQLException;

public class InsurancePolicyRowMapper implements RowMapper<InsurancePolicy> {

    @Override
    public InsurancePolicy map(ResultSet rs) throws SQLException {
        InsurancePolicy p = new InsurancePolicy();
        p.setPolicyId(rs.getInt("policy_id"));
        p.setVehicleId(rs.getInt("vehicle_id"));
        p.setPolicyNumber(rs.getString("policy_number"));
        p.setProvider(rs.getString("provider"));

        Date start = rs.getDate("start_date");
        if (start != null) {
            p.setStartDate(start.toLocalDate());
        }
        Date expiry = rs.getDate("expiry_date");
        if (expiry != null) {
            p.setExpiryDate(expiry.toLocalDate());
        }

        p.setStatus(rs.getString("status"));
        return p;
    }
}
