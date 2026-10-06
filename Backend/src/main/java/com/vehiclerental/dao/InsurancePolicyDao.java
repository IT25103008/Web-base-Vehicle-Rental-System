package com.vehiclerental.dao;

import com.vehiclerental.model.InsurancePolicy;

import java.util.List;
import java.util.Optional;

public interface InsurancePolicyDao {
    InsurancePolicy save(InsurancePolicy policy);
    Optional<InsurancePolicy> findById(int policyId);
    Optional<InsurancePolicy> findActiveByVehicle(int vehicleId);
    List<InsurancePolicy> findAll();
    List<InsurancePolicy> findExpiringWithinDays(int daysAhead);

    /** Every policy on a vehicle, newest expiry first. */
    List<InsurancePolicy> findByVehicle(int vehicleId);

    /** An ACTIVE policy that covers the whole of [from, to], if there is one. */
    Optional<InsurancePolicy> findCovering(int vehicleId, java.time.LocalDate from, java.time.LocalDate to);

    /** ACTIVE policies on a vehicle whose dates overlap [from, to] — used to stop double cover. */
    List<InsurancePolicy> findActiveOverlapping(int vehicleId, java.time.LocalDate from,
                                                java.time.LocalDate to, Integer excludePolicyId);

    /** Flip ACTIVE policies whose expiry date has passed to EXPIRED. Returns how many changed. */
    int expirePoliciesBefore(java.time.LocalDate today);
    void update(InsurancePolicy policy);
    void delete(int policyId);
}
