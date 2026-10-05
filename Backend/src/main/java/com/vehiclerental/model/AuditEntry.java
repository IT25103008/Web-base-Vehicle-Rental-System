package com.vehiclerental.model;

import java.time.LocalDateTime;

/**
 * One row of the audit trail. Every status change in the system writes one of
 * these, so questions like "who cancelled booking 14, when, and why?" can be
 * answered from the data instead of guessed.
 */
public class AuditEntry {

    private int auditId;
    private String entityType;      // BOOKING / PAYMENT / VEHICLE / USER / ...
    private int entityId;
    private String action;          // CREATE / STATUS_CHANGE / UPDATE / DELETE
    private String fromStatus;
    private String toStatus;
    private Integer actorUserId;    // null for system/scheduled actions
    private String reason;
    private LocalDateTime changedAt;

    public AuditEntry() {
    }

    public int getAuditId() { return auditId; }
    public void setAuditId(int auditId) { this.auditId = auditId; }

    public String getEntityType() { return entityType; }
    public void setEntityType(String entityType) { this.entityType = entityType; }

    public int getEntityId() { return entityId; }
    public void setEntityId(int entityId) { this.entityId = entityId; }

    public String getAction() { return action; }
    public void setAction(String action) { this.action = action; }

    public String getFromStatus() { return fromStatus; }
    public void setFromStatus(String fromStatus) { this.fromStatus = fromStatus; }

    public String getToStatus() { return toStatus; }
    public void setToStatus(String toStatus) { this.toStatus = toStatus; }

    public Integer getActorUserId() { return actorUserId; }
    public void setActorUserId(Integer actorUserId) { this.actorUserId = actorUserId; }

    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }

    public LocalDateTime getChangedAt() { return changedAt; }
    public void setChangedAt(LocalDateTime changedAt) { this.changedAt = changedAt; }
}
