package com.vehiclerental.service;

import com.vehiclerental.model.Branch;
import com.vehiclerental.model.VehicleTransfer;

import java.time.LocalDate;
import java.util.List;

public interface BranchService {

    Branch create(Branch branch, int actorUserId);

    Branch findById(int branchId);

    List<Branch> findAll();

    Branch update(int branchId, Branch updates, int actorUserId);

    /** Close a branch to new business. Refused while it still has commitments. */
    void deactivate(int branchId, int actorUserId);

    void reactivate(int branchId, int actorUserId);

    void delete(int branchId, int actorUserId);

    // Vehicle transfers between branches
    VehicleTransfer requestTransfer(int vehicleId, int toBranchId, LocalDate transferDate,
                                    String reason, int actorUserId);

    void cancelTransfer(int transferId, int actorUserId);

    void completeTransfer(int transferId, int actorUserId);

    List<VehicleTransfer> listPendingTransfers();

    List<VehicleTransfer> listAllTransfers();
}
