package com.vehiclerental.dao.impl;

import com.vehiclerental.dao.AbstractJdbcDao;
import com.vehiclerental.dao.VehicleDao;
import com.vehiclerental.dao.impl.rowmapper.VehicleRowMapper;
import com.vehiclerental.exception.DataAccessException;
import com.vehiclerental.model.Vehicle;
import com.vehiclerental.model.VehicleImage;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.datasource.DataSourceUtils;
import org.springframework.stereotype.Repository;

import javax.sql.DataSource;
import java.sql.*;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Repository
public class VehicleDaoImpl extends AbstractJdbcDao<Vehicle, Integer> implements VehicleDao {

    /**
     * Every column except image_data / image_type. The photo is a LONGBLOB, so
     * "SELECT *" would drag every image out of the database on every fleet
     * listing. It is fetched on its own by findImage() instead.
     */
    private static final String COLS =
        "vehicle_id, branch_id, plate_number, model, category, manufacture_year, "
      + "rental_price_per_day, passenger_capacity, fuel_type, image_url, status, mileage";

    /** The same list, qualified with a table alias. */
    private static String cols(String alias) {
        StringBuilder sb = new StringBuilder();
        for (String column : COLS.split(",")) {
            if (sb.length() > 0) sb.append(", ");
            sb.append(alias).append('.').append(column.trim());
        }
        return sb.toString();
    }

    private final VehicleRowMapper mapper = new VehicleRowMapper();

    /**
     * Whether a vehicle must have insurance covering the whole rental before it
     * can be offered. True in any real deployment; the switch exists so a demo
     * database without policies can still be browsed.
     */
    private final boolean requireInsurance;

    public VehicleDaoImpl(DataSource dataSource,
                          @Value("${rental.require-insurance:true}") boolean requireInsurance) {
        super(dataSource);
        this.requireInsurance = requireInsurance;
    }

    @Override
    public Vehicle save(Vehicle v) {
        String sql = "INSERT INTO vehicles (branch_id, plate_number, model, category, manufacture_year, " +
                     "rental_price_per_day, passenger_capacity, fuel_type, image_url, status, mileage) " +
                     "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
        int newId = executeInsertReturnId(sql,
            v.getBranchId(), v.getPlateNumber(), v.getModel(), v.getCategory(), v.getManufactureYear(),
            v.getRentalPricePerDay(), v.getPassengerCapacity(), v.getFuelType(), v.getImageUrl(),
            v.getStatus().name(), v.getMileage());
        v.setVehicleId(newId);
        return v;
    }

    @Override
    public Optional<Vehicle> findById(int vehicleId) {
        String sql = "SELECT " + COLS + " FROM vehicles WHERE vehicle_id = ?";
        return queryOne(sql, mapper, vehicleId);
    }

    @Override
    public Optional<Vehicle> findByPlate(String plateNumber) {
        String sql = "SELECT " + COLS + " FROM vehicles WHERE plate_number = ?";
        return queryOne(sql, mapper, plateNumber);
    }

    @Override
    public List<Vehicle> findAll() {
        return queryList("SELECT " + COLS + " FROM vehicles ORDER BY vehicle_id", mapper);
    }

    @Override
    public List<Vehicle> findByBranch(int branchId) {
        return queryList("SELECT " + COLS + " FROM vehicles WHERE branch_id = ? ORDER BY vehicle_id", mapper, branchId);
    }

    @Override
    public List<Vehicle> findByStatus(String status) {
        return queryList("SELECT " + COLS + " FROM vehicles WHERE status = ? ORDER BY vehicle_id", mapper, status);
    }

    /**
     * The public catalogue (E3): every vehicle a customer could book at some
     * point - on the road, at an open branch, with no serious damage waiting,
     * and (when insurance is required) a live policy that has not yet expired.
     */
    @Override
    public List<Vehicle> findCatalogue(LocalDate today) {
        StringBuilder sql = new StringBuilder()
            .append("SELECT ").append(cols("v")).append(" FROM vehicles v ")
            .append("JOIN branches br ON br.branch_id = v.branch_id ")
            .append("WHERE v.status IN ('AVAILABLE', 'RESERVED', 'RENTED') AND br.status = 'ACTIVE' ")
            .append("AND NOT EXISTS ( SELECT 1 FROM damage_reports d WHERE d.vehicle_id = v.vehicle_id ")
            .append("    AND d.status = 'UNDER_REVIEW' AND d.damage_severity IN ('MODERATE','SEVERE') ) ");
        List<Object> params = new ArrayList<>();
        if (requireInsurance) {
            sql.append("AND EXISTS ( SELECT 1 FROM insurance_policies p WHERE p.vehicle_id = v.vehicle_id ")
               .append("    AND p.status = 'ACTIVE' AND p.expiry_date >= ? ) ");
            params.add(Date.valueOf(today));
        }
        sql.append("ORDER BY v.rental_price_per_day, v.vehicle_id");
        return queryList(sql.toString(), mapper, params.toArray());
    }

    @Override
    public List<Vehicle> findAvailable(LocalDate pickup, LocalDate returnDate) {
        return findAvailable(pickup, returnDate, null);
    }

    /**
     * A vehicle can be offered for [pickup, returnDate] when ALL of these hold:
     *
     *   - it is physically on the road (not in the workshop, not written off).
     *     A car that is RENTED or RESERVED today is still offered for a future
     *     window it is free for — that is the whole point of advance booking.
     *   - its home branch is open for business
     *   - no live booking of that vehicle overlaps the window
     *   - no scheduled or in-progress maintenance overlaps the window
     *   - no unresolved serious damage report is open against it
     *   - no pending branch transfer lands on or before the end of the window
     *   - an ACTIVE insurance policy covers the whole window
     *
     * Overlap rule: two inclusive ranges A and B overlap when
     * A.start <= B.end AND B.start <= A.end.
     */
    @Override
    public List<Vehicle> findAvailable(LocalDate pickup, LocalDate returnDate, Integer branchId) {

        StringBuilder sql = new StringBuilder()
            .append("SELECT ").append(cols("v")).append(" FROM vehicles v ")
            .append("JOIN branches br ON br.branch_id = v.branch_id ")
            .append("WHERE v.status IN ('AVAILABLE', 'RESERVED', 'RENTED') ")
            .append("AND br.status = 'ACTIVE' ");

        List<Object> params = new ArrayList<>();

        if (branchId != null) {
            sql.append("AND v.branch_id = ? ");
            params.add(branchId);
        }

        // no overlapping live booking
        sql.append("AND NOT EXISTS ( SELECT 1 FROM bookings b ")
           .append("    WHERE b.vehicle_id = v.vehicle_id ")
           .append("    AND b.status IN ('PENDING_APPROVAL','APPROVED','ACTIVE_RENTAL') ")
           .append("    AND b.pickup_date <= ? AND ? <= b.return_date ) ");
        params.add(Date.valueOf(returnDate));
        params.add(Date.valueOf(pickup));

        // no overlapping workshop window
        sql.append("AND NOT EXISTS ( SELECT 1 FROM maintenance_records m ")
           .append("    WHERE m.vehicle_id = v.vehicle_id ")
           .append("    AND m.status IN ('SCHEDULED','IN_PROGRESS') ")
           .append("    AND m.service_date <= ? ")
           .append("    AND ? <= COALESCE(m.expected_end_date, m.service_date) ) ");
        params.add(Date.valueOf(returnDate));
        params.add(Date.valueOf(pickup));

        // no open serious damage
        sql.append("AND NOT EXISTS ( SELECT 1 FROM damage_reports d ")
           .append("    WHERE d.vehicle_id = v.vehicle_id ")
           .append("    AND d.status = 'UNDER_REVIEW' ")
           .append("    AND d.damage_severity IN ('MODERATE','SEVERE') ) ");

        // no pending transfer that happens before the rental ends
        sql.append("AND NOT EXISTS ( SELECT 1 FROM vehicle_transfers t ")
           .append("    WHERE t.vehicle_id = v.vehicle_id ")
           .append("    AND t.status = 'PENDING' AND t.transfer_date <= ? ) ");
        params.add(Date.valueOf(returnDate));

        if (requireInsurance) {
            sql.append("AND EXISTS ( SELECT 1 FROM insurance_policies p ")
               .append("    WHERE p.vehicle_id = v.vehicle_id ")
               .append("    AND p.status = 'ACTIVE' ")
               .append("    AND p.start_date <= ? AND p.expiry_date >= ? ) ");
            params.add(Date.valueOf(pickup));
            params.add(Date.valueOf(returnDate));
        }

        sql.append("ORDER BY v.vehicle_id");

        return queryList(sql.toString(), mapper, params.toArray());
    }

    @Override
    public void update(Vehicle v) {
        String sql = "UPDATE vehicles SET branch_id=?, plate_number=?, model=?, category=?, " +
                     "manufacture_year=?, rental_price_per_day=?, passenger_capacity=?, fuel_type=?, " +
                     "image_url=?, status=?, mileage=? WHERE vehicle_id=?";
        executeUpdate(sql,
            v.getBranchId(), v.getPlateNumber(), v.getModel(), v.getCategory(), v.getManufactureYear(),
            v.getRentalPricePerDay(), v.getPassengerCapacity(), v.getFuelType(), v.getImageUrl(),
            v.getStatus().name(), v.getMileage(), v.getVehicleId());
    }

    @Override
    public void updateStatus(int vehicleId, String status) {
        executeUpdate("UPDATE vehicles SET status = ? WHERE vehicle_id = ?", status, vehicleId);
    }

    @Override
    public void updateImage(int vehicleId, byte[] data, String contentType, String imageUrl) {
        executeUpdate("UPDATE vehicles SET image_data = ?, image_type = ?, image_url = ? "
                    + "WHERE vehicle_id = ?", data, contentType, imageUrl, vehicleId);
    }

    @Override
    public Optional<VehicleImage> findImage(int vehicleId) {
        String sql = "SELECT image_data, image_type FROM vehicles WHERE vehicle_id = ?";
        Connection conn = DataSourceUtils.getConnection(dataSource);
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, vehicleId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) return Optional.empty();
                byte[] data = rs.getBytes("image_data");
                if (data == null || data.length == 0) return Optional.empty();
                return Optional.of(new VehicleImage(data, rs.getString("image_type")));
            }
        } catch (SQLException e) {
            throw new DataAccessException("SELECT failed: " + sql, e);
        } finally {
            DataSourceUtils.releaseConnection(conn, dataSource);
        }
    }

    @Override
    public void updateBranch(int vehicleId, int branchId) {
        executeUpdate("UPDATE vehicles SET branch_id = ? WHERE vehicle_id = ?", branchId, vehicleId);
    }

    @Override
    public void delete(int vehicleId) {
        // Only a vehicle with no history is ever deleted (see VehicleServiceImpl);
        // its extra photos and customers' saved-car entries go with it.
        executeUpdate("DELETE FROM vehicle_images WHERE vehicle_id = ?", vehicleId);
        executeUpdate("DELETE FROM favourites WHERE vehicle_id = ?", vehicleId);
        executeUpdate("DELETE FROM vehicles WHERE vehicle_id = ?", vehicleId);
    }

    /**
     * Locks the vehicle's row until the surrounding transaction ends. Every
     * booking change for one car takes this lock first, so two requests for
     * the same car are checked one after the other, never side by side.
     */
    @Override
    public void lockForUpdate(int vehicleId) {
        queryRows("SELECT vehicle_id FROM vehicles WHERE vehicle_id = ? FOR UPDATE", rs -> rs.getInt(1), vehicleId);
    }
}
