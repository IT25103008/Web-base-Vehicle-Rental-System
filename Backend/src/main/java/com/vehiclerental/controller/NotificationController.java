package com.vehiclerental.controller;

import com.vehiclerental.model.Notification;
import com.vehiclerental.security.AppUserPrincipal;
import com.vehiclerental.service.NotificationService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/notifications")
public class NotificationController {

    private final NotificationService notificationService;

    public NotificationController(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    // Anyone logged in reads their OWN notifications only.
    @GetMapping("/mine")
    public List<Notification> myNotifications(@AuthenticationPrincipal AppUserPrincipal me) {
        return notificationService.listByUser(me.getUserId());
    }

    @GetMapping("/mine/unread")
    public List<Notification> myUnread(@AuthenticationPrincipal AppUserPrincipal me) {
        return notificationService.listUnreadByUser(me.getUserId());
    }

    @PatchMapping("/{id}/read")
    public ResponseEntity<java.util.Map<String, Object>> markRead(@PathVariable int id,
                                                                 @AuthenticationPrincipal AppUserPrincipal me) {
        notificationService.markAsRead(id, me.getUserId());
        return ResponseEntity.ok(java.util.Map.of("notificationId", id, "readStatus", true));
    }

    /** Everything read in one call, instead of one request per notification. */
    @PatchMapping("/mine/read-all")
    public java.util.Map<String, Object> markAllRead(@AuthenticationPrincipal AppUserPrincipal me) {
        return java.util.Map.of("marked", notificationService.markAllRead(me.getUserId()));
    }

    @DeleteMapping("/{id}")
    public java.util.Map<String, Object> delete(@PathVariable int id, @AuthenticationPrincipal AppUserPrincipal me) {
        notificationService.delete(id, me.getUserId());
        return java.util.Map.of("deleted", id);
    }
}
