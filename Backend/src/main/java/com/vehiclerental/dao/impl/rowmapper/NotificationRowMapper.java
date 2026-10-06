package com.vehiclerental.dao.impl.rowmapper;

import com.vehiclerental.dao.RowMapper;
import com.vehiclerental.enums.NotificationChannel;
import com.vehiclerental.model.Notification;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;

public class NotificationRowMapper implements RowMapper<Notification> {

    @Override
    public Notification map(ResultSet rs) throws SQLException {
        Notification n = new Notification();
        n.setNotificationId(rs.getInt("notification_id"));
        n.setUserId(rs.getInt("user_id"));
        n.setType(rs.getString("type"));
        n.setMessage(rs.getString("message"));

        Timestamp sent = rs.getTimestamp("sent_at");
        if (sent != null) {
            n.setSentAt(sent.toLocalDateTime());
        }

        n.setChannel(NotificationChannel.valueOf(rs.getString("channel")));
        n.setReadStatus(rs.getBoolean("read_status"));
        n.setEntityType(rs.getString("entity_type"));
        int entityId = rs.getInt("entity_id");
        n.setEntityId(rs.wasNull() ? null : entityId);
        return n;
    }
}
