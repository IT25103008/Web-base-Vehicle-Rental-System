package com.vehiclerental.service;

import com.vehiclerental.dto.request.PickupRequest;
import com.vehiclerental.dto.request.ReturnVehicleRequest;
import com.vehiclerental.model.Handover;

import java.util.List;

public interface HandoverService {

    /** Hand the keys over: every gate (payment, licence, branch, condition) is checked here. */
    Handover confirmPickup(int bookingId, int staffId, PickupRequest request);

    /** Take the vehicle back, price the trip, and deal with any damage. */
    Handover recordReturn(int bookingId, int staffId, ReturnVehicleRequest request);

    List<Handover> listActive();

    List<Handover> listByBooking(int bookingId);
}
