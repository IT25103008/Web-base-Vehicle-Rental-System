package com.vehiclerental.dao;

import com.vehiclerental.model.Handover;
import java.util.List;
import java.util.Optional;

public interface HandoverDao {
    Handover save(Handover h);
    Optional<Handover> findById(int id);
    List<Handover> findByBooking(int bookingId);
    java.util.Optional<Handover> findByBookingAndType(int bookingId, String handoverType);
    void deleteByBooking(int bookingId);   // used only when a whole booking is deleted
    List<Handover> findActive();       // handovers of bookings currently ACTIVE_RENTAL
}
