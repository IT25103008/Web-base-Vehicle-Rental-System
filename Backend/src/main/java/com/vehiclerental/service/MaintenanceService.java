package com.vehiclerental.service;

import com.vehiclerental.dto.request.CreateMaintenanceRequest;
import com.vehiclerental.dto.request.UpdateMaintenanceRequest;
import com.vehiclerental.dto.response.MaintenanceResponse;

import java.util.List;

public interface MaintenanceService {

    MaintenanceResponse create(int staffId, CreateMaintenanceRequest request);

    MaintenanceResponse findById(int eventId);

    /** Edits a service record. What may change depends on its status. */
    MaintenanceResponse update(int eventId, UpdateMaintenanceRequest request, int actorUserId);

    List<MaintenanceResponse> listAll();

    List<MaintenanceResponse> listByVehicle(int vehicleId);

    /** Services whose next-service reminder falls inside the next N days. */
    List<MaintenanceResponse> listUpcoming(int daysAhead);

    void updateStatus(int eventId, String newStatus, int actorUserId);

    void completeMaintenance(int eventId, int actorUserId);

    void delete(int eventId, int actorUserId);
}
