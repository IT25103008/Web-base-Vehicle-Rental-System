package com.vehiclerental.enums;

public enum PaymentStatus {
    PENDING,     // money is owed
    PAID,        // settled in full
    REFUNDED,    // was paid, then the booking was cancelled — money owed back to the customer
    CANCELLED    // was never paid and never will be (booking cancelled or rejected)
}
