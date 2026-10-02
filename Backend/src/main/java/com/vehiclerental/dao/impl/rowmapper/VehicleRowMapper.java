package com.vehiclerental.dao.impl.rowmapper;

import com.vehiclerental.dao.RowMapper;
import com.vehiclerental.enums.VehicleStatus;
import com.vehiclerental.model.Vehicle;

import java.sql.ResultSet;
import java.sql.SQLException;

public class VehicleRowMapper implements RowMapper<Vehicle> {

    @Override
    public Vehicle map(ResultSet rs) throws SQLException {
        Vehicle v = new Vehicle();
        v.setVehicleId(rs.getInt("vehicle_id"));
        v.setBranchId(rs.getInt("branch_id"));
        v.setPlateNumber(rs.getString("plate_number"));
        v.setModel(rs.getString("model"));
        v.setCategory(rs.getString("category"));

        int year = rs.getInt("manufacture_year");
        if (!rs.wasNull()) v.setManufactureYear(year);

        v.setRentalPricePerDay(rs.getBigDecimal("rental_price_per_day"));

        int cap = rs.getInt("passenger_capacity");
        if (!rs.wasNull()) v.setPassengerCapacity(cap);

        v.setFuelType(rs.getString("fuel_type"));
        v.setImageUrl(rs.getString("image_url"));
        v.setStatus(VehicleStatus.valueOf(rs.getString("status")));
        v.setMileage(rs.getInt("mileage"));
        return v;
    }
}
