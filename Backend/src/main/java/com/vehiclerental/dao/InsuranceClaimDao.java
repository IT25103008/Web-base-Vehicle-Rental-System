package com.vehiclerental.dao;

import com.vehiclerental.model.InsuranceClaim;

import java.util.List;
import java.util.Optional;

public interface InsuranceClaimDao {
    InsuranceClaim save(InsuranceClaim claim);
    Optional<InsuranceClaim> findById(int claimId);
    List<InsuranceClaim> findAll();
    List<InsuranceClaim> findByStatus(String status);
    List<InsuranceClaim> findByDamageReport(int damageReportId);
    void updateStatus(int claimId, String newStatus);
    void delete(int claimId);
}
