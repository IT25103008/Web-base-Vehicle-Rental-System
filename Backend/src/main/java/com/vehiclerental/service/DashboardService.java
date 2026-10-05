package com.vehiclerental.service;

import com.vehiclerental.dto.response.DashboardResponse;

public interface DashboardService {
    DashboardResponse getDashboard();

    /** The same figures for one branch (null = the whole company). */
    DashboardResponse getDashboard(Integer branchId);
}
