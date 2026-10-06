package com.vehiclerental.controller;

import com.vehiclerental.dto.request.PickupRequest;
import com.vehiclerental.dto.request.ReturnVehicleRequest;
import com.vehiclerental.model.Handover;
import com.vehiclerental.security.AppUserPrincipal;
import com.vehiclerental.service.HandoverService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/handovers")
@PreAuthorize("hasAnyRole('STAFF', 'ADMINISTRATOR')")
public class HandoverController {

    private final HandoverService handoverService;

    public HandoverController(HandoverService handoverService) {
        this.handoverService = handoverService;
    }

    @PostMapping("/pickup/{bookingId}")
    public ResponseEntity<Handover> pickup(@PathVariable int bookingId,
                                           @Valid @RequestBody PickupRequest r,
                                           @AuthenticationPrincipal AppUserPrincipal me) {
        return ResponseEntity.ok(handoverService.confirmPickup(bookingId, me.getUserId(), r));
    }

    @PostMapping("/return/{bookingId}")
    public ResponseEntity<Handover> returnVehicle(@PathVariable int bookingId,
                                                  @Valid @RequestBody ReturnVehicleRequest r,
                                                  @AuthenticationPrincipal AppUserPrincipal me) {
        return ResponseEntity.ok(handoverService.recordReturn(bookingId, me.getUserId(), r));
    }

    @GetMapping("/active")
    public List<Handover> active() {
        return handoverService.listActive();
    }

    @GetMapping("/booking/{bookingId}")
    public List<Handover> byBooking(@PathVariable int bookingId) {
        return handoverService.listByBooking(bookingId);
    }
}
