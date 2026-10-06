package com.vehiclerental.dao.impl;

import com.vehiclerental.dao.AbstractJdbcDao;
import com.vehiclerental.dao.HandoverDao;
import com.vehiclerental.dao.impl.rowmapper.HandoverRowMapper;
import com.vehiclerental.model.Handover;
import com.vehiclerental.util.AppClock;
import org.springframework.stereotype.Repository;

import javax.sql.DataSource;
import java.sql.Timestamp;
import java.util.List;
import java.util.Optional;

@Repository
public class HandoverDaoImpl extends AbstractJdbcDao<Handover, Integer> implements HandoverDao {

    private final HandoverRowMapper mapper = new HandoverRowMapper();

    public HandoverDaoImpl(DataSource dataSource) {
        super(dataSource);
    }

    @Override
    public Handover save(Handover h) {
        String sql = "INSERT INTO handovers (booking_id, handover_type, processed_by_staff_id, " +
                     "mileage_at_event, fuel_level, condition_notes, status, handover_date) " +
                     "VALUES (?, ?, ?, ?, ?, ?, ?, ?)";
        int id = executeInsertReturnId(sql,
            h.getBookingId(), h.getHandoverType().name(), h.getProcessedByStaffId(),
            h.getMileageAtEvent(), h.getFuelLevel(), h.getConditionNotes(),
            h.getStatus() == null ? "COMPLETED" : h.getStatus().name(),
            Timestamp.valueOf(h.getHandoverDate() != null ? h.getHandoverDate() : AppClock.now()));
        h.setHandoverId(id);
        return h;
    }

    @Override
    public Optional<Handover> findById(int id) {
        return queryOne("SELECT * FROM handovers WHERE handover_id = ?", mapper, id);
    }

    @Override
    public List<Handover> findByBooking(int bookingId) {
        return queryList("SELECT * FROM handovers WHERE booking_id = ? ORDER BY handover_date",
                         mapper, bookingId);
    }

    @Override
    public List<Handover> findActive() {
        String sql = "SELECT h.* FROM handovers h " +
                     "JOIN bookings b ON b.booking_id = h.booking_id " +
                     "WHERE b.status = 'ACTIVE_RENTAL' " +
                     "ORDER BY h.handover_date DESC";
        return queryList(sql, mapper);
    }

    @Override
    public Optional<Handover> findByBookingAndType(int bookingId, String handoverType) {
        return queryOne("SELECT * FROM handovers WHERE booking_id = ? AND handover_type = ?",
                        mapper, bookingId, handoverType);
    }
}
