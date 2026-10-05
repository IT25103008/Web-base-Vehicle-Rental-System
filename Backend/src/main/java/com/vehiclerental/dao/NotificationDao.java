package com.vehiclerental.dao;

import com.vehiclerental.model.Notification;

import java.util.List;
import java.util.Optional;

public interface NotificationDao {
    Notification save(Notification notification);
    Optional<Notification> findById(int notificationId);
    List<Notification> findByUser(int userId);
    List<Notification> findUnreadByUser(int userId);
    void markAsRead(int notificationId);
    int markAllRead(int userId);
    void delete(int notificationId);
    /** category -> [email, sms] */
    java.util.Map<String, boolean[]> findPreferences(int userId);
    void savePreference(int userId, String category, boolean email, boolean sms);
}
