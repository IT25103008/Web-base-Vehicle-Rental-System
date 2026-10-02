package com.vehiclerental.dao.impl;

import com.vehiclerental.dao.AbstractJdbcDao;
import com.vehiclerental.dao.VehicleImageDao;
import com.vehiclerental.model.VehicleImage;
import com.vehiclerental.util.AppClock;
import org.springframework.stereotype.Repository;

import javax.sql.DataSource;
import java.sql.Timestamp;
import java.util.List;
import java.util.Optional;

@Repository
public class VehicleImageDaoImpl extends AbstractJdbcDao<VehicleImageDao.Entry, Integer> implements VehicleImageDao {

    public VehicleImageDaoImpl(DataSource dataSource) {
        super(dataSource);
    }

    // The bytes are only read when one photo is actually asked for.
    @Override
    public List<Entry> findByVehicle(int vehicleId) {
        return queryList("SELECT image_id, vehicle_id, content_type, sort_order FROM vehicle_images "
                       + "WHERE vehicle_id = ? ORDER BY sort_order, image_id",
            rs -> new Entry(rs.getInt("image_id"), rs.getInt("vehicle_id"),
                            rs.getString("content_type"), rs.getInt("sort_order")), vehicleId);
    }

    @Override
    public Optional<VehicleImage> findData(int vehicleId, int imageId) {
        List<VehicleImage> rows = queryRows("SELECT image_data, content_type FROM vehicle_images "
                                          + "WHERE vehicle_id = ? AND image_id = ?",
            rs -> new VehicleImage(rs.getBytes("image_data"), rs.getString("content_type")), vehicleId, imageId);
        return rows.isEmpty() ? Optional.empty() : Optional.of(rows.get(0));
    }

    @Override
    public int add(int vehicleId, byte[] data, String contentType) {
        int next = (int) queryCount("SELECT COALESCE(MAX(sort_order), 0) + 1 FROM vehicle_images WHERE vehicle_id = ?",
                                    vehicleId);
        return executeInsertReturnId("INSERT INTO vehicle_images (vehicle_id, image_data, content_type, sort_order, created_at) "
                                   + "VALUES (?, ?, ?, ?, ?)",
                                     vehicleId, data, contentType, next, Timestamp.valueOf(AppClock.now()));
    }

    @Override
    public void delete(int vehicleId, int imageId) {
        executeUpdate("DELETE FROM vehicle_images WHERE vehicle_id = ? AND image_id = ?", vehicleId, imageId);
    }

    @Override
    public int countByVehicle(int vehicleId) {
        return (int) queryCount("SELECT COUNT(*) FROM vehicle_images WHERE vehicle_id = ?", vehicleId);
    }
}
