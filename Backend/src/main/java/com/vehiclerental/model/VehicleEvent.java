package com.vehiclerental.model;

import java.time.LocalDate;

// Abstract superclass shared by MaintenanceRecord and DamageReport.
// Both are "a dated event tied to a vehicle, with a status" — that's what
// this base class captures.
//
// Note: status is a String here because the two subclasses use different
// enum types (MaintenanceStatus and DamageStatus). The services validate
// the value against the correct enum.
public abstract class VehicleEvent {

    private int eventId;
    private int vehicleId;
    private LocalDate eventDate;
    private String status;
    private String description;

    protected VehicleEvent() {
    }

    // Polymorphism (overriding): each subclass explains what "resolving"
    // this event does to the vehicle.
    public abstract String applyEffect();

    public int getEventId() { return eventId; }
    public void setEventId(int eventId) { this.eventId = eventId; }

    public int getVehicleId() { return vehicleId; }
    public void setVehicleId(int vehicleId) { this.vehicleId = vehicleId; }

    public LocalDate getEventDate() { return eventDate; }
    public void setEventDate(LocalDate eventDate) { this.eventDate = eventDate; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
}
