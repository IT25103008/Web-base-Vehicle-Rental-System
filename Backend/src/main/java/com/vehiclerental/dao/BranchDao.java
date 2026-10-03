package com.vehiclerental.dao;

import com.vehiclerental.model.Branch;

import java.util.List;
import java.util.Optional;

public interface BranchDao {

    Branch save(Branch branch);
    Optional<Branch> findById(int branchId);
    List<Branch> findAll();
    void update(Branch branch);
    void updateStatus(int branchId, String status);      // ACTIVE / INACTIVE
    void delete(int branchId);
}
