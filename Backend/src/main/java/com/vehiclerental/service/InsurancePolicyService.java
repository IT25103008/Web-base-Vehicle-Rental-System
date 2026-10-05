package com.vehiclerental.service;

import com.vehiclerental.model.InsurancePolicy;

import java.util.List;

public interface InsurancePolicyService {

    InsurancePolicy create(InsurancePolicy policy, int actorUserId);

    InsurancePolicy findById(int policyId);

    List<InsurancePolicy> listAll();

    List<InsurancePolicy> listByVehicle(int vehicleId);

    /**
     * Policies that need attention: those expiring inside the next N days, and
     * any that have already lapsed.
     */
    List<InsurancePolicy> listExpiring(int daysAhead);

    InsurancePolicy update(InsurancePolicy policy, int actorUserId);

    void delete(int policyId, int actorUserId);

    /** Move ACTIVE policies whose expiry date has passed to EXPIRED. Returns how many changed. */
    int expireLapsedPolicies();
}
