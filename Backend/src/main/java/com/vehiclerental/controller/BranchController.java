package com.vehiclerental.controller;

import com.vehiclerental.dto.request.BranchRequest;
import com.vehiclerental.dto.request.TransferRequest;
import com.vehiclerental.model.Branch;
import com.vehiclerental.model.VehicleTransfer;
import com.vehiclerental.security.AppUserPrincipal;
import com.vehiclerental.service.BranchService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/branches")
public class BranchController {

    private final BranchService branchService;

    public BranchController(BranchService branchService) {
        this.branchService = branchService;
    }

    // Public
    @GetMapping
    public List<Branch> list() {
        return branchService.findAll();
    }

    // Public
    @GetMapping("/{id}")
    public Branch getOne(@PathVariable int id) {
        return branchService.findById(id);
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMINISTRATOR')")
    public ResponseEntity<Branch> create(@Valid @RequestBody BranchRequest body,
                                         @AuthenticationPrincipal AppUserPrincipal me) {
        return ResponseEntity.ok(branchService.create(body.toModel(), me.getUserId()));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMINISTRATOR')")
    public Branch update(@PathVariable int id, @Valid @RequestBody BranchRequest body,
                         @AuthenticationPrincipal AppUserPrincipal me) {
        return branchService.update(id, body.toModel(), me.getUserId());
    }

    @PatchMapping("/{id}/deactivate")
    @PreAuthorize("hasRole('ADMINISTRATOR')")
    public ResponseEntity<String> deactivate(@PathVariable int id,
                                             @AuthenticationPrincipal AppUserPrincipal me) {
        branchService.deactivate(id, me.getUserId());
        return ResponseEntity.ok("Branch deactivated");
    }

    @PatchMapping("/{id}/activate")
    @PreAuthorize("hasRole('ADMINISTRATOR')")
    public ResponseEntity<String> activate(@PathVariable int id,
                                           @AuthenticationPrincipal AppUserPrincipal me) {
        branchService.reactivate(id, me.getUserId());
        return ResponseEntity.ok("Branch re-opened");
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMINISTRATOR')")
    public ResponseEntity<String> delete(@PathVariable int id,
                                         @AuthenticationPrincipal AppUserPrincipal me) {
        branchService.delete(id, me.getUserId());
        return ResponseEntity.ok("Branch deleted");
    }

    // Vehicle transfers

    @PostMapping("/transfers")
    @PreAuthorize("hasRole('ADMINISTRATOR')")
    public ResponseEntity<VehicleTransfer> requestTransfer(@Valid @RequestBody TransferRequest r,
                                                           @AuthenticationPrincipal AppUserPrincipal me) {
        VehicleTransfer t = branchService.requestTransfer(
            r.getVehicleId(), r.getToBranchId(), r.getTransferDate(), r.getReason(), me.getUserId());
        return ResponseEntity.ok(t);
    }

    @GetMapping("/transfers")
    @PreAuthorize("hasRole('ADMINISTRATOR')")
    public List<VehicleTransfer> allTransfers() {
        return branchService.listAllTransfers();
    }

    @GetMapping("/transfers/pending")
    @PreAuthorize("hasRole('ADMINISTRATOR')")
    public List<VehicleTransfer> pendingTransfers() {
        return branchService.listPendingTransfers();
    }

    @PatchMapping("/transfers/{id}/complete")
    @PreAuthorize("hasRole('ADMINISTRATOR')")
    public ResponseEntity<String> completeTransfer(@PathVariable int id,
                                                   @AuthenticationPrincipal AppUserPrincipal me) {
        branchService.completeTransfer(id, me.getUserId());
        return ResponseEntity.ok("Transfer completed");
    }

    @PatchMapping("/transfers/{id}/cancel")
    @PreAuthorize("hasRole('ADMINISTRATOR')")
    public ResponseEntity<String> cancelTransfer(@PathVariable int id,
                                                 @AuthenticationPrincipal AppUserPrincipal me) {
        branchService.cancelTransfer(id, me.getUserId());
        return ResponseEntity.ok("Transfer cancelled");
    }
}
