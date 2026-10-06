package com.vehiclerental.dao;

import com.vehiclerental.model.Handover;

import java.util.List;
import java.util.Optional;

public interface HandoverDao {
    Handover save(Handover h);
    Optional<Handover> findById(int id);
    List<Handover> findByBooking(int bookingId);
    Optional<Handover> findByBookingAndType(int bookingId, String handoverType);
    List<Handover> findActive();       // handovers of bookings currently ACTIVE_RENTAL
}
