package com.vehiclerental.dao.impl;

import com.vehiclerental.dao.AbstractJdbcDao;
import com.vehiclerental.dao.AuditDao;
import com.vehiclerental.dao.impl.rowmapper.AuditRowMapper;
import com.vehiclerental.model.AuditEntry;
import org.springframework.stereotype.Repository;

import javax.sql.DataSource;
import java.sql.Timestamp;
import java.util.List;

@Repository
public class AuditDaoImpl extends AbstractJdbcDao<AuditEntry, Integer> implements AuditDao {

    private static final AuditRowMapper MAPPER = new AuditRowMapper();

    public AuditDaoImpl(DataSource dataSource) {
        super(dataSource);
    }

    @Override
    public AuditEntry save(AuditEntry e) {
        String sql = "INSERT INTO audit_log " +
                "(entity_type, entity_id, action, from_status, to_status, actor_user_id, reason, changed_at) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?)";
        int id = executeInsertReturnId(sql,
                e.getEntityType(),
                e.getEntityId(),
                e.getAction(),
                e.getFromStatus(),
                e.getToStatus(),
                e.getActorUserId(),
                e.getReason(),
                Timestamp.valueOf(e.getChangedAt()));
        e.setAuditId(id);
        return e;
    }

    @Override
    public List<AuditEntry> findByEntity(String entityType, int entityId) {
        String sql = "SELECT * FROM audit_log WHERE entity_type = ? AND entity_id = ? " +
                "ORDER BY changed_at DESC, audit_id DESC";
        return queryList(sql, MAPPER, entityType, entityId);
    }

    @Override
    public List<AuditEntry> findRecent(int limit) {
        String sql = "SELECT * FROM audit_log ORDER BY changed_at DESC, audit_id DESC LIMIT ?";
        return queryList(sql, MAPPER, limit);
    }
}
