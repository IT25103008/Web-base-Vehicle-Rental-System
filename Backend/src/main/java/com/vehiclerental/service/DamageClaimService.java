package com.vehiclerental.service;

import com.vehiclerental.dto.request.CreateClaimRequest;
import com.vehiclerental.dto.request.CreateDamageReportRequest;
import com.vehiclerental.dto.response.ClaimResponse;
import com.vehiclerental.dto.response.DamageReportResponse;

import java.util.List;

public interface DamageClaimService {

    // ---------- Damage reports ----------
    DamageReportResponse createDamageReport(int staffId, CreateDamageReportRequest request);

    DamageReportResponse findDamageReportById(int eventId);

    List<DamageReportResponse> listAllDamageReports();

    List<DamageReportResponse> listDamageReportsByVehicle(int vehicleId);

    List<DamageReportResponse> listDamageReportsByStatus(String status);

    /** Resolving a report is what puts a damaged vehicle back on the road. */
    void updateDamageReportStatus(int eventId, String newStatus, int actorUserId, String reason);

    void deleteClosedReport(int eventId, int actorUserId);

    // ---------- Insurance claims ----------
    ClaimResponse createClaim(CreateClaimRequest request, int actorUserId);

    ClaimResponse findClaimById(int claimId);

    List<ClaimResponse> listAllClaims();

    List<ClaimResponse> listClaimsByStatus(String status);

    List<ClaimResponse> listClaimsForDamageReport(int damageReportId);

    /** Approving a claim credits the customer's bill for the part insurance covers. */
    void updateClaimStatus(int claimId, String newStatus, int actorUserId, String reason);

    void deleteCancelledClaim(int claimId, int actorUserId);
}
