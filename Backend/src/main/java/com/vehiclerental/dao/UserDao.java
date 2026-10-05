package com.vehiclerental.dao;

import com.vehiclerental.model.User;
import java.util.List;
import java.util.Optional;

public interface UserDao {
    void lockForUpdate(int userId);
    /** One page of people (C1), newest first; role null = everyone. Erased accounts are left out. */
    List<User> findPage(String role, String text, int offset, int limit);
    long count(String role, String text);
    void updateProfile(int userId, String firstName, String lastName, String phone, String address);
    /** A new licence number or expiry must be checked again, so this also clears license_verified. */
    void updateLicence(int customerId, String number, java.time.LocalDate expiry);
    void updatePasswordHash(int userId, String hash);
    void updateEmailVerified(int userId, boolean verified);
    void updateTotp(int userId, String secret, boolean enabled);
    void anonymise(int userId, String placeholderEmail, String unusableHash, java.time.LocalDateTime when);


    User save(User user);                          // returns user with generated id
    Optional<User> findById(int userId);
    Optional<User> findByEmail(String email);
    List<User> findAll();
    List<User> findByRole(String role);
    void updateLicenseVerified(int customerId, boolean verified);
    void updateActive(int userId, boolean active);
    List<User> findStaffByBranch(int branchId);
    List<User> findActiveByRole(String role);
    boolean existsByEmail(String email);
}
