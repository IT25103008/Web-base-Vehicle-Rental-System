package com.vehiclerental.dao.impl.rowmapper;

import com.vehiclerental.dao.RowMapper;
import com.vehiclerental.enums.TransferStatus;
import com.vehiclerental.model.VehicleTransfer;

import java.sql.ResultSet;
import java.sql.SQLException;

public class VehicleTransferRowMapper implements RowMapper<VehicleTransfer> {

    @Override
    public VehicleTransfer map(ResultSet rs) throws SQLException {
        VehicleTransfer t = new VehicleTransfer();
        t.setTransferId(rs.getInt("transfer_id"));
        t.setVehicleId(rs.getInt("vehicle_id"));
        t.setFromBranchId(rs.getInt("from_branch_id"));
        t.setToBranchId(rs.getInt("to_branch_id"));
        java.sql.Date d = rs.getDate("transfer_date");
        if (d != null) t.setTransferDate(d.toLocalDate());
        t.setStatus(TransferStatus.valueOf(rs.getString("status")));
        t.setReason(rs.getString("reason"));
        return t;
    }
}
