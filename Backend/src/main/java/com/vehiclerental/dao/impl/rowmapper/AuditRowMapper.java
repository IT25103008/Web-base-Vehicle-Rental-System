package com.vehiclerental.dao.impl.rowmapper;

import com.vehiclerental.dao.RowMapper;
import com.vehiclerental.model.AuditEntry;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;

public class AuditRowMapper implements RowMapper<AuditEntry> {

    @Override
    public AuditEntry map(ResultSet rs) throws SQLException {
        AuditEntry a = new AuditEntry();
        a.setAuditId(rs.getInt("audit_id"));
        a.setEntityType(rs.getString("entity_type"));
        a.setEntityId(rs.getInt("entity_id"));
        a.setAction(rs.getString("action"));
        a.setFromStatus(rs.getString("from_status"));
        a.setToStatus(rs.getString("to_status"));

        int actor = rs.getInt("actor_user_id");
        if (!rs.wasNull()) a.setActorUserId(actor);

        a.setReason(rs.getString("reason"));

        Timestamp changed = rs.getTimestamp("changed_at");
        if (changed != null) a.setChangedAt(changed.toLocalDateTime());
        return a;
    }
}
