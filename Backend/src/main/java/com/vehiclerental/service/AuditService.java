package com.vehiclerental.service;

import com.vehiclerental.model.AuditEntry;

import java.util.List;

public interface AuditService {

    /** Record a status change. actorUserId may be null for scheduled/system actions. */
    void recordStatusChange(String entityType, int entityId,
                            String fromStatus, String toStatus,
                            Integer actorUserId, String reason);

    /** Record something that is not a status change (create, update, delete). */
    void record(String entityType, int entityId, String action,
                Integer actorUserId, String reason);

    List<AuditEntry> history(String entityType, int entityId);

    List<AuditEntry> recent(int limit);
}
