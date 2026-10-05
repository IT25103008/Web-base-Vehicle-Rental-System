package com.vehiclerental.controller;

import com.vehiclerental.service.ReminderService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * The daily reminder run happens by itself at 07:00 (see ReminderServiceImpl).
 * This endpoint lets an administrator trigger it on demand — useful when
 * demonstrating the system rather than waiting until tomorrow morning.
 */
@RestController
@RequestMapping("/api/reminders")
@PreAuthorize("hasRole('ADMINISTRATOR')")
public class ReminderController {

    private final ReminderService reminderService;

    public ReminderController(ReminderService reminderService) {
        this.reminderService = reminderService;
    }

    @PostMapping("/run")
    public ResponseEntity<ReminderService.ReminderSummary> run() {
        return ResponseEntity.ok(reminderService.runDailyChecks());
    }
}
