package com.vehiclerental.dao.impl;

import com.vehiclerental.dao.AbstractJdbcDao;
import com.vehiclerental.dao.NotificationDao;
import com.vehiclerental.dao.impl.rowmapper.NotificationRowMapper;
import com.vehiclerental.model.Notification;
import com.vehiclerental.util.AppClock;
import org.springframework.stereotype.Repository;

import javax.sql.DataSource;
import java.sql.Timestamp;
import java.util.List;
import java.util.Optional;

@Repository
public class NotificationDaoImpl extends AbstractJdbcDao<Notification, Integer>
        implements NotificationDao {

    private static final NotificationRowMapper MAPPER = new NotificationRowMapper();

    public NotificationDaoImpl(DataSource dataSource) {
        super(dataSource);
    }

    @Override
    public Notification save(Notification n) {
        String sql = "INSERT INTO notifications " +
                "(user_id, type, message, sent_at, channel, read_status, entity_type, entity_id) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?)";

        Timestamp when = n.getSentAt() != null
                ? Timestamp.valueOf(n.getSentAt())
                : Timestamp.valueOf(AppClock.now());

        int newId = executeInsertReturnId(sql,
                n.getUserId(),
                n.getType(),
                n.getMessage(),
                when,
                n.getChannel().name(),
                n.isReadStatus(),
                n.getEntityType(),
                n.getEntityId()
        );
        n.setNotificationId(newId);
        return n;
    }

    @Override
    public Optional<Notification> findById(int notificationId) {
        String sql = "SELECT * FROM notifications WHERE notification_id = ?";
        return queryOne(sql, MAPPER, notificationId);
    }

    @Override
    public List<Notification> findByUser(int userId) {
        String sql = "SELECT * FROM notifications WHERE user_id = ? ORDER BY sent_at DESC";
        return queryList(sql, MAPPER, userId);
    }

    @Override
    public List<Notification> findUnreadByUser(int userId) {
        String sql = "SELECT * FROM notifications " +
                "WHERE user_id = ? AND read_status = FALSE " +
                "ORDER BY sent_at DESC";
        return queryList(sql, MAPPER, userId);
    }

    @Override
    public int markAllRead(int userId) {
        return executeUpdate("UPDATE notifications SET read_status = TRUE WHERE user_id = ? AND read_status = FALSE", userId);
    }

    @Override
    public void delete(int notificationId) {
        executeUpdate("DELETE FROM notifications WHERE notification_id = ?", notificationId);
    }

    @Override
    public java.util.Map<String, boolean[]> findPreferences(int userId) {
        java.util.Map<String, boolean[]> out = new java.util.LinkedHashMap<>();
        queryRows("SELECT category, email, sms FROM notification_preferences WHERE user_id = ?", rs -> {
            out.put(rs.getString("category"), new boolean[] { rs.getBoolean("email"), rs.getBoolean("sms") });
            return null;
        }, userId);
        return out;
    }

    @Override
    public void savePreference(int userId, String category, boolean email, boolean sms) {
        int updated = executeUpdate("UPDATE notification_preferences SET email = ?, sms = ? WHERE user_id = ? AND category = ?",
                                    email, sms, userId, category);
        if (updated == 0) {
            executeUpdate("INSERT INTO notification_preferences (user_id, category, email, sms) VALUES (?, ?, ?, ?)",
                          userId, category, email, sms);
        }
    }

    @Override
    public void markAsRead(int notificationId) {
        String sql = "UPDATE notifications SET read_status = TRUE WHERE notification_id = ?";
        executeUpdate(sql, notificationId);
    }
}
