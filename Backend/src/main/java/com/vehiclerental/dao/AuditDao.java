package com.vehiclerental.dao;

import com.vehiclerental.model.AuditEntry;

import java.util.List;

public interface AuditDao {
    AuditEntry save(AuditEntry entry);
    List<AuditEntry> findByEntity(String entityType, int entityId);
    List<AuditEntry> findRecent(int limit);
}
