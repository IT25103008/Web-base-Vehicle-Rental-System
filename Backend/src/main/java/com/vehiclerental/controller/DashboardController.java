package com.vehiclerental.controller;

import com.vehiclerental.dto.response.DashboardResponse;
import com.vehiclerental.security.AppUserPrincipal;
import com.vehiclerental.security.BranchGuard;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.RequestParam;
import com.vehiclerental.service.DashboardService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/dashboard")
@PreAuthorize("hasAnyRole('STAFF', 'ADMINISTRATOR')")
public class DashboardController {

    private final DashboardService dashboardService;
    private final BranchGuard branchGuard;

    public DashboardController(DashboardService dashboardService, BranchGuard branchGuard) {
        this.dashboardService = dashboardService;
        this.branchGuard = branchGuard;
    }

    /**
     * Staff see their own branch (C6). An administrator sees the whole
     * company, or one branch with ?branchId=.
     */
    @GetMapping
    public DashboardResponse dashboard(@RequestParam(required = false) Integer branchId,
                                       @AuthenticationPrincipal AppUserPrincipal me) {
        Integer scope = branchGuard.scopeFor(me.getUserId());
        return dashboardService.getDashboard(scope != null ? scope : branchId);
    }
}
