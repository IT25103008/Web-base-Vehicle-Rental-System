package com.vehiclerental.dto.response;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Every line of what a trip cost, for the customer's own trip page (B6):
 * the rental, a late-return surcharge, damage found at return (with any
 * insurance claim and its outcome), a cancellation charge, and the payment.
 * Staff-only details (who reported what, internal notes) are left out.
 */
public class BookingChargesResponse {

    public static class DamageLine {
        private String description;
        private String severity;
        private BigDecimal cost;
        private String status;
        private LocalDate reportedOn;
        private String claimStatus;
        private BigDecimal claimAmount;

        public String getDescription() { return description; }
        public void setDescription(String description) { this.description = description; }
        public String getSeverity() { return severity; }
        public void setSeverity(String severity) { this.severity = severity; }
        public BigDecimal getCost() { return cost; }
        public void setCost(BigDecimal cost) { this.cost = cost; }
        public String getStatus() { return status; }
        public void setStatus(String status) { this.status = status; }
        public LocalDate getReportedOn() { return reportedOn; }
        public void setReportedOn(LocalDate reportedOn) { this.reportedOn = reportedOn; }
        public String getClaimStatus() { return claimStatus; }
        public void setClaimStatus(String claimStatus) { this.claimStatus = claimStatus; }
        public BigDecimal getClaimAmount() { return claimAmount; }
        public void setClaimAmount(BigDecimal claimAmount) { this.claimAmount = claimAmount; }
    }

    private int bookingId;
    private String status;
    private long nights;
    private BigDecimal dailyRate;
    private BigDecimal baseCost;
    private long lateDays;
    private BigDecimal lateFee = BigDecimal.ZERO;
    private List<DamageLine> damage = new ArrayList<>();
    private BigDecimal damageTotal = BigDecimal.ZERO;
    private BigDecimal insuranceCredit = BigDecimal.ZERO;
    private BigDecimal cancellationCharge = BigDecimal.ZERO;
    private BigDecimal total;
    private boolean finalTotal;
    private PaymentResponse payment;

    public int getBookingId() { return bookingId; }
    public void setBookingId(int bookingId) { this.bookingId = bookingId; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public long getNights() { return nights; }
    public void setNights(long nights) { this.nights = nights; }
    public BigDecimal getDailyRate() { return dailyRate; }
    public void setDailyRate(BigDecimal dailyRate) { this.dailyRate = dailyRate; }
    public BigDecimal getBaseCost() { return baseCost; }
    public void setBaseCost(BigDecimal baseCost) { this.baseCost = baseCost; }
    public long getLateDays() { return lateDays; }
    public void setLateDays(long lateDays) { this.lateDays = lateDays; }
    public BigDecimal getLateFee() { return lateFee; }
    public void setLateFee(BigDecimal lateFee) { this.lateFee = lateFee; }
    public List<DamageLine> getDamage() { return damage; }
    public void setDamage(List<DamageLine> damage) { this.damage = damage; }
    public BigDecimal getDamageTotal() { return damageTotal; }
    public void setDamageTotal(BigDecimal damageTotal) { this.damageTotal = damageTotal; }
    public BigDecimal getInsuranceCredit() { return insuranceCredit; }
    public void setInsuranceCredit(BigDecimal insuranceCredit) { this.insuranceCredit = insuranceCredit; }
    public BigDecimal getCancellationCharge() { return cancellationCharge; }
    public void setCancellationCharge(BigDecimal cancellationCharge) { this.cancellationCharge = cancellationCharge; }
    public BigDecimal getTotal() { return total; }
    public void setTotal(BigDecimal total) { this.total = total; }
    public boolean isFinalTotal() { return finalTotal; }
    public void setFinalTotal(boolean finalTotal) { this.finalTotal = finalTotal; }
    public PaymentResponse getPayment() { return payment; }
    public void setPayment(PaymentResponse payment) { this.payment = payment; }
}
