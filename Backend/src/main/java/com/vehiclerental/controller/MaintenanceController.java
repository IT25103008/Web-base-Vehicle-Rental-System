package com.vehiclerental.controller;

import com.vehiclerental.dto.request.CreateMaintenanceRequest;
import com.vehiclerental.dto.request.UpdateMaintenanceRequest;
import com.vehiclerental.dto.response.MaintenanceResponse;
import com.vehiclerental.security.AppUserPrincipal;
import com.vehiclerental.service.MaintenanceService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/maintenance")
@PreAuthorize("hasAnyRole('STAFF', 'ADMINISTRATOR')")
public class MaintenanceController {

    private final MaintenanceService maintenanceService;

    public MaintenanceController(MaintenanceService maintenanceService) {
        this.maintenanceService = maintenanceService;
    }

    @PostMapping
    public ResponseEntity<MaintenanceResponse> create(
            @Valid @RequestBody CreateMaintenanceRequest request,
            @AuthenticationPrincipal AppUserPrincipal me) {
        MaintenanceResponse r = maintenanceService.create(me.getUserId(), request);
        return ResponseEntity.ok(r);
    }

    /** Edit a service record (what can change depends on its status). */
    @PutMapping("/{id}")
    public MaintenanceResponse update(@PathVariable int id,
                                      @Valid @RequestBody UpdateMaintenanceRequest request,
                                      @AuthenticationPrincipal AppUserPrincipal me) {
        return maintenanceService.update(id, request, me.getUserId());
    }

    @GetMapping("/{id}")
    public MaintenanceResponse findById(@PathVariable int id) {
        return maintenanceService.findById(id);
    }

    @GetMapping
    public List<MaintenanceResponse> all() {
        return maintenanceService.listAll();
    }

    @GetMapping("/vehicle/{vehicleId}")
    public List<MaintenanceResponse> byVehicle(@PathVariable int vehicleId) {
        return maintenanceService.listByVehicle(vehicleId);
    }

    // Upcoming services in the next N days (default 30).
    @GetMapping("/reminders")
    public List<MaintenanceResponse> reminders(
            @RequestParam(name = "days", defaultValue = "30")
            @Min(value = 1, message = "Days must be at least 1") @Max(value = 366, message = "Days can be at most 366") int daysAhead) {
        return maintenanceService.listUpcoming(daysAhead);
    }

    @PatchMapping("/{id}/status")
    public ResponseEntity<String> updateStatus(@PathVariable int id,
                                               @RequestParam String status,
                                               @AuthenticationPrincipal AppUserPrincipal me) {
        maintenanceService.updateStatus(id, status, me.getUserId());
        return ResponseEntity.ok("Status updated");
    }

    @PatchMapping("/{id}/complete")
    public ResponseEntity<String> complete(@PathVariable int id,
                                           @AuthenticationPrincipal AppUserPrincipal me) {
        maintenanceService.completeMaintenance(id, me.getUserId());
        return ResponseEntity.ok("Maintenance completed and vehicle released");
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<String> delete(@PathVariable int id,
                                         @AuthenticationPrincipal AppUserPrincipal me) {
        maintenanceService.delete(id, me.getUserId());
        return ResponseEntity.ok("Maintenance record deleted");
    }
}
