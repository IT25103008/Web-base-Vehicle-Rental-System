package com.vehiclerental.dto.response;

import java.math.BigDecimal;
import java.util.Map;

public class DashboardResponse {

    private long totalBookings;
    private long activeRentals;
    private long pendingApprovals;
    private long availableVehicles;
    private long vehiclesUnderMaintenance;
    private long expiredInsurancePolicies;
    private BigDecimal totalRevenuePaid;   // sum of PAID payments
    private BigDecimal outstandingPayments; // sum of PENDING payments on live bookings
    private long overdueReturns;           // vehicles out past their return date
    private long missedPickups;            // approved bookings nobody collected
    private long unpaidReadyForPickup;     // approved, due out, still unpaid
    private long openDamageReports;
    private long policiesExpiringSoon;     // inside the next 30 days
    private long uninsuredVehicles;        // no active policy covering today

    // { "AVAILABLE": 40, "RENTED": 10, "UNDER_MAINTENANCE": 5, ... }
    private Map<String, Long> vehiclesByStatus;

    // { "PENDING_APPROVAL": 3, "APPROVED": 2, "ACTIVE_RENTAL": 10, ... }
    private Map<String, Long> bookingsByStatus;

    public DashboardResponse() { }

    public long getTotalBookings() { return totalBookings; }
    public void setTotalBookings(long totalBookings) { this.totalBookings = totalBookings; }

    public long getActiveRentals() { return activeRentals; }
    public void setActiveRentals(long activeRentals) { this.activeRentals = activeRentals; }

    public long getPendingApprovals() { return pendingApprovals; }
    public void setPendingApprovals(long pendingApprovals) { this.pendingApprovals = pendingApprovals; }

    public long getAvailableVehicles() { return availableVehicles; }
    public void setAvailableVehicles(long availableVehicles) { this.availableVehicles = availableVehicles; }

    public long getVehiclesUnderMaintenance() { return vehiclesUnderMaintenance; }
    public void setVehiclesUnderMaintenance(long v) { this.vehiclesUnderMaintenance = v; }

    public long getExpiredInsurancePolicies() { return expiredInsurancePolicies; }
    public void setExpiredInsurancePolicies(long v) { this.expiredInsurancePolicies = v; }

    public BigDecimal getTotalRevenuePaid() { return totalRevenuePaid; }
    public void setTotalRevenuePaid(BigDecimal totalRevenuePaid) {
        this.totalRevenuePaid = totalRevenuePaid;
    }

    public Map<String, Long> getVehiclesByStatus() { return vehiclesByStatus; }
    public void setVehiclesByStatus(Map<String, Long> vehiclesByStatus) {
        this.vehiclesByStatus = vehiclesByStatus;
    }

    public Map<String, Long> getBookingsByStatus() { return bookingsByStatus; }
    public void setBookingsByStatus(Map<String, Long> bookingsByStatus) {
        this.bookingsByStatus = bookingsByStatus;
    }

    public BigDecimal getOutstandingPayments() { return outstandingPayments; }
    public void setOutstandingPayments(BigDecimal v) { this.outstandingPayments = v; }

    public long getOverdueReturns() { return overdueReturns; }
    public void setOverdueReturns(long v) { this.overdueReturns = v; }

    public long getMissedPickups() { return missedPickups; }
    public void setMissedPickups(long v) { this.missedPickups = v; }

    public long getUnpaidReadyForPickup() { return unpaidReadyForPickup; }
    public void setUnpaidReadyForPickup(long v) { this.unpaidReadyForPickup = v; }

    public long getOpenDamageReports() { return openDamageReports; }
    public void setOpenDamageReports(long v) { this.openDamageReports = v; }

    public long getPoliciesExpiringSoon() { return policiesExpiringSoon; }
    public void setPoliciesExpiringSoon(long v) { this.policiesExpiringSoon = v; }

    public long getUninsuredVehicles() { return uninsuredVehicles; }
    public void setUninsuredVehicles(long v) { this.uninsuredVehicles = v; }

    // The branch these figures are for; null = the whole company.
    private Integer branchId;

    public Integer getBranchId() { return branchId; }
    public void setBranchId(Integer branchId) { this.branchId = branchId; }
}
