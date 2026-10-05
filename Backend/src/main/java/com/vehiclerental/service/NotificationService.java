package com.vehiclerental.service;

import com.vehiclerental.enums.NotificationChannel;
import com.vehiclerental.model.Notification;

import java.util.List;

public interface NotificationService {

    Notification send(int userId, String type, String message, NotificationChannel channel);

    // Overloaded version — polymorphism (overloading) demo.
    Notification send(int userId, String type, String message);

    /**
     * Send without ever throwing.
     *
     * Notifications are a courtesy, not part of the business transaction: a
     * failure to write one must never roll back the booking, payment or
     * handover that triggered it.
     */
    void safeSend(int userId, String type, String message);

    void safeSend(int userId, String type, String message, NotificationChannel channel);

    /** Tell the counter staff of one branch about something they need to act on. */
    void safeSendToBranchStaff(Integer branchId, String type, String message);

    /** Tell every administrator about something that needs a decision. */
    void safeSendToAdministrators(String type, String message);

    List<Notification> listByUser(int userId);

    List<Notification> listUnreadByUser(int userId);

    void markAsRead(int notificationId, int callerUserId);

    /** Marks everything the caller has read; returns how many changed. */
    int markAllRead(int userId);

    /** Removes one of the caller's own notifications. */
    void delete(int notificationId, int callerUserId);

    /** Whether a category of update is also emailed (and texted, once an SMS provider is connected). */
    record Preference(String category, String label, boolean email, boolean sms) { }

    List<Preference> preferences(int userId);

    void savePreferences(int userId, List<Preference> preferences);
}
