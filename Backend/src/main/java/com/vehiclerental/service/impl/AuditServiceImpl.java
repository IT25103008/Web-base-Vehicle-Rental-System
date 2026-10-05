package com.vehiclerental.service.impl;

import com.vehiclerental.dao.AuditDao;
import com.vehiclerental.model.AuditEntry;
import com.vehiclerental.service.AuditService;
import com.vehiclerental.util.AppClock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class AuditServiceImpl implements AuditService {

    private static final Logger log = LoggerFactory.getLogger(AuditServiceImpl.class);

    private final AuditDao auditDao;

    public AuditServiceImpl(AuditDao auditDao) {
        this.auditDao = auditDao;
    }

    @Override
    public void recordStatusChange(String entityType, int entityId,
                                   String fromStatus, String toStatus,
                                   Integer actorUserId, String reason) {
        write(entityType, entityId, "STATUS_CHANGE", fromStatus, toStatus, actorUserId, reason);
    }

    @Override
    public void record(String entityType, int entityId, String action,
                       Integer actorUserId, String reason) {
        write(entityType, entityId, action, null, null, actorUserId, reason);
    }

    @Override
    public List<AuditEntry> history(String entityType, int entityId) {
        return auditDao.findByEntity(entityType, entityId);
    }

    @Override
    public List<AuditEntry> recent(int limit) {
        return auditDao.findRecent(limit <= 0 || limit > 500 ? 100 : limit);
    }

    private void write(String entityType, int entityId, String action,
                       String from, String to, Integer actor, String reason) {
        AuditEntry e = new AuditEntry();
        e.setEntityType(entityType);
        e.setEntityId(entityId);
        e.setAction(action);
        e.setFromStatus(from);
        e.setToStatus(to);
        e.setActorUserId(actor);
        e.setReason(reason);
        e.setChangedAt(AppClock.now());
        try {
            auditDao.save(e);
        } catch (RuntimeException ex) {
            // The audit trail must never be the reason a legitimate business
            // operation fails. Log loudly instead.
            log.error("Failed to write audit entry {} {} -> {}", entityType, entityId, to, ex);
        }
    }
}
