package com.vehiclerental.service.impl;

import com.vehiclerental.dao.NotificationDao;
import com.vehiclerental.dao.UserDao;
import com.vehiclerental.enums.NotificationChannel;
import com.vehiclerental.enums.Role;
import com.vehiclerental.exception.ResourceNotFoundException;
import com.vehiclerental.exception.UnauthorizedActionException;
import com.vehiclerental.model.Notification;
import com.vehiclerental.model.User;
import com.vehiclerental.service.MailService;
import com.vehiclerental.service.NotificationService;
import com.vehiclerental.util.AppClock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class NotificationServiceImpl implements NotificationService {

    private static final Logger log = LoggerFactory.getLogger(NotificationServiceImpl.class);

    /** The categories a person can choose email for, with their default. */
    private static final String[][] CATEGORIES = {
        // key, label, email by default
        { "BOOKINGS", "Bookings: approved, declined, cancelled, picked up, returned", "true" },
        { "PAYMENTS", "Payments: received, refunds, balances due", "true" },
        { "REMINDERS", "Reminders: returns due, overdue, missed pick-ups", "false" },
        { "ACCOUNT", "Everything else", "false" },
    };

    private static final Pattern BOOKING_REF = Pattern.compile("(?i)booking\\s*#(\\d+)");

    private final NotificationDao notificationDao;
    private final UserDao userDao;
    private final MailService mail;

    public NotificationServiceImpl(NotificationDao notificationDao, UserDao userDao, MailService mail) {
        this.notificationDao = notificationDao;
        this.userDao = userDao;
        this.mail = mail;
    }

    @Override
    public Notification send(int userId, String type, String message, NotificationChannel channel) {
        Notification n = new Notification();
        n.setUserId(userId);
        n.setType(type);
        n.setMessage(message);
        n.setSentAt(AppClock.now());
        n.setChannel(channel);
        n.setReadStatus(false);
        linkTo(n);
        Notification saved = notificationDao.save(n);
        dispatch(saved);
        return saved;
    }

    // Overloaded version — polymorphism (overloading) demo.
    @Override
    public Notification send(int userId, String type, String message) {
        return send(userId, type, message, NotificationChannel.WEBSITE);
    }

    @Override
    public void safeSend(int userId, String type, String message) {
        safeSend(userId, type, message, NotificationChannel.WEBSITE);
    }

    @Override
    public void safeSend(int userId, String type, String message, NotificationChannel channel) {
        try {
            send(userId, type, message, channel);
        } catch (RuntimeException e) {
            log.error("Could not send {} notification to user {}", type, userId, e);
        }
    }

    @Override
    public void safeSendToBranchStaff(Integer branchId, String type, String message) {
        if (branchId == null) {
            return;
        }
        try {
            for (User staff : userDao.findStaffByBranch(branchId)) {
                if (staff.isActive()) {
                    safeSend(staff.getUserId(), type, message);
                }
            }
        } catch (RuntimeException e) {
            log.error("Could not notify staff of branch {}", branchId, e);
        }
    }

    @Override
    public void safeSendToAdministrators(String type, String message) {
        try {
            for (User admin : userDao.findActiveByRole(Role.ADMINISTRATOR.name())) {
                safeSend(admin.getUserId(), type, message);
            }
        } catch (RuntimeException e) {
            log.error("Could not notify administrators about {}", type, e);
        }
    }

    @Override
    public List<Notification> listByUser(int userId) {
        return notificationDao.findByUser(userId);
    }

    @Override
    public List<Notification> listUnreadByUser(int userId) {
        return notificationDao.findUnreadByUser(userId);
    }

    @Override
    public void markAsRead(int notificationId, int callerUserId) {
        notificationDao.markAsRead(ownedBy(notificationId, callerUserId).getNotificationId());
    }

    @Override
    public int markAllRead(int userId) {
        return notificationDao.markAllRead(userId);
    }

    @Override
    public void delete(int notificationId, int callerUserId) {
        notificationDao.delete(ownedBy(notificationId, callerUserId).getNotificationId());
    }

    @Override
    public List<Preference> preferences(int userId) {
        Map<String, boolean[]> saved = notificationDao.findPreferences(userId);
        List<Preference> out = new ArrayList<>();
        for (String[] c : CATEGORIES) {
            boolean[] v = saved.get(c[0]);
            out.add(new Preference(c[0], c[1], v == null ? Boolean.parseBoolean(c[2]) : v[0], v != null && v[1]));
        }
        return out;
    }

    @Override
    public void savePreferences(int userId, List<Preference> preferences) {
        if (preferences == null) {
            return;
        }
        for (Preference p : preferences) {
            if (isCategory(p.category())) {
                notificationDao.savePreference(userId, p.category(), p.email(), p.sms());
            }
        }
    }

    // ------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------
    private Notification ownedBy(int notificationId, int callerUserId) {
        Notification n = notificationDao.findById(notificationId)
                .orElseThrow(() -> new ResourceNotFoundException("Notification not found: " + notificationId));
        if (n.getUserId() != callerUserId) {
            throw new UnauthorizedActionException("You can only change your own notifications");
        }
        return n;
    }

    /**
     * Records what the notification is about, so the app can link straight to
     * it instead of guessing from the wording. Every booking-related message
     * names its booking as "booking #N".
     */
    private static void linkTo(Notification n) {
        if (n.getEntityType() != null || n.getMessage() == null) {
            return;
        }
        Matcher m = BOOKING_REF.matcher(n.getMessage());
        if (m.find()) {
            n.setEntityType("BOOKING");
            n.setEntityId(Integer.parseInt(m.group(1)));
        }
    }

    static String categoryOf(String type) {
        String t = type == null ? "" : type;
        if (t.startsWith("PAYMENT")) return "PAYMENTS";
        if (t.startsWith("RETURN_") || t.startsWith("PICKUP_") || t.startsWith("INSURANCE_")
                || t.startsWith("MAINTENANCE") || t.startsWith("SERVICE")) return "REMINDERS";
        if (t.startsWith("BOOKING") || t.startsWith("VEHICLE")) return "BOOKINGS";
        return "ACCOUNT";
    }

    private static boolean isCategory(String key) {
        for (String[] c : CATEGORIES) {
            if (c[0].equals(key)) return true;
        }
        return false;
    }

    /**
     * WEBSITE notifications are delivered by the bell in the web app, which
     * reads them straight from this table. On top of that, each person chooses
     * per category whether they also want an email; an explicit EMAIL channel
     * always emails. SMS has no gateway wired up yet: it is logged, not sent,
     * and the row stays as the record of what should have gone out.
     */
    private void dispatch(Notification n) {
        try {
            User u = userDao.findById(n.getUserId()).orElse(null);
            if (u == null || !u.isActive()) {
                return;
            }
            String category = categoryOf(n.getType());
            Preference pref = preferences(u.getUserId()).stream()
                .filter(p -> p.category().equals(category)).findFirst().orElse(null);
            boolean email = n.getChannel() == NotificationChannel.EMAIL || (pref != null && pref.email());
            boolean sms = n.getChannel() == NotificationChannel.SMS || (pref != null && pref.sms());
            if (email && u.isEmailVerified()) {
                mail.send(u.getEmail(), "Axle: " + humanise(n.getType()),
                          "Hello " + u.getFirstName() + ",\n\n" + n.getMessage()
                          + "\n\nYou can change which updates are emailed on your account page.\n\n- Axle");
            }
            if (sms) {
                log.info("[SMS NOT DELIVERED - no gateway configured] to user {}: {}", n.getUserId(), n.getMessage());
            }
        } catch (RuntimeException e) {
            log.warn("Could not deliver notification {} beyond the website", n.getNotificationId(), e);
        }
    }

    private static String humanise(String type) {
        if (type == null) return "an update";
        String s = type.replace('_', ' ').toLowerCase();
        return Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }
}
