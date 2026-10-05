package com.vehiclerental.controller;

import com.vehiclerental.dto.request.CreateAdministratorRequest;
import com.vehiclerental.dto.request.CreateStaffRequest;
import com.vehiclerental.dto.request.ReasonRequest;
import com.vehiclerental.dto.response.PageResponse;
import com.vehiclerental.dto.response.UserResponse;
import com.vehiclerental.security.AppUserPrincipal;
import com.vehiclerental.service.UserService;
import com.vehiclerental.validation.Rules;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/users")
@PreAuthorize("hasAnyRole('STAFF', 'ADMINISTRATOR')")
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    /** Everyone, with licence numbers masked (lists should not carry them). */
    @GetMapping
    public List<UserResponse> list(@RequestParam(required = false) String role) {
        return userService.listUsers(role);
    }

    /** One page of people, newest first (C1). */
    @GetMapping("/page")
    public PageResponse<UserResponse> page(@RequestParam(required = false) String role,
                                           @RequestParam(required = false) @Size(max = Rules.SEARCH_TEXT, message = "Search text is too long") String q,
                                           @RequestParam(required = false) Integer page,
                                           @RequestParam(required = false) Integer size) {
        return userService.listPage(role, q, page, size);
    }

    /**
     * One person with everything on file, including the full licence number
     * staff compare against the card at the counter. Every look is audited.
     */
    @GetMapping("/{id}")
    public UserResponse get(@PathVariable int id, @AuthenticationPrincipal AppUserPrincipal me) {
        return userService.getForStaff(id, me.getUserId());
    }

    @PostMapping("/staff")
    @PreAuthorize("hasRole('ADMINISTRATOR')")
    public ResponseEntity<UserResponse> createStaff(@Valid @RequestBody CreateStaffRequest request) {
        return ResponseEntity.ok(userService.createStaff(request));
    }

    @PostMapping("/administrators")
    @PreAuthorize("hasRole('ADMINISTRATOR')")
    public ResponseEntity<UserResponse> createAdministrator(
            @Valid @RequestBody CreateAdministratorRequest request) {
        return ResponseEntity.ok(userService.createAdministrator(request));
    }

    @PatchMapping("/{id}/verify-license")
    public UserResponse verifyLicense(@PathVariable int id,
                                      @AuthenticationPrincipal AppUserPrincipal me) {
        userService.verifyLicense(id, me.getUserId());
        return current(id);
    }

    @PatchMapping("/{id}/unverify-license")
    public UserResponse unverifyLicense(@PathVariable int id,
                                        @RequestParam(required = false) String reason,
                                        @Valid @RequestBody(required = false) ReasonRequest body,
                                        @AuthenticationPrincipal AppUserPrincipal me) {
        userService.unverifyLicense(id, me.getUserId(), BookingController.reasonOf(body, reason));
        return current(id);
    }

    /** Disable an account so it can no longer sign in. */
    @PatchMapping("/{id}/disable")
    @PreAuthorize("hasRole('ADMINISTRATOR')")
    public UserResponse disable(@PathVariable int id,
                                @RequestParam(required = false) String reason,
                                @Valid @RequestBody(required = false) ReasonRequest body,
                                @AuthenticationPrincipal AppUserPrincipal me) {
        String why = BookingController.reasonOf(body, reason);
        if (why == null || why.isBlank()) {
            throw new IllegalArgumentException("Disabling an account requires a reason");
        }
        userService.setActive(id, false, me.getUserId(), why);
        return current(id);
    }

    @PatchMapping("/{id}/enable")
    @PreAuthorize("hasRole('ADMINISTRATOR')")
    public UserResponse enable(@PathVariable int id,
                               @RequestParam(required = false) String reason,
                               @Valid @RequestBody(required = false) ReasonRequest body,
                               @AuthenticationPrincipal AppUserPrincipal me) {
        String why = BookingController.reasonOf(body, reason);
        userService.setActive(id, true, me.getUserId(), why == null ? "Account re-enabled" : why);
        return current(id);
    }

    private UserResponse current(int id) {
        return userService.findById(id).map(userService::toResponse)
            .orElseThrow(() -> new com.vehiclerental.exception.ResourceNotFoundException("User not found: " + id));
    }
}
