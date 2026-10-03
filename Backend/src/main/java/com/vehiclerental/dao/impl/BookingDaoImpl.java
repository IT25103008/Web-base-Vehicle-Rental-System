package com.vehiclerental.dao.impl;

import com.vehiclerental.dao.AbstractJdbcDao;
import com.vehiclerental.dao.BookingDao;
import com.vehiclerental.dao.impl.rowmapper.BookingRowMapper;
import com.vehiclerental.model.Booking;
import com.vehiclerental.util.AppClock;
import org.springframework.stereotype.Repository;

import javax.sql.DataSource;
import java.math.BigDecimal;
import java.sql.Date;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public class BookingDaoImpl extends AbstractJdbcDao<Booking, Integer> implements BookingDao {

    /** Statuses that still hold the vehicle. Anything else has released it. */
    private static final String LIVE_STATUSES = "('PENDING_APPROVAL','APPROVED','ACTIVE_RENTAL')";

    private final BookingRowMapper mapper = new BookingRowMapper();

    public BookingDaoImpl(DataSource dataSource) {
        super(dataSource);
    }

    @Override
    public Booking save(Booking b) {
        // submitted_date is passed explicitly so every timestamp in the database
        // comes from the application clock (see AppClock).
        String sql = "INSERT INTO bookings (customer_id, vehicle_id, pickup_branch_id, approved_by, " +
                     "pickup_date, return_date, special_requests, estimated_cost, status, submitted_date) " +
                     "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";

        Timestamp submitted = Timestamp.valueOf(
            b.getSubmittedDate() != null ? b.getSubmittedDate() : AppClock.now());

        int newId = executeInsertReturnId(sql,
            b.getCustomerId(),
            b.getVehicleId(),
            b.getPickupBranchId(),
            b.getApprovedBy(),
            Date.valueOf(b.getPickupDate()),
            Date.valueOf(b.getReturnDate()),
            b.getSpecialRequests(),
            b.getEstimatedCost(),
            b.getStatus().name(),
            submitted);

        b.setBookingId(newId);
        return b;
    }

    @Override
    public Optional<Booking> findById(int bookingId) {
        return queryOne("SELECT * FROM bookings WHERE booking_id = ?", mapper, bookingId);
    }

    @Override
    public List<Booking> findAll() {
        return queryList("SELECT * FROM bookings ORDER BY submitted_date DESC", mapper);
    }

    @Override
    public List<Booking> findByCustomer(int customerId) {
        return queryList("SELECT * FROM bookings WHERE customer_id = ? ORDER BY submitted_date DESC",
                         mapper, customerId);
    }

    @Override
    public List<Booking> findByStatus(String status) {
        return queryList("SELECT * FROM bookings WHERE status = ? ORDER BY submitted_date DESC",
                         mapper, status);
    }

    @Override
    public List<Booking> findByVehicle(int vehicleId) {
        return queryList("SELECT * FROM bookings WHERE vehicle_id = ? ORDER BY pickup_date DESC",
                         mapper, vehicleId);
    }

    @Override
    public List<Booking> findOverlapping(int vehicleId, LocalDate pickup, LocalDate returnDate) {
        return findOverlapping(vehicleId, pickup, returnDate, null);
    }

    @Override
    public List<Booking> findOverlapping(int vehicleId, LocalDate pickup, LocalDate returnDate,
                                         Integer excludeBookingId) {
        // Two inclusive ranges overlap when a.start <= b.end AND b.start <= a.end.
        String sql =
            "SELECT * FROM bookings " +
            "WHERE vehicle_id = ? " +
            "AND status IN " + LIVE_STATUSES + " " +
            "AND pickup_date <= ? AND ? <= return_date " +
            (excludeBookingId != null ? "AND booking_id <> ? " : "") +
            "ORDER BY pickup_date";

        if (excludeBookingId != null) {
            return queryList(sql, mapper, vehicleId,
                             Date.valueOf(returnDate), Date.valueOf(pickup), excludeBookingId);
        }
        return queryList(sql, mapper, vehicleId, Date.valueOf(returnDate), Date.valueOf(pickup));
    }

    @Override
    public List<Booking> findOverlappingForCustomer(int customerId, LocalDate pickup, LocalDate returnDate,
                                                    Integer excludeBookingId) {
        String sql =
            "SELECT * FROM bookings " +
            "WHERE customer_id = ? " +
            "AND status IN " + LIVE_STATUSES + " " +
            "AND pickup_date <= ? AND ? <= return_date " +
            (excludeBookingId != null ? "AND booking_id <> ? " : "") +
            "ORDER BY pickup_date";

        if (excludeBookingId != null) {
            return queryList(sql, mapper, customerId,
                             Date.valueOf(returnDate), Date.valueOf(pickup), excludeBookingId);
        }
        return queryList(sql, mapper, customerId, Date.valueOf(returnDate), Date.valueOf(pickup));
    }

    @Override
    public int countLiveByCustomer(int customerId) {
        // Reuses queryList so we stay inside the shared JDBC helpers.
        String sql = "SELECT * FROM bookings WHERE customer_id = ? AND status IN " + LIVE_STATUSES;
        return queryList(sql, mapper, customerId).size();
    }

    @Override
    public List<Booking> findLiveByVehicle(int vehicleId) {
        String sql = "SELECT * FROM bookings WHERE vehicle_id = ? AND status IN " + LIVE_STATUSES +
                     " ORDER BY pickup_date";
        return queryList(sql, mapper, vehicleId);
    }

    @Override
    public List<Booking> findLiveByVehicleInWindow(int vehicleId, LocalDate from, LocalDate to) {
        return findOverlapping(vehicleId, from, to, null);
    }

    @Override
    public List<Booking> findOverdueReturns(LocalDate asOf) {
        String sql = "SELECT * FROM bookings WHERE status = 'ACTIVE_RENTAL' AND return_date < ? " +
                     "ORDER BY return_date";
        return queryList(sql, mapper, Date.valueOf(asOf));
    }

    @Override
    public List<Booking> findMissedPickups(LocalDate asOf) {
        String sql = "SELECT * FROM bookings WHERE status = 'APPROVED' AND pickup_date < ? " +
                     "ORDER BY pickup_date";
        return queryList(sql, mapper, Date.valueOf(asOf));
    }

    @Override
    public List<Booking> findReturnsDueOn(LocalDate day) {
        String sql = "SELECT * FROM bookings WHERE status = 'ACTIVE_RENTAL' AND return_date = ?";
        return queryList(sql, mapper, Date.valueOf(day));
    }

    @Override
    public List<Booking> findApprovedPickupsDueBy(int vehicleId, LocalDate day) {
        String sql = "SELECT * FROM bookings WHERE vehicle_id = ? AND status = 'APPROVED' " +
                     "AND pickup_date <= ? ORDER BY pickup_date";
        return queryList(sql, mapper, vehicleId, Date.valueOf(day));
    }

    @Override
    public void updateStatus(int bookingId, String newStatus) {
        executeUpdate("UPDATE bookings SET status = ? WHERE booking_id = ?", newStatus, bookingId);
    }

    @Override
    public void updateApprover(int bookingId, Integer approverId) {
        executeUpdate("UPDATE bookings SET approved_by = ? WHERE booking_id = ?", approverId, bookingId);
    }

    @Override
    public void updateDates(int bookingId, LocalDate pickup, LocalDate returnDate, BigDecimal newCost) {
        String sql = "UPDATE bookings SET pickup_date = ?, return_date = ?, estimated_cost = ? " +
                     "WHERE booking_id = ?";
        executeUpdate(sql, Date.valueOf(pickup), Date.valueOf(returnDate), newCost, bookingId);
    }

    @Override
    public void updateReturnOutcome(int bookingId, LocalDate actualReturnDate, BigDecimal finalCost) {
        String sql = "UPDATE bookings SET actual_return_date = ?, final_cost = ? WHERE booking_id = ?";
        executeUpdate(sql, actualReturnDate == null ? null : Date.valueOf(actualReturnDate),
                      finalCost, bookingId);
    }

    @Override
    public void updateFinalCost(int bookingId, BigDecimal finalCost) {
        executeUpdate("UPDATE bookings SET final_cost = ? WHERE booking_id = ?", finalCost, bookingId);
    }

    // ------------------------------------------------------------
    // Paging for the staff console (C1). Searches the customer's name and
    // email, the vehicle's model and plate, and the booking number.
    // ------------------------------------------------------------
    @Override
    public List<Booking> findPage(String status, Integer branchId, String text, int offset, int limit) {
        StringBuilder sql = new StringBuilder("SELECT b.* FROM bookings b "
            + "JOIN users u ON u.user_id = b.customer_id JOIN vehicles v ON v.vehicle_id = b.vehicle_id WHERE 1=1 ");
        List<Object> params = new java.util.ArrayList<>();
        pageFilter(sql, params, status, branchId, text);
        sql.append("ORDER BY b.submitted_date DESC, b.booking_id DESC LIMIT ? OFFSET ?");
        params.add(limit);
        params.add(offset);
        return queryList(sql.toString(), mapper, params.toArray());
    }

    @Override
    public java.util.Map<String, Long> countByStatus(Integer branchId, String text) {
        StringBuilder sql = new StringBuilder("SELECT b.status, COUNT(*) FROM bookings b "
            + "JOIN users u ON u.user_id = b.customer_id JOIN vehicles v ON v.vehicle_id = b.vehicle_id WHERE 1=1 ");
        List<Object> params = new java.util.ArrayList<>();
        pageFilter(sql, params, null, branchId, text);
        sql.append("GROUP BY b.status");
        java.util.Map<String, Long> out = new java.util.LinkedHashMap<>();
        queryRows(sql.toString(), rs -> {
            out.put(rs.getString(1), rs.getLong(2));
            return null;
        }, params.toArray());
        return out;
    }

    private static void pageFilter(StringBuilder sql, List<Object> params, String status, Integer branchId, String text) {
        if (status != null && !status.isBlank() && !"all".equalsIgnoreCase(status)) {
            sql.append("AND b.status = ? ");
            params.add(status);
        }
        if (branchId != null) {
            sql.append("AND b.pickup_branch_id = ? ");
            params.add(branchId);
        }
        if (text != null && !text.isBlank()) {
            String q = text.trim().toLowerCase();
            String like = "%" + q + "%";
            sql.append("AND (CAST(b.booking_id AS CHAR) = ? OR LOWER(u.email) LIKE ? "
                     + "OR LOWER(CONCAT(u.first_name, ' ', u.last_name)) LIKE ? "
                     + "OR LOWER(v.model) LIKE ? OR LOWER(v.plate_number) LIKE ?) ");
            params.add(q.replace("#", ""));
            params.add(like);
            params.add(like);
            params.add(like);
            params.add(like);
        }
    }

    /** Live bookings touching [from, to], for the fleet timeline (C2). */
    @Override
    public List<Booking> findLiveInWindow(LocalDate from, LocalDate to, Integer branchId) {
        StringBuilder sql = new StringBuilder("SELECT * FROM bookings WHERE status IN " + LIVE_STATUSES
            + " AND pickup_date <= ? AND return_date >= ? ");
        List<Object> params = new java.util.ArrayList<>();
        params.add(Date.valueOf(to));
        params.add(Date.valueOf(from));
        if (branchId != null) {
            sql.append("AND pickup_branch_id = ? ");
            params.add(branchId);
        }
        sql.append("ORDER BY pickup_date");
        return queryList(sql.toString(), mapper, params.toArray());
    }

    /** Busy ranges for one car from a date on - public, so no customer details (E1). */
    @Override
    public List<Booking> findLiveByVehicleFrom(int vehicleId, LocalDate from) {
        String sql = "SELECT * FROM bookings WHERE vehicle_id = ? AND status IN " + LIVE_STATUSES
                   + " AND return_date >= ? ORDER BY pickup_date";
        return queryList(sql, mapper, vehicleId, Date.valueOf(from));
    }
}
