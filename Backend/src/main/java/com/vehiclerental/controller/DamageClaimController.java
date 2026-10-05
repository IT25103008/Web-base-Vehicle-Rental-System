package com.vehiclerental.controller;

import jakarta.validation.constraints.Size;
import com.vehiclerental.validation.Rules;
import com.vehiclerental.dto.request.CreateClaimRequest;
import com.vehiclerental.dto.request.CreateDamageReportRequest;
import com.vehiclerental.dto.response.ClaimResponse;
import com.vehiclerental.dto.response.DamageReportResponse;
import com.vehiclerental.security.AppUserPrincipal;
import com.vehiclerental.service.DamageClaimService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@PreAuthorize("hasAnyRole('STAFF', 'ADMINISTRATOR')")
public class DamageClaimController {

    private final DamageClaimService damageClaimService;

    public DamageClaimController(DamageClaimService damageClaimService) {
        this.damageClaimService = damageClaimService;
    }

    // ==================== Damage Reports ====================

    @PostMapping("/api/damage-reports")
    public ResponseEntity<DamageReportResponse> createDamageReport(
            @Valid @RequestBody CreateDamageReportRequest request,
            @AuthenticationPrincipal AppUserPrincipal me) {
        DamageReportResponse r = damageClaimService.createDamageReport(me.getUserId(), request);
        return ResponseEntity.ok(r);
    }

    @GetMapping("/api/damage-reports/{id}")
    public DamageReportResponse findDamageReport(@PathVariable int id) {
        return damageClaimService.findDamageReportById(id);
    }

    @GetMapping("/api/damage-reports")
    public List<DamageReportResponse> listAllDamageReports(
            @RequestParam(required = false) String status) {
        if (status == null || status.isBlank()) {
            return damageClaimService.listAllDamageReports();
        }
        return damageClaimService.listDamageReportsByStatus(status);
    }

    @GetMapping("/api/damage-reports/vehicle/{vehicleId}")
    public List<DamageReportResponse> listDamageReportsByVehicle(@PathVariable int vehicleId) {
        return damageClaimService.listDamageReportsByVehicle(vehicleId);
    }

    @PatchMapping("/api/damage-reports/{id}/status")
    public ResponseEntity<String> updateDamageReportStatus(@PathVariable int id,
                                                           @RequestParam String status,
                                                           @RequestParam(required = false) @Size(max = Rules.REASON, message = "Reason can be at most " + Rules.REASON + " characters") String reason,
                                                           @AuthenticationPrincipal AppUserPrincipal me) {
        damageClaimService.updateDamageReportStatus(id, status, me.getUserId(), reason);
        return ResponseEntity.ok("Damage report status updated");
    }

    @DeleteMapping("/api/damage-reports/{id}")
    public ResponseEntity<String> deleteDamageReport(@PathVariable int id,
                                                     @AuthenticationPrincipal AppUserPrincipal me) {
        damageClaimService.deleteClosedReport(id, me.getUserId());
        return ResponseEntity.ok("Damage report deleted");
    }

    // ==================== Insurance Claims ====================
    // Admin-only per SecurityConfig, and we double up with method-level checks.

    @PostMapping("/api/claims")
    @PreAuthorize("hasRole('ADMINISTRATOR')")
    public ResponseEntity<ClaimResponse> createClaim(@Valid @RequestBody CreateClaimRequest request,
                                                     @AuthenticationPrincipal AppUserPrincipal me) {
        ClaimResponse r = damageClaimService.createClaim(request, me.getUserId());
        return ResponseEntity.ok(r);
    }

    @GetMapping("/api/claims/{id}")
    @PreAuthorize("hasRole('ADMINISTRATOR')")
    public ClaimResponse findClaim(@PathVariable int id) {
        return damageClaimService.findClaimById(id);
    }

    @GetMapping("/api/claims")
    @PreAuthorize("hasRole('ADMINISTRATOR')")
    public List<ClaimResponse> listAllClaims(@RequestParam(required = false) String status) {
        if (status == null || status.isBlank()) {
            return damageClaimService.listAllClaims();
        }
        return damageClaimService.listClaimsByStatus(status);
    }

    @GetMapping("/api/claims/damage-report/{damageReportId}")
    @PreAuthorize("hasRole('ADMINISTRATOR')")
    public List<ClaimResponse> listClaimsForDamageReport(@PathVariable int damageReportId) {
        return damageClaimService.listClaimsForDamageReport(damageReportId);
    }

    @PatchMapping("/api/claims/{id}/status")
    @PreAuthorize("hasRole('ADMINISTRATOR')")
    public ResponseEntity<String> updateClaimStatus(@PathVariable int id,
                                                    @RequestParam String status,
                                                    @RequestParam(required = false) @Size(max = Rules.REASON, message = "Reason can be at most " + Rules.REASON + " characters") String reason,
                                                    @AuthenticationPrincipal AppUserPrincipal me) {
        damageClaimService.updateClaimStatus(id, status, me.getUserId(), reason);
        return ResponseEntity.ok("Claim status updated");
    }

    @DeleteMapping("/api/claims/{id}")
    @PreAuthorize("hasRole('ADMINISTRATOR')")
    public ResponseEntity<String> deleteClaim(@PathVariable int id,
                                              @AuthenticationPrincipal AppUserPrincipal me) {
        damageClaimService.deleteCancelledClaim(id, me.getUserId());
        return ResponseEntity.ok("Claim deleted");
    }
}
