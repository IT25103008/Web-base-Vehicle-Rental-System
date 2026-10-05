package com.vehiclerental.service.impl;

import com.vehiclerental.dao.BookingDao;
import com.vehiclerental.dao.BranchDao;
import com.vehiclerental.dao.DamageReportDao;
import com.vehiclerental.dao.InsurancePolicyDao;
import com.vehiclerental.dao.MaintenanceDao;
import com.vehiclerental.dao.VehicleDao;
import com.vehiclerental.dao.VehicleTransferDao;
import com.vehiclerental.enums.VehicleStatus;
import com.vehiclerental.exception.DoubleBookingException;
import com.vehiclerental.exception.InvalidStatusTransitionException;
import com.vehiclerental.exception.VehicleNotAvailableException;
import com.vehiclerental.model.Booking;
import com.vehiclerental.model.Branch;
import com.vehiclerental.model.DamageReport;
import com.vehiclerental.model.MaintenanceRecord;
import com.vehiclerental.model.Vehicle;
import com.vehiclerental.model.VehicleTransfer;
import com.vehiclerental.service.AvailabilityService;
import com.vehiclerental.util.AppClock;
import com.vehiclerental.util.RentalPolicy;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;

@Service
public class AvailabilityServiceImpl implements AvailabilityService {

    private final BookingDao bookingDao;
    private final VehicleDao vehicleDao;
    private final BranchDao branchDao;
    private final MaintenanceDao maintenanceDao;
    private final DamageReportDao damageReportDao;
    private final InsurancePolicyDao insurancePolicyDao;
    private final VehicleTransferDao transferDao;
    private final boolean requireInsurance;

    public AvailabilityServiceImpl(BookingDao bookingDao,
                                   VehicleDao vehicleDao,
                                   BranchDao branchDao,
                                   MaintenanceDao maintenanceDao,
                                   DamageReportDao damageReportDao,
                                   InsurancePolicyDao insurancePolicyDao,
                                   VehicleTransferDao transferDao,
                                   @Value("${rental.require-insurance:true}") boolean requireInsurance) {
        this.bookingDao = bookingDao;
        this.vehicleDao = vehicleDao;
        this.branchDao = branchDao;
        this.maintenanceDao = maintenanceDao;
        this.damageReportDao = damageReportDao;
        this.insurancePolicyDao = insurancePolicyDao;
        this.transferDao = transferDao;
        this.requireInsurance = requireInsurance;
    }

    @Override
    public void requireBookable(Vehicle v, LocalDate pickup, LocalDate returnDate,
                                Integer excludeBookingId) {

        // 1. Physical state. A car that is out on rental today is still bookable
        //    for a future window — only the workshop and write-offs are hard stops.
        if (v.getStatus() == VehicleStatus.UNDER_MAINTENANCE || v.getStatus() == VehicleStatus.UNAVAILABLE) {
            throw new VehicleNotAvailableException(
                "This vehicle is currently " + v.getStatus() + " and cannot be booked");
        }

        // 2. Its home branch must be trading.
        Branch branch = branchDao.findById(v.getBranchId()).orElse(null);
        if (branch == null || !"ACTIVE".equals(branch.getStatus())) {
            throw new VehicleNotAvailableException(
                "The branch that holds this vehicle is not currently open for business");
        }

        // 3. No clashing booking.
        List<Booking> clashes = bookingDao.findOverlapping(
            v.getVehicleId(), pickup, returnDate, excludeBookingId);
        if (!clashes.isEmpty()) {
            Booking c = clashes.get(0);
            throw new DoubleBookingException(
                "This vehicle is already booked from " + c.getPickupDate() + " to " + c.getReturnDate());
        }

        // 4. No clashing workshop window.
        List<MaintenanceRecord> works = maintenanceDao.findBlocking(
            v.getVehicleId(), pickup, returnDate, null);
        if (!works.isEmpty()) {
            MaintenanceRecord m = works.get(0);
            throw new VehicleNotAvailableException(
                "This vehicle is booked into the workshop from " + m.getEventDate()
                + " to " + m.blockedUntil());
        }

        // 5. No unresolved serious damage.
        List<DamageReport> open = damageReportDao.findOpenByVehicle(v.getVehicleId());
        boolean seriousDamage = open.stream()
            .anyMatch(d -> RentalPolicy.blocksVehicle(d.getDamageSeverity()));
        if (seriousDamage) {
            throw new VehicleNotAvailableException(
                "This vehicle has an unresolved damage report and cannot be rented out");
        }

        // 6. No pending transfer that moves the car before the rental ends.
        List<VehicleTransfer> transfers = transferDao.findPendingByVehicle(v.getVehicleId());
        for (VehicleTransfer t : transfers) {
            if (!t.getTransferDate().isAfter(returnDate)) {
                throw new VehicleNotAvailableException(
                    "This vehicle is due to move to another branch on " + t.getTransferDate());
            }
        }

        // 7. Insurance must cover the whole rental.
        if (requireInsurance
                && insurancePolicyDao.findCovering(v.getVehicleId(), pickup, returnDate).isEmpty()) {
            throw new VehicleNotAvailableException(
                "This vehicle has no active insurance policy covering "
                + pickup + " to " + returnDate);
        }
    }

    @Override
    public List<BusyRange> busy(int vehicleId, LocalDate from, LocalDate to) {
        Vehicle v = vehicleDao.findById(vehicleId)
            .orElseThrow(() -> new com.vehiclerental.exception.ResourceNotFoundException("Vehicle not found: " + vehicleId));
        List<BusyRange> out = new java.util.ArrayList<>();

        boolean offRoad = v.getStatus() == VehicleStatus.UNDER_MAINTENANCE || v.getStatus() == VehicleStatus.UNAVAILABLE
            || damageReportDao.findOpenByVehicle(vehicleId).stream().anyMatch(d -> RentalPolicy.blocksVehicle(d.getDamageSeverity()));
        Branch branch = branchDao.findById(v.getBranchId()).orElse(null);
        if (offRoad || branch == null || !"ACTIVE".equals(branch.getStatus())) {
            out.add(new BusyRange(from, to, "OFF_ROAD"));
            return out;
        }
        for (Booking b : bookingDao.findLiveByVehicleFrom(vehicleId, from)) {
            if (!b.getPickupDate().isAfter(to)) {
                out.add(new BusyRange(max(b.getPickupDate(), from), min(b.getReturnDate(), to), "BOOKED"));
            }
        }
        for (MaintenanceRecord m : maintenanceDao.findBlocking(vehicleId, from, to, null)) {
            out.add(new BusyRange(max(m.getEventDate(), from), min(m.blockedUntil(), to), "WORKSHOP"));
        }
        for (VehicleTransfer t : transferDao.findPendingByVehicle(vehicleId)) {
            if (!t.getTransferDate().isAfter(to)) {
                out.add(new BusyRange(max(t.getTransferDate(), from), to, "MOVING"));
            }
        }
        if (requireInsurance) {
            // Days not inside any active policy.
            List<com.vehiclerental.model.InsurancePolicy> cover = insurancePolicyDao.findByVehicle(vehicleId).stream()
                .filter(p -> "ACTIVE".equals(p.getStatus())).toList();
            LocalDate gapStart = null;
            for (LocalDate d = from; !d.isAfter(to); d = d.plusDays(1)) {
                LocalDate day = d;
                boolean covered = cover.stream().anyMatch(p -> !p.getStartDate().isAfter(day) && !p.getExpiryDate().isBefore(day));
                if (!covered && gapStart == null) gapStart = d;
                if (covered && gapStart != null) {
                    out.add(new BusyRange(gapStart, d.minusDays(1), "UNINSURED"));
                    gapStart = null;
                }
            }
            if (gapStart != null) out.add(new BusyRange(gapStart, to, "UNINSURED"));
        }
        out.sort(java.util.Comparator.comparing(BusyRange::from));
        return out;
    }

    @Override
    public LocalDate nextFree(int vehicleId, int nights, LocalDate from) {
        int n = Math.max(1, Math.min(nights, RentalPolicy.MAX_RENTAL_DAYS));
        LocalDate start = from == null || from.isBefore(AppClock.today()) ? AppClock.today() : from;
        LocalDate horizon = AppClock.today().plusDays(RentalPolicy.MAX_ADVANCE_DAYS);
        List<BusyRange> busy = busy(vehicleId, start, horizon.plusDays(n));
        Vehicle v = vehicleDao.findById(vehicleId).orElse(null);
        for (LocalDate s = start; !s.isAfter(horizon); s = s.plusDays(1)) {
            LocalDate e = s.plusDays(n);
            LocalDate ps = s;
            boolean clash = busy.stream().anyMatch(r -> !r.from().isAfter(e) && !ps.isAfter(r.to()));
            // The day scan is quick; the full rule set confirms the winner.
            if (!clash && v != null && isBookable(v, s, e, null)) {
                return s;
            }
        }
        return null;
    }

    private static LocalDate max(LocalDate a, LocalDate b) { return a.isAfter(b) ? a : b; }
    private static LocalDate min(LocalDate a, LocalDate b) { return a.isBefore(b) ? a : b; }

    @Override
    public boolean isBookable(Vehicle vehicle, LocalDate pickup, LocalDate returnDate,
                              Integer excludeBookingId) {
        try {
            requireBookable(vehicle, pickup, returnDate, excludeBookingId);
            return true;
        } catch (RuntimeException e) {
            return false;
        }
    }

    @Override
    public void requireNoLiveBookings(int vehicleId, LocalDate from, LocalDate to, String action) {
        List<Booking> clashes = bookingDao.findLiveByVehicleInWindow(vehicleId, from, to);
        if (!clashes.isEmpty()) {
            Booking b = clashes.get(0);
            throw new InvalidStatusTransitionException(
                "Cannot " + action + ": booking #" + b.getBookingId() + " (" + b.getStatus()
                + ") runs from " + b.getPickupDate() + " to " + b.getReturnDate());
        }
    }

    /**
     * RESERVED means "set aside on the lot for a pickup that is due now". It is
     * deliberately NOT used for bookings months away — those are handled by the
     * date-range checks above, so the car stays rentable in the meantime.
     */
    @Override
    public void refreshReservationStatus(int vehicleId) {
        Vehicle v = vehicleDao.findById(vehicleId).orElse(null);
        if (v == null) {
            return;
        }
        // Never override a car that is out with a customer, in the workshop, or written off.
        if (v.getStatus() != VehicleStatus.AVAILABLE && v.getStatus() != VehicleStatus.RESERVED) {
            return;
        }

        LocalDate cutoff = AppClock.today().plusDays(RentalPolicy.RESERVE_VEHICLE_DAYS_AHEAD);
        boolean dueOut = !bookingDao.findApprovedPickupsDueBy(vehicleId, cutoff).isEmpty();

        VehicleStatus target = dueOut ? VehicleStatus.RESERVED : VehicleStatus.AVAILABLE;
        if (v.getStatus() != target) {
            vehicleDao.updateStatus(vehicleId, target.name());
        }
    }
}
