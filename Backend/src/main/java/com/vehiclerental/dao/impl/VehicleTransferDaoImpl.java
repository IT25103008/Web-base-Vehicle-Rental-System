package com.vehiclerental.dao.impl;

import com.vehiclerental.dao.AbstractJdbcDao;
import com.vehiclerental.dao.VehicleTransferDao;
import com.vehiclerental.dao.impl.rowmapper.VehicleTransferRowMapper;
import com.vehiclerental.model.VehicleTransfer;
import org.springframework.stereotype.Repository;

import javax.sql.DataSource;
import java.sql.Date;
import java.util.List;
import java.util.Optional;

@Repository
public class VehicleTransferDaoImpl extends AbstractJdbcDao<VehicleTransfer, Integer>
                                    implements VehicleTransferDao {

    private final VehicleTransferRowMapper mapper = new VehicleTransferRowMapper();

    public VehicleTransferDaoImpl(DataSource dataSource) {
        super(dataSource);
    }

    @Override
    public VehicleTransfer save(VehicleTransfer t) {
        String sql = "INSERT INTO vehicle_transfers (vehicle_id, from_branch_id, to_branch_id, " +
                     "transfer_date, status, reason) VALUES (?, ?, ?, ?, ?, ?)";
        int id = executeInsertReturnId(sql,
            t.getVehicleId(), t.getFromBranchId(), t.getToBranchId(),
            Date.valueOf(t.getTransferDate()), t.getStatus().name(), t.getReason());
        t.setTransferId(id);
        return t;
    }

    @Override
    public Optional<VehicleTransfer> findById(int transferId) {
        String sql = "SELECT * FROM vehicle_transfers WHERE transfer_id = ?";
        return queryOne(sql, mapper, transferId);
    }

    @Override
    public List<VehicleTransfer> findAll() {
        String sql = "SELECT * FROM vehicle_transfers ORDER BY transfer_date DESC";
        return queryList(sql, mapper);
    }

    @Override
    public List<VehicleTransfer> findPending() {
        String sql = "SELECT * FROM vehicle_transfers WHERE status = 'PENDING' ORDER BY transfer_date";
        return queryList(sql, mapper);
    }

    @Override
    public void updateStatus(int transferId, String status) {
        String sql = "UPDATE vehicle_transfers SET status = ? WHERE transfer_id = ?";
        executeUpdate(sql, status, transferId);
    }

    @Override
    public List<VehicleTransfer> findPendingByVehicle(int vehicleId) {
        String sql = "SELECT * FROM vehicle_transfers WHERE vehicle_id = ? AND status = 'PENDING' " +
                     "ORDER BY transfer_date";
        return queryList(sql, mapper, vehicleId);
    }
}
