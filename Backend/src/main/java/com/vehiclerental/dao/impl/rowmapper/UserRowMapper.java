package com.vehiclerental.dao.impl.rowmapper;

import com.vehiclerental.dao.RowMapper;
import com.vehiclerental.enums.Role;
import com.vehiclerental.model.Administrator;
import com.vehiclerental.model.Customer;
import com.vehiclerental.model.Staff;
import com.vehiclerental.model.User;

import java.sql.ResultSet;
import java.sql.SQLException;

/**
 * Maps a row from the users table to the correct User subclass
 * based on the 'role' column. This is where inheritance meets JDBC.
 */
public class UserRowMapper implements RowMapper<User> {

    @Override
    public User map(ResultSet rs) throws SQLException {

        String roleString = rs.getString("role");
        Role role = Role.valueOf(roleString);

        User user;
        switch (role) {
            case CUSTOMER:      user = mapCustomer(rs);      break;
            case STAFF:         user = mapStaff(rs);         break;
            case ADMINISTRATOR: user = mapAdmin(rs);         break;
            default: throw new SQLException("Unknown role: " + roleString);
        }

        // Common fields from the User base class
        user.setUserId(rs.getInt("user_id"));
        user.setEmail(rs.getString("email"));
        user.setPasswordHash(rs.getString("password_hash"));
        user.setFirstName(rs.getString("first_name"));
        user.setLastName(rs.getString("last_name"));
        user.setPhoneNumber(rs.getString("phone_number"));
        user.setAddress(rs.getString("address"));
        user.setActive(rs.getBoolean("active"));

        java.sql.Date dob = rs.getDate("date_of_birth");
        if (dob != null) user.setDateOfBirth(dob.toLocalDate());

        java.sql.Timestamp reg = rs.getTimestamp("registration_date");
        if (reg != null) user.setRegistrationDate(reg.toLocalDateTime());

        user.setEmailVerified(rs.getBoolean("email_verified"));
        java.sql.Timestamp consent = rs.getTimestamp("privacy_consent_at");
        if (consent != null) user.setPrivacyConsentAt(consent.toLocalDateTime());
        user.setTotpSecret(rs.getString("totp_secret"));
        user.setTotpEnabled(rs.getBoolean("totp_enabled"));
        java.sql.Timestamp erased = rs.getTimestamp("anonymised_at");
        if (erased != null) user.setAnonymisedAt(erased.toLocalDateTime());

        return user;
    }

    private Customer mapCustomer(ResultSet rs) throws SQLException {
        Customer c = new Customer();
        c.setDrivingLicenseNumber(rs.getString("driving_license_number"));
        c.setLicenseVerified(rs.getBoolean("license_verified"));
        java.sql.Date exp = rs.getDate("license_expiry_date");
        if (exp != null) c.setLicenseExpiryDate(exp.toLocalDate());
        return c;
    }

    private Staff mapStaff(ResultSet rs) throws SQLException {
        Staff s = new Staff();
        s.setEmployeeCode(rs.getString("employee_code"));
        java.sql.Date hire = rs.getDate("hire_date");
        if (hire != null) s.setHireDate(hire.toLocalDate());
        s.setPosition(rs.getString("position"));

        int branchId = rs.getInt("branch_id");
        if (!rs.wasNull()) s.setBranchId(branchId);

        int supervisorId = rs.getInt("supervisor_id");
        if (!rs.wasNull()) s.setSupervisorId(supervisorId);

        return s;
    }

    private Administrator mapAdmin(ResultSet rs) throws SQLException {
        Administrator a = new Administrator();
        a.setAdminCode(rs.getString("admin_code"));
        java.sql.Date app = rs.getDate("date_of_appointment");
        if (app != null) a.setDateOfAppointment(app.toLocalDate());
        a.setAdminLevel(rs.getString("admin_level"));
        return a;
    }
}
