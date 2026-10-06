package com.vehiclerental.service.impl;

import com.vehiclerental.dao.BookingDao;
import com.vehiclerental.dao.InsurancePolicyDao;
import com.vehiclerental.dao.VehicleDao;
import com.vehiclerental.exception.InvalidStatusTransitionException;
import com.vehiclerental.exception.ResourceNotFoundException;
import com.vehiclerental.model.Booking;
import com.vehiclerental.model.InsurancePolicy;
import com.vehiclerental.model.Vehicle;
import com.vehiclerental.service.AuditService;
import com.vehiclerental.service.InsurancePolicyService;
import com.vehiclerental.util.AppClock;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.*;

@Service
public class InsurancePolicyServiceImpl implements InsurancePolicyService {

    private static final String ENTITY = "INSURANCE_POLICY";
    private static final Set<String> STATUSES = Set.of("ACTIVE", "EXPIRED", "CANCELLED");

    private final InsurancePolicyDao insurancePolicyDao;
    private final VehicleDao vehicleDao;
    private final BookingDao bookingDao;
    private final AuditService auditService;

    public InsurancePolicyServiceImpl(InsurancePolicyDao insurancePolicyDao, VehicleDao vehicleDao,
                                      BookingDao bookingDao, AuditService auditService) {
        this.insurancePolicyDao = insurancePolicyDao;
        this.vehicleDao = vehicleDao;
        this.bookingDao = bookingDao;
        this.auditService = auditService;
    }

    @Override
    @Transactional
    public InsurancePolicy create(InsurancePolicy policy, int actorUserId) {
        if (policy.getStatus() == null || policy.getStatus().isBlank()) {
            policy.setStatus("ACTIVE");
        }
        validate(policy);

        // Two active policies covering the same days on the same vehicle means
        // somebody is paying twice and nobody knows which one applies.
        if ("ACTIVE".equals(policy.getStatus())) {
            List<InsurancePolicy> overlapping = insurancePolicyDao.findActiveOverlapping(
                policy.getVehicleId(), policy.getStartDate(), policy.getExpiryDate(), null);
            if (!overlapping.isEmpty()) {
                InsurancePolicy other = overlapping.get(0);
                throw new InvalidStatusTransitionException(
                    "This vehicle already has active cover (policy " + other.getPolicyNumber()
                    + ") from " + other.getStartDate() + " to " + other.getExpiryDate());
            }
        }

        InsurancePolicy saved = insurancePolicyDao.save(policy);
        auditService.record(ENTITY, saved.getPolicyId(), "CREATE", actorUserId,
            "Policy " + saved.getPolicyNumber() + " covering " + saved.getStartDate()
            + " to " + saved.getExpiryDate());
        return saved;
    }

    @Override
    public InsurancePolicy findById(int policyId) {
        return insurancePolicyDao.findById(policyId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Insurance policy not found: " + policyId));
    }

    @Override
    @Transactional
    public List<InsurancePolicy> listAll() {
        expireLapsedPolicies();
        return insurancePolicyDao.findAll();
    }

    @Override
    public List<InsurancePolicy> listByVehicle(int vehicleId) {
        return insurancePolicyDao.findByVehicle(vehicleId);
    }

    /**
     * "Expiring" has to include policies that have already lapsed. The old query
     * only looked forwards from today and filtered on status = ACTIVE, so a
     * policy that ran out yesterday quietly disappeared from the warning list
     * instead of shouting about itself.
     */
    @Override
    @Transactional
    public List<InsurancePolicy> listExpiring(int daysAhead) {
        if (daysAhead <= 0) {
            daysAhead = 30;
        }
        expireLapsedPolicies();

        Map<Integer, InsurancePolicy> byId = new LinkedHashMap<>();
        LocalDate today = AppClock.today();

        for (InsurancePolicy p : insurancePolicyDao.findAll()) {
            boolean lapsed = p.getExpiryDate().isBefore(today) && !"CANCELLED".equals(p.getStatus());
            boolean expiringSoon = "ACTIVE".equals(p.getStatus())
                    && !p.getExpiryDate().isBefore(today)
                    && !p.getExpiryDate().isAfter(today.plusDays(daysAhead));
            if (lapsed || expiringSoon) {
                byId.put(p.getPolicyId(), p);
            }
        }
        List<InsurancePolicy> result = new ArrayList<>(byId.values());
        result.sort((a, b) -> a.getExpiryDate().compareTo(b.getExpiryDate()));
        return result;
    }

    @Override
    @Transactional
    public InsurancePolicy update(InsurancePolicy policy, int actorUserId) {
        InsurancePolicy existing = findById(policy.getPolicyId());

        // A policy belongs to the vehicle it was written for. Moving it would
        // silently leave the original vehicle uninsured.
        if (policy.getVehicleId() != 0 && policy.getVehicleId() != existing.getVehicleId()) {
            throw new IllegalArgumentException(
                "A policy cannot be moved to a different vehicle. Cancel it and write a new one.");
        }

        // Fields left out of the request keep their current values.
        policy.setVehicleId(existing.getVehicleId());
        if (policy.getPolicyNumber() == null) policy.setPolicyNumber(existing.getPolicyNumber());
        if (policy.getProvider() == null) policy.setProvider(existing.getProvider());
        if (policy.getStartDate() == null) policy.setStartDate(existing.getStartDate());
        if (policy.getExpiryDate() == null) policy.setExpiryDate(existing.getExpiryDate());
        if (policy.getStatus() == null) policy.setStatus(existing.getStatus());

        validate(policy);

        // A lapsed policy cannot simply be switched back on.
        if ("ACTIVE".equals(policy.getStatus()) && policy.getExpiryDate().isBefore(AppClock.today())) {
            throw new IllegalArgumentException(
                "A policy that expired on " + policy.getExpiryDate() + " cannot be set ACTIVE. "
                + "Extend the expiry date or write a new policy.");
        }
        if ("ACTIVE".equals(policy.getStatus())) {
            List<InsurancePolicy> overlapping = insurancePolicyDao.findActiveOverlapping(
                policy.getVehicleId(), policy.getStartDate(), policy.getExpiryDate(), policy.getPolicyId());
            if (!overlapping.isEmpty()) {
                throw new InvalidStatusTransitionException(
                    "Another active policy already covers part of that period");
            }
        }

        insurancePolicyDao.update(policy);
        auditService.record(ENTITY, policy.getPolicyId(), "UPDATE", actorUserId,
            "Policy " + policy.getPolicyNumber() + " updated (" + existing.getStatus()
            + " -> " + policy.getStatus() + ")");
        return policy;
    }

    /**
     * Cover that a booking is relying on cannot be deleted out from under it.
     */
    @Override
    @Transactional
    public void delete(int policyId, int actorUserId) {
        InsurancePolicy p = findById(policyId);

        if ("ACTIVE".equals(p.getStatus())) {
            List<Booking> relying = bookingDao.findLiveByVehicleInWindow(
                p.getVehicleId(), p.getStartDate(), p.getExpiryDate());
            if (!relying.isEmpty()) {
                Booking b = relying.get(0);
                throw new InvalidStatusTransitionException(
                    "Booking #" + b.getBookingId() + " relies on this cover. "
                    + "Cancel the booking or write replacement cover first.");
            }
        }

        insurancePolicyDao.delete(policyId);
        auditService.record(ENTITY, policyId, "DELETE", actorUserId,
                            "Policy " + p.getPolicyNumber() + " removed");
    }

    /**
     * Status is derived from the calendar, not from someone remembering to
     * change it. This runs on every read and once a day from the reminder job.
     */
    @Override
    @Transactional
    public int expireLapsedPolicies() {
        return insurancePolicyDao.expirePoliciesBefore(AppClock.today());
    }

    private void validate(InsurancePolicy p) {
        Vehicle vehicle = vehicleDao.findById(p.getVehicleId())
                .orElseThrow(() -> new ResourceNotFoundException("Vehicle not found: " + p.getVehicleId()));
        if (p.getPolicyNumber() == null || p.getPolicyNumber().isBlank()) {
            throw new IllegalArgumentException("Policy number is required");
        }
        p.setPolicyNumber(p.getPolicyNumber().trim());
        if (p.getProvider() == null || p.getProvider().isBlank()) {
            throw new IllegalArgumentException("Provider is required");
        }
        if (p.getStartDate() == null || p.getExpiryDate() == null) {
            throw new IllegalArgumentException("Start date and expiry date are required");
        }
        if (!p.getExpiryDate().isAfter(p.getStartDate())) {
            throw new IllegalArgumentException("Expiry date must be after start date");
        }
        p.setStatus(p.getStatus().toUpperCase());
        if (!STATUSES.contains(p.getStatus())) {
            throw new IllegalArgumentException("Status must be ACTIVE, EXPIRED or CANCELLED");
        }
        // Referenced so the vehicle lookup is not optimised away by a reader:
        // a policy must always point at a real vehicle.
        if (vehicle.getVehicleId() != p.getVehicleId()) {
            throw new IllegalArgumentException("Policy vehicle mismatch");
        }
    }
}
