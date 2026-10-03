package com.vehiclerental.enums;

public enum BookingStatus {
    PENDING_APPROVAL,
    APPROVED,
    ACTIVE_RENTAL,
    COMPLETED,
    REJECTED,
    CANCELLED,
    /** Approved, never collected: closed by staff or by the daily run after the grace period. */
    NO_SHOW
}
