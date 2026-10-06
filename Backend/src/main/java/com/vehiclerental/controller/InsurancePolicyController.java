package com.vehiclerental.controller;

import com.vehiclerental.dto.request.InsurancePolicyRequest;
import com.vehiclerental.model.InsurancePolicy;
import com.vehiclerental.security.AppUserPrincipal;
import com.vehiclerental.service.InsurancePolicyService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/insurance-policies")
@PreAuthorize("hasAnyRole('STAFF', 'ADMINISTRATOR')")
public class InsurancePolicyController {

    private final InsurancePolicyService insurancePolicyService;

    public InsurancePolicyController(InsurancePolicyService insurancePolicyService) {
        this.insurancePolicyService = insurancePolicyService;
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMINISTRATOR')")
    public ResponseEntity<InsurancePolicy> create(@Valid @RequestBody InsurancePolicyRequest body,
                                                  @AuthenticationPrincipal AppUserPrincipal me) {
        return ResponseEntity.ok(insurancePolicyService.create(body.toModel(), me.getUserId()));
    }

    @GetMapping("/{id}")
    public InsurancePolicy findById(@PathVariable int id) {
        return insurancePolicyService.findById(id);
    }

    @GetMapping
    public List<InsurancePolicy> all(@RequestParam(required = false) Integer vehicleId) {
        if (vehicleId != null) {
            return insurancePolicyService.listByVehicle(vehicleId);
        }
        return insurancePolicyService.listAll();
    }

    // Policies expiring in the next N days (default 30) — used for the
    // staff reminders screen.
    @GetMapping("/expiring")
    public List<InsurancePolicy> expiring(
            @RequestParam(name = "days", defaultValue = "30")
            @Min(value = 1, message = "Days must be at least 1") @Max(value = 366, message = "Days can be at most 366") int daysAhead) {
        return insurancePolicyService.listExpiring(daysAhead);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMINISTRATOR')")
    public InsurancePolicy update(@PathVariable int id, @Valid @RequestBody InsurancePolicyRequest body,
                                  @AuthenticationPrincipal AppUserPrincipal me) {
        InsurancePolicy policy = body.toModel();
        policy.setPolicyId(id);
        return insurancePolicyService.update(policy, me.getUserId());
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMINISTRATOR')")
    public ResponseEntity<String> delete(@PathVariable int id,
                                         @AuthenticationPrincipal AppUserPrincipal me) {
        insurancePolicyService.delete(id, me.getUserId());
        return ResponseEntity.ok("Insurance policy deleted");
    }
}
