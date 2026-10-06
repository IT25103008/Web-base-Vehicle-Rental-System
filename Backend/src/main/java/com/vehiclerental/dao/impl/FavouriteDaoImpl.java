package com.vehiclerental.dao.impl;

import com.vehiclerental.dao.AbstractJdbcDao;
import com.vehiclerental.dao.FavouriteDao;
import com.vehiclerental.util.AppClock;
import org.springframework.stereotype.Repository;

import javax.sql.DataSource;
import java.sql.Timestamp;
import java.util.List;

@Repository
public class FavouriteDaoImpl extends AbstractJdbcDao<Integer, Integer> implements FavouriteDao {

    public FavouriteDaoImpl(DataSource dataSource) {
        super(dataSource);
    }

    @Override
    public List<Integer> findVehicleIds(int userId) {
        return queryList("SELECT vehicle_id FROM favourites WHERE user_id = ? ORDER BY created_at DESC",
                         rs -> rs.getInt(1), userId);
    }

    @Override
    public boolean add(int userId, int vehicleId) {
        if (queryCount("SELECT COUNT(*) FROM favourites WHERE user_id = ? AND vehicle_id = ?", userId, vehicleId) > 0) {
            return false;
        }
        executeUpdate("INSERT INTO favourites (user_id, vehicle_id, created_at) VALUES (?, ?, ?)",
                      userId, vehicleId, Timestamp.valueOf(AppClock.now()));
        return true;
    }

    @Override
    public void remove(int userId, int vehicleId) {
        executeUpdate("DELETE FROM favourites WHERE user_id = ? AND vehicle_id = ?", userId, vehicleId);
    }
}
