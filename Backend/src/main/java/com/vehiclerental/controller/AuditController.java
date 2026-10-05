package com.vehiclerental.controller;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Max;
import com.vehiclerental.model.AuditEntry;
import com.vehiclerental.service.AuditService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Read-only view of the audit trail.
 *
 * "Who cancelled this booking, and when?" now has an answer that comes from the
 * data rather than from memory.
 */
@RestController
@RequestMapping("/api/audit")
@PreAuthorize("hasAnyRole('STAFF', 'ADMINISTRATOR')")
public class AuditController {

    private final AuditService auditService;

    public AuditController(AuditService auditService) {
        this.auditService = auditService;
    }

    /** Everything that ever happened to one record, newest first. */
    @GetMapping("/{entityType}/{entityId}")
    public List<AuditEntry> history(@PathVariable String entityType, @PathVariable int entityId) {
        return auditService.history(entityType.toUpperCase(), entityId);
    }

    /** The most recent activity across the whole system. */
    @GetMapping
    @PreAuthorize("hasRole('ADMINISTRATOR')")
    public List<AuditEntry> recent(@RequestParam(defaultValue = "100")
                                   @Min(value = 1, message = "Limit must be at least 1")
                                   @Max(value = 500, message = "Limit can be at most 500") int limit) {
        return auditService.recent(limit);
    }
}
