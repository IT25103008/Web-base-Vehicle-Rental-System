package com.vehiclerental.service.impl;

import com.vehiclerental.dao.BookingDao;
import com.vehiclerental.dao.MaintenanceDao;
import com.vehiclerental.dao.VehicleDao;
import com.vehiclerental.model.Booking;
import com.vehiclerental.model.InsurancePolicy;
import com.vehiclerental.model.MaintenanceRecord;
import com.vehiclerental.model.Vehicle;
import com.vehiclerental.service.BookingService;
import com.vehiclerental.service.InsurancePolicyService;
import com.vehiclerental.service.NotificationService;
import com.vehiclerental.service.ReminderService;
import com.vehiclerental.util.AppClock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;

@Service
public class ReminderServiceImpl implements ReminderService {

    private static final Logger log = LoggerFactory.getLogger(ReminderServiceImpl.class);

    private static final int POLICY_WARNING_DAYS = 30;
    private static final int MAINTENANCE_WARNING_DAYS = 7;

    private final BookingDao bookingDao;
    private final VehicleDao vehicleDao;
    private final MaintenanceDao maintenanceDao;
    private final InsurancePolicyService insurancePolicyService;
    private final NotificationService notificationService;
    private final BookingService bookingService;

    public ReminderServiceImpl(BookingDao bookingDao, VehicleDao vehicleDao,
                               MaintenanceDao maintenanceDao,
                               InsurancePolicyService insurancePolicyService,
                               NotificationService notificationService,
                               BookingService bookingService) {
        this.bookingService = bookingService;
        this.bookingDao = bookingDao;
        this.vehicleDao = vehicleDao;
        this.maintenanceDao = maintenanceDao;
        this.insurancePolicyService = insurancePolicyService;
        this.notificationService = notificationService;
    }

    /** 07:00 every morning, before the branches open. */
    @Scheduled(cron = "${rental.reminder-cron:0 0 7 * * *}")
    public void scheduledRun() {
        try {
            ReminderSummary s = runDailyChecks();
            log.info("Daily reminders: {} policies expired, {} expiring soon, {} overdue returns, "
                     + "{} missed pickups, {} returns due today, {} services due",
                     s.getPoliciesExpired(), s.getPoliciesExpiringSoon(), s.getOverdueReturns(),
                     s.getMissedPickups(), s.getReturnsDueToday(), s.getMaintenanceDue());
        } catch (RuntimeException e) {
            log.error("Daily reminder run failed", e);
        }
    }

    @Override
    public ReminderSummary runDailyChecks() {
        LocalDate today = AppClock.today();
        ReminderSummary summary = new ReminderSummary();

        summary.setPoliciesExpired(expireAndWarnAboutInsurance(today));
        summary.setPoliciesExpiringSoon(warnAboutExpiringInsurance(today));
        summary.setOverdueReturns(chaseOverdueReturns(today));
        // Close the ones past the grace period first, then chase the rest.
        summary.setNoShowsClosed(bookingService.closeNoShows(today));
        summary.setMissedPickups(chaseMissedPickups(today));
        summary.setReturnsDueToday(remindReturnsDueToday(today));
        summary.setMaintenanceDue(warnAboutMaintenance(today));

        return summary;
    }

    // ------------------------------------------------------------
    // Insurance
    // ------------------------------------------------------------
    private int expireAndWarnAboutInsurance(LocalDate today) {
        int expired = insurancePolicyService.expireLapsedPolicies();
        if (expired > 0) {
            notificationService.safeSendToAdministrators("INSURANCE_EXPIRED",
                expired + " insurance " + (expired == 1 ? "policy has" : "policies have")
                + " lapsed. The affected vehicles cannot be rented until they are covered again.");
        }
        return expired;
    }

    private int warnAboutExpiringInsurance(LocalDate today) {
        List<InsurancePolicy> soon = insurancePolicyService.listExpiring(POLICY_WARNING_DAYS).stream()
            .filter(p -> "ACTIVE".equals(p.getStatus()))
            .toList();
        for (InsurancePolicy p : soon) {
            Vehicle v = vehicleDao.findById(p.getVehicleId()).orElse(null);
            String plate = v == null ? ("vehicle " + p.getVehicleId()) : v.getPlateNumber();
            long days = ChronoUnit.DAYS.between(today, p.getExpiryDate());
            notificationService.safeSendToAdministrators("INSURANCE_EXPIRING",
                "Policy " + p.getPolicyNumber() + " for " + plate + " expires in " + days
                + " day(s), on " + p.getExpiryDate() + ".");
        }
        return soon.size();
    }

    // ------------------------------------------------------------
    // Bookings
    // ------------------------------------------------------------
    private int chaseOverdueReturns(LocalDate today) {
        List<Booking> overdue = bookingDao.findOverdueReturns(today);
        for (Booking b : overdue) {
            long days = ChronoUnit.DAYS.between(b.getReturnDate(), today);
            notificationService.safeSend(b.getCustomerId(), "RETURN_OVERDUE",
                "Booking #" + b.getBookingId() + " was due back on " + b.getReturnDate()
                + ". It is now " + days + " day(s) late and a late fee is building up.");
            notificationService.safeSendToBranchStaff(b.getPickupBranchId(), "RETURN_OVERDUE",
                "Booking #" + b.getBookingId() + " is " + days + " day(s) overdue.");
        }
        return overdue.size();
    }

    private int chaseMissedPickups(LocalDate today) {
        List<Booking> missed = bookingDao.findMissedPickups(today);
        for (Booking b : missed) {
            long days = ChronoUnit.DAYS.between(b.getPickupDate(), today);
            notificationService.safeSendToBranchStaff(b.getPickupBranchId(), "PICKUP_MISSED",
                "Booking #" + b.getBookingId() + " was due for collection on " + b.getPickupDate()
                + " (" + days + " day(s) ago) and nobody has picked it up. "
                + "Cancel it to free the vehicle.");
            notificationService.safeSend(b.getCustomerId(), "PICKUP_MISSED",
                "You have not collected the vehicle for booking #" + b.getBookingId()
                + ", booked for " + b.getPickupDate() + ". Please contact the branch.");
        }
        return missed.size();
    }

    private int remindReturnsDueToday(LocalDate today) {
        List<Booking> due = bookingDao.findReturnsDueOn(today);
        for (Booking b : due) {
            notificationService.safeSend(b.getCustomerId(), "RETURN_DUE_TODAY",
                "Booking #" + b.getBookingId() + " is due back today. "
                + "Late returns are charged at 1.5 times the daily rate.");
        }
        return due.size();
    }

    // ------------------------------------------------------------
    // Workshop
    // ------------------------------------------------------------
    private int warnAboutMaintenance(LocalDate today) {
        // Work that should already have started.
        List<MaintenanceRecord> due = maintenanceDao.findDueToStart(today);
        for (MaintenanceRecord m : due) {
            Vehicle v = vehicleDao.findById(m.getVehicleId()).orElse(null);
            String plate = v == null ? ("vehicle " + m.getVehicleId()) : v.getPlateNumber();
            notificationService.safeSendToBranchStaff(v == null ? null : v.getBranchId(),
                "MAINTENANCE_DUE",
                "Service for " + plate + " was scheduled for " + m.getEventDate()
                + " and has not been started.");
        }

        // Next-service reminders coming up.
        List<MaintenanceRecord> upcoming = maintenanceDao.findUpcoming(MAINTENANCE_WARNING_DAYS);
        for (MaintenanceRecord m : upcoming) {
            Vehicle v = vehicleDao.findById(m.getVehicleId()).orElse(null);
            String plate = v == null ? ("vehicle " + m.getVehicleId()) : v.getPlateNumber();
            notificationService.safeSendToBranchStaff(v == null ? null : v.getBranchId(),
                "SERVICE_REMINDER",
                plate + " is due for its next service on " + m.getNextServiceDate() + ".");
        }
        return due.size() + upcoming.size();
    }
}
