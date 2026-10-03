package com.vehiclerental.dao.impl.rowmapper;

import com.vehiclerental.dao.RowMapper;
import com.vehiclerental.model.Branch;

import java.sql.ResultSet;
import java.sql.SQLException;

public class BranchRowMapper implements RowMapper<Branch> {

    @Override
    public Branch map(ResultSet rs) throws SQLException {
        Branch b = new Branch();
        b.setBranchId(rs.getInt("branch_id"));
        b.setName(rs.getString("name"));
        b.setStreet(rs.getString("street"));
        b.setCity(rs.getString("city"));
        b.setDistrict(rs.getString("district"));
        b.setContactNumber(rs.getString("contact_number"));

        java.sql.Time open = rs.getTime("open_time");
        if (open != null) b.setOpenTime(open.toLocalTime());

        java.sql.Time close = rs.getTime("close_time");
        if (close != null) b.setCloseTime(close.toLocalTime());

        b.setStatus(rs.getString("status"));
        return b;
    }
}
