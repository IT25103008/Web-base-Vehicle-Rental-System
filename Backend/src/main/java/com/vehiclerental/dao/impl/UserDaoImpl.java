package com.vehiclerental.dao.impl;

import com.vehiclerental.dao.AbstractJdbcDao;
import com.vehiclerental.dao.UserDao;
import com.vehiclerental.dao.impl.rowmapper.UserRowMapper;
import com.vehiclerental.model.Administrator;
import com.vehiclerental.model.Customer;
import com.vehiclerental.model.Staff;
import com.vehiclerental.model.User;
import org.springframework.stereotype.Repository;

import javax.sql.DataSource;
import java.sql.Date;
import java.util.List;
import java.util.Optional;

@Repository
public class UserDaoImpl extends AbstractJdbcDao<User, Integer> implements UserDao {

    private final UserRowMapper mapper = new UserRowMapper();

    public UserDaoImpl(DataSource dataSource) {
        super(dataSource);
    }

    @Override
    public User save(User user) {
        String sql =
            "INSERT INTO users (email, password_hash, first_name, last_name, phone_number, " +
            "address, role, active, date_of_birth, driving_license_number, license_verified, " +
            "license_expiry_date, employee_code, hire_date, position, branch_id, supervisor_id, " +
            "admin_code, date_of_appointment, admin_level, email_verified, privacy_consent_at) " +
            "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";

        // One value per column. Nulls for whichever subclass this user isn't.
        String drivingLicense = null;
        Boolean licenseVerified = null;
        Date licenseExpiry = null;
        String employeeCode = null;
        Date hireDate = null;
        String position = null;
        Integer branchId = null;
        Integer supervisorId = null;
        String adminCode = null;
        Date dateOfAppointment = null;
        String adminLevel = null;

        if (user instanceof Customer) {
            Customer c = (Customer) user;
            drivingLicense = c.getDrivingLicenseNumber();
            licenseVerified = c.isLicenseVerified();
            if (c.getLicenseExpiryDate() != null) licenseExpiry = Date.valueOf(c.getLicenseExpiryDate());
        } else if (user instanceof Staff) {
            Staff s = (Staff) user;
            employeeCode = s.getEmployeeCode();
            if (s.getHireDate() != null) hireDate = Date.valueOf(s.getHireDate());
            position = s.getPosition();
            branchId = s.getBranchId();
            supervisorId = s.getSupervisorId();
        } else if (user instanceof Administrator) {
            Administrator a = (Administrator) user;
            adminCode = a.getAdminCode();
            if (a.getDateOfAppointment() != null) dateOfAppointment = Date.valueOf(a.getDateOfAppointment());
            adminLevel = a.getAdminLevel();
        }

        Date dob = user.getDateOfBirth() == null ? null : Date.valueOf(user.getDateOfBirth());

        int generatedId = executeInsertReturnId(sql,
            user.getEmail(),
            user.getPasswordHash(),
            user.getFirstName(),
            user.getLastName(),
            user.getPhoneNumber(),
            user.getAddress(),
            user.getRole().name(),
            user.isActive(),
            dob,
            drivingLicense,
            licenseVerified,
            licenseExpiry,
            employeeCode,
            hireDate,
            position,
            branchId,
            supervisorId,
            adminCode,
            dateOfAppointment,
            adminLevel,
            user.isEmailVerified(),
            user.getPrivacyConsentAt() == null ? null : java.sql.Timestamp.valueOf(user.getPrivacyConsentAt())
        );
        user.setUserId(generatedId);
        return user;
    }

    @Override
    public Optional<User> findById(int userId) {
        String sql = "SELECT * FROM users WHERE user_id = ?";
        return queryOne(sql, mapper, userId);
    }

    @Override
    public Optional<User> findByEmail(String email) {
        String sql = "SELECT * FROM users WHERE email = ?";
        return queryOne(sql, mapper, email);
    }

    @Override
    public List<User> findAll() {
        String sql = "SELECT * FROM users ORDER BY user_id";
        return queryList(sql, mapper);
    }

    @Override
    public List<User> findByRole(String role) {
        String sql = "SELECT * FROM users WHERE role = ? ORDER BY user_id";
        return queryList(sql, mapper, role);
    }

    @Override
    public void updateLicenseVerified(int customerId, boolean verified) {
        String sql = "UPDATE users SET license_verified = ? WHERE user_id = ? AND role = 'CUSTOMER'";
        executeUpdate(sql, verified, customerId);
    }

    @Override
    public boolean existsByEmail(String email) {
        return findByEmail(email).isPresent();
    }

    @Override
    public void updateActive(int userId, boolean active) {
        executeUpdate("UPDATE users SET active = ? WHERE user_id = ?", active, userId);
    }

    @Override
    public List<User> findStaffByBranch(int branchId) {
        String sql = "SELECT * FROM users WHERE role = 'STAFF' AND branch_id = ? ORDER BY user_id";
        return queryList(sql, mapper, branchId);
    }

    @Override
    public void updateProfile(int userId, String firstName, String lastName, String phone, String address) {
        executeUpdate("UPDATE users SET first_name = ?, last_name = ?, phone_number = ?, address = ? "
                    + "WHERE user_id = ?", firstName, lastName, phone, address, userId);
    }

    @Override
    public void updateLicence(int customerId, String number, java.time.LocalDate expiry) {
        executeUpdate("UPDATE users SET driving_license_number = ?, license_expiry_date = ?, "
                    + "license_verified = FALSE WHERE user_id = ? AND role = 'CUSTOMER'",
                      number, expiry == null ? null : Date.valueOf(expiry), customerId);
    }

    @Override
    public void updatePasswordHash(int userId, String hash) {
        executeUpdate("UPDATE users SET password_hash = ? WHERE user_id = ?", hash, userId);
    }

    @Override
    public void updateEmailVerified(int userId, boolean verified) {
        executeUpdate("UPDATE users SET email_verified = ? WHERE user_id = ?", verified, userId);
    }

    @Override
    public void updateTotp(int userId, String secret, boolean enabled) {
        executeUpdate("UPDATE users SET totp_secret = ?, totp_enabled = ? WHERE user_id = ?",
                      secret, enabled, userId);
    }

    /**
     * Erasure under the PDPA: everything that identifies the person is
     * replaced, the sign-in is made impossible, and the row itself stays so
     * that bookings and payments the business must keep still add up.
     */
    @Override
    public void anonymise(int userId, String placeholderEmail, String unusableHash, java.time.LocalDateTime when) {
        executeUpdate("UPDATE users SET email = ?, password_hash = ?, first_name = 'Former', "
                    + "last_name = 'customer', phone_number = NULL, address = NULL, date_of_birth = NULL, "
                    + "driving_license_number = NULL, license_verified = FALSE, active = FALSE, "
                    + "totp_secret = NULL, totp_enabled = FALSE, anonymised_at = ? WHERE user_id = ?",
                      placeholderEmail, unusableHash, java.sql.Timestamp.valueOf(when), userId);
    }

    @Override
    public List<User> findActiveByRole(String role) {
        String sql = "SELECT * FROM users WHERE role = ? AND active = TRUE ORDER BY user_id";
        return queryList(sql, mapper, role);
    }

    /** Serialises booking changes for one customer (one car at a time, three open bookings). */
    @Override
    public void lockForUpdate(int userId) {
        queryRows("SELECT user_id FROM users WHERE user_id = ? FOR UPDATE", rs -> rs.getInt(1), userId);
    }

    @Override
    public List<User> findPage(String role, String text, int offset, int limit) {
        StringBuilder sql = new StringBuilder("SELECT * FROM users WHERE anonymised_at IS NULL ");
        List<Object> params = new java.util.ArrayList<>();
        userFilter(sql, params, role, text);
        sql.append("ORDER BY user_id DESC LIMIT ? OFFSET ?");
        params.add(limit);
        params.add(offset);
        return queryList(sql.toString(), mapper, params.toArray());
    }

    @Override
    public long count(String role, String text) {
        StringBuilder sql = new StringBuilder("SELECT COUNT(*) FROM users WHERE anonymised_at IS NULL ");
        List<Object> params = new java.util.ArrayList<>();
        userFilter(sql, params, role, text);
        return queryCount(sql.toString(), params.toArray());
    }

    private static void userFilter(StringBuilder sql, List<Object> params, String role, String text) {
        if (role != null && !role.isBlank()) {
            sql.append("AND role = ? ");
            params.add(role);
        }
        if (text != null && !text.isBlank()) {
            String like = "%" + text.trim().toLowerCase() + "%";
            sql.append("AND (LOWER(email) LIKE ? OR LOWER(CONCAT(first_name, ' ', last_name)) LIKE ?) ");
            params.add(like);
            params.add(like);
        }
    }
}
