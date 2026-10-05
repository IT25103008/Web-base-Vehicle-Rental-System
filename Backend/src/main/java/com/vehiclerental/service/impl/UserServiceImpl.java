package com.vehiclerental.service.impl;

import com.vehiclerental.dao.BranchDao;
import com.vehiclerental.dao.UserDao;
import com.vehiclerental.dto.request.CreateAdministratorRequest;
import com.vehiclerental.dto.request.CreateStaffRequest;
import com.vehiclerental.dto.request.RegisterCustomerRequest;
import com.vehiclerental.dto.response.UserResponse;
import com.vehiclerental.enums.Role;
import com.vehiclerental.exception.ResourceNotFoundException;
import com.vehiclerental.exception.UnauthorizedActionException;
import com.vehiclerental.model.*;
import com.vehiclerental.service.AuditService;
import com.vehiclerental.service.UserService;
import com.vehiclerental.util.AppClock;
import com.vehiclerental.util.PasswordPolicy;
import com.vehiclerental.util.RentalPolicy;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.Period;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class UserServiceImpl implements UserService {

    private final UserDao userDao;
    private final BranchDao branchDao;
    private final PasswordEncoder passwordEncoder;
    private final AuditService auditService;

    public UserServiceImpl(UserDao userDao, BranchDao branchDao,
                           PasswordEncoder passwordEncoder, AuditService auditService) {
        this.userDao = userDao;
        this.branchDao = branchDao;
        this.passwordEncoder = passwordEncoder;
        this.auditService = auditService;
    }

    // ============================================================
    // Registration / creation
    // ============================================================
    @Override
    @Transactional
    public UserResponse registerCustomer(RegisterCustomerRequest r) {

        String email = normaliseEmail(r.getEmail());
        if (userDao.existsByEmail(email)) {
            throw new IllegalArgumentException("Email already registered: " + email);
        }
        PasswordPolicy.require(r.getPassword());

        // A driver must be old enough, and must hold a licence that has not expired.
        if (r.getDateOfBirth() == null) {
            throw new IllegalArgumentException("Date of birth is required");
        }
        int age = Period.between(r.getDateOfBirth(), AppClock.today()).getYears();
        if (age < RentalPolicy.MIN_DRIVER_AGE) {
            throw new IllegalArgumentException(
                "You must be at least " + RentalPolicy.MIN_DRIVER_AGE + " years old to register");
        }
        if (r.getDateOfBirth().isAfter(AppClock.today())) {
            throw new IllegalArgumentException("Date of birth cannot be in the future");
        }
        if (r.getLicenseExpiryDate() == null) {
            throw new IllegalArgumentException("Driving licence expiry date is required");
        }
        if (!r.getLicenseExpiryDate().isAfter(AppClock.today())) {
            throw new IllegalArgumentException("Your driving licence has already expired");
        }

        Customer c = new Customer();
        c.setEmail(email);
        c.setPasswordHash(passwordEncoder.encode(r.getPassword()));
        c.setFirstName(r.getFirstName());
        c.setLastName(r.getLastName());
        c.setPhoneNumber(r.getPhoneNumber());
        c.setAddress(r.getAddress());
        c.setDateOfBirth(r.getDateOfBirth());
        c.setDrivingLicenseNumber(r.getDrivingLicenseNumber());
        c.setLicenseExpiryDate(r.getLicenseExpiryDate());
        c.setLicenseVerified(false);              // staff must verify manually
        c.setActive(true);
        c.setEmailVerified(false);                // confirmed from the link we email
        if (!r.isPrivacyConsent()) {
            throw new IllegalArgumentException("Please read and accept the privacy notice to create an account");
        }
        c.setPrivacyConsentAt(AppClock.now());

        User saved = userDao.save(c);
        auditService.record("USER", saved.getUserId(), "CREATE", saved.getUserId(),
                            "Customer self-registered");
        return toResponse(saved);
    }

    @Override
    @Transactional
    public UserResponse createStaff(CreateStaffRequest r) {

        String email = normaliseEmail(r.getEmail());
        if (userDao.existsByEmail(email)) {
            throw new IllegalArgumentException("Email already registered: " + email);
        }
        PasswordPolicy.require(r.getPassword());

        Branch branch = branchDao.findById(r.getBranchId())
            .orElseThrow(() -> new ResourceNotFoundException("Branch not found: " + r.getBranchId()));
        if (!"ACTIVE".equals(branch.getStatus())) {
            throw new IllegalArgumentException("Cannot assign staff to a closed branch");
        }
        if (r.getHireDate() != null && r.getHireDate().isAfter(AppClock.today())) {
            throw new IllegalArgumentException("Hire date cannot be in the future");
        }

        if (r.getSupervisorId() != null) {
            User supervisor = userDao.findById(r.getSupervisorId())
                .orElseThrow(() -> new ResourceNotFoundException("Supervisor not found: " + r.getSupervisorId()));
            if (supervisor.getRole() != Role.STAFF) {
                throw new IllegalArgumentException("Supervisor must be a staff member");
            }
            if (!supervisor.isActive()) {
                throw new IllegalArgumentException("Supervisor account is disabled");
            }
            Staff sup = (Staff) supervisor;
            if (sup.getBranchId() != null && !sup.getBranchId().equals(r.getBranchId())) {
                throw new IllegalArgumentException("A supervisor must work at the same branch");
            }
        }

        Staff s = new Staff();
        s.setEmail(email);
        s.setPasswordHash(passwordEncoder.encode(r.getPassword()));
        s.setFirstName(r.getFirstName());
        s.setLastName(r.getLastName());
        s.setPhoneNumber(r.getPhoneNumber());
        s.setAddress(r.getAddress());
        s.setEmployeeCode(r.getEmployeeCode());
        s.setHireDate(r.getHireDate());
        s.setPosition(r.getPosition());
        s.setBranchId(r.getBranchId());
        s.setSupervisorId(r.getSupervisorId());
        s.setActive(true);

        User saved = userDao.save(s);
        auditService.record("USER", saved.getUserId(), "CREATE", null,
                            "Staff account created at branch " + r.getBranchId());
        return toResponse(saved);
    }

    @Override
    @Transactional
    public UserResponse createAdministrator(CreateAdministratorRequest r) {

        String email = normaliseEmail(r.getEmail());
        if (userDao.existsByEmail(email)) {
            throw new IllegalArgumentException("Email already registered: " + email);
        }
        PasswordPolicy.require(r.getPassword());

        Administrator a = new Administrator();
        a.setEmail(email);
        a.setPasswordHash(passwordEncoder.encode(r.getPassword()));
        a.setFirstName(r.getFirstName());
        a.setLastName(r.getLastName());
        a.setPhoneNumber(r.getPhoneNumber());
        a.setAddress(r.getAddress());
        a.setAdminCode(r.getAdminCode());
        a.setAdminLevel(r.getAdminLevel() == null ? "STANDARD" : r.getAdminLevel().toUpperCase());
        a.setDateOfAppointment(r.getDateOfAppointment() == null ? AppClock.today() : r.getDateOfAppointment());
        a.setActive(true);

        User saved = userDao.save(a);
        auditService.record("USER", saved.getUserId(), "CREATE", null, "Administrator account created");
        return toResponse(saved);
    }

    // ============================================================
    // Licence and account state
    // ============================================================
    @Override
    @Transactional
    public void verifyLicense(int customerId, int actorUserId) {
        Customer c = loadCustomer(customerId);

        if (c.getLicenseExpiryDate() == null) {
            throw new IllegalArgumentException(
                "This customer has no licence expiry date on file — it cannot be verified");
        }
        if (!c.getLicenseExpiryDate().isAfter(AppClock.today())) {
            throw new IllegalArgumentException(
                "This driving licence expired on " + c.getLicenseExpiryDate() + " and cannot be verified");
        }
        if (c.isLicenseVerified()) {
            return;   // already done; verifying twice is not an error
        }

        userDao.updateLicenseVerified(customerId, true);
        auditService.recordStatusChange("USER", customerId, "LICENCE_UNVERIFIED", "LICENCE_VERIFIED",
                                        actorUserId, "Licence checked at the counter");
    }

    @Override
    @Transactional
    public void unverifyLicense(int customerId, int actorUserId, String reason) {
        loadCustomer(customerId);
        userDao.updateLicenseVerified(customerId, false);
        auditService.recordStatusChange("USER", customerId, "LICENCE_VERIFIED", "LICENCE_UNVERIFIED",
                                        actorUserId, reason);
    }

    @Override
    @Transactional
    public void setActive(int userId, boolean active, int actorUserId, String reason) {
        User u = userDao.findById(userId)
            .orElseThrow(() -> new ResourceNotFoundException("User not found: " + userId));

        if (u.getUserId() == actorUserId && !active) {
            throw new UnauthorizedActionException("You cannot disable your own account");
        }
        if (!active && u.getRole() == Role.ADMINISTRATOR && lastActiveAdministrator(userId)) {
            throw new IllegalArgumentException(
                "This is the last active administrator — create another one before disabling this account");
        }
        if (u.isActive() == active) {
            return;
        }

        userDao.updateActive(userId, active);
        auditService.recordStatusChange("USER", userId, u.isActive() ? "ACTIVE" : "DISABLED",
                                        active ? "ACTIVE" : "DISABLED", actorUserId, reason);
    }

    // ============================================================
    // Queries
    // ============================================================
    @Override
    public Optional<User> findById(int userId) {
        return userDao.findById(userId);
    }

    @Override
    public List<UserResponse> listUsers(String role) {
        List<User> users;
        if (role == null || role.isBlank()) {
            users = userDao.findAll();
        } else {
            users = userDao.findByRole(Role.valueOf(role.toUpperCase()).name());
        }
        return users.stream().map(this::toResponse).collect(Collectors.toList());
    }

    /**
     * The gate every rental passes through: the person driving away must have an
     * enabled account, be old enough, and hold a verified licence that stays
     * valid for the whole trip.
     */
    @Override
    public Customer requireEligibleToBook(int customerId, LocalDate pickupDate, LocalDate returnDate) {
        User u = userDao.findById(customerId)
            .orElseThrow(() -> new ResourceNotFoundException("Customer not found: " + customerId));

        if (!(u instanceof Customer)) {
            throw new UnauthorizedActionException("Only customers can book vehicles");
        }
        Customer c = (Customer) u;

        if (!c.isActive()) {
            throw new UnauthorizedActionException(
                "This account has been disabled. Please contact the branch.");
        }
        if (c.getDateOfBirth() != null) {
            int ageAtPickup = Period.between(c.getDateOfBirth(), pickupDate).getYears();
            if (ageAtPickup < RentalPolicy.MIN_DRIVER_AGE) {
                throw new UnauthorizedActionException(
                    "Drivers must be at least " + RentalPolicy.MIN_DRIVER_AGE + " years old");
            }
        }
        if (!c.isLicenseVerified()) {
            throw new UnauthorizedActionException(
                "Your driving licence has not been verified yet. Please visit a branch with your licence.");
        }
        if (!c.isEmailVerified()) {
            throw new UnauthorizedActionException(
                "Please confirm your email address first. We sent you a link - you can ask for a new one "
                + "from your account page.");
        }
        if (c.getLicenseExpiryDate() == null) {
            throw new UnauthorizedActionException("No driving licence expiry date on file");
        }
        LocalDate mustCoverUntil = returnDate.plusDays(RentalPolicy.LICENCE_MUST_OUTLAST_RETURN_BY_DAYS);
        if (c.getLicenseExpiryDate().isBefore(mustCoverUntil)) {
            throw new UnauthorizedActionException(
                "Your driving licence expires on " + c.getLicenseExpiryDate()
                + ", before the end of this rental (" + returnDate + ")");
        }
        return c;
    }

    // ============================================================
    // Helpers
    // ============================================================
    private Customer loadCustomer(int customerId) {
        User user = userDao.findById(customerId)
            .orElseThrow(() -> new ResourceNotFoundException("User not found: " + customerId));
        if (!(user instanceof Customer)) {
            throw new IllegalArgumentException("User " + customerId + " is not a customer");
        }
        return (Customer) user;
    }

    private boolean lastActiveAdministrator(int excludingUserId) {
        Set<Integer> others = new HashSet<>();
        for (User u : userDao.findActiveByRole(Role.ADMINISTRATOR.name())) {
            if (u.getUserId() != excludingUserId) {
                others.add(u.getUserId());
            }
        }
        return others.isEmpty();
    }

    private String normaliseEmail(String email) {
        return email == null ? null : email.trim().toLowerCase();
    }

    /**
     * One person, with everything on file - for staff checking a licence at
     * the counter. Looking at someone's full licence number is recorded.
     */
    @Override
    public UserResponse getForStaff(int userId, int actorUserId) {
        User u = userDao.findById(userId)
            .orElseThrow(() -> new ResourceNotFoundException("User not found: " + userId));
        if (u instanceof Customer) {
            auditService.record("USER", userId, "VIEW", actorUserId, "Full licence details viewed");
        }
        return toFullResponse(u);
    }

    @Override
    public com.vehiclerental.dto.response.PageResponse<UserResponse> listPage(String role, String text,
                                                                             Integer page, Integer size) {
        int s = com.vehiclerental.dto.response.PageResponse.size(size);
        int pg = com.vehiclerental.dto.response.PageResponse.page(page);
        String r = role == null || role.isBlank() || "all".equalsIgnoreCase(role) ? null : Role.valueOf(role.toUpperCase()).name();
        java.util.Map<String, Long> counts = new java.util.LinkedHashMap<>();
        for (Role each : Role.values()) {
            counts.put(each.name(), userDao.count(each.name(), text));
        }
        counts.put("all", userDao.count(null, text));
        List<UserResponse> items = userDao.findPage(r, text, (pg - 1) * s, s).stream()
            .map(this::toResponse).collect(Collectors.toList());
        return new com.vehiclerental.dto.response.PageResponse<>(items, pg, s, userDao.count(r, text), counts);
    }

    /** Everything on file: for the person themselves, or a single audited staff lookup. */
    @Override
    public UserResponse toFullResponse(User u) {
        UserResponse r = toResponse(u);
        if (u instanceof Customer c) {
            r.setDrivingLicenseNumber(c.getDrivingLicenseNumber());
        }
        r.setFirstName(u.getFirstName());
        r.setLastName(u.getLastName());
        r.setPhoneNumber(u.getPhoneNumber());
        r.setAddress(u.getAddress());
        r.setDateOfBirth(u.getDateOfBirth());
        r.setRegistrationDate(u.getRegistrationDate());
        r.setTotpEnabled(u.isTotpEnabled());
        r.setPrivacyConsentAt(u.getPrivacyConsentAt());
        return r;
    }

    /** Last four characters only, e.g. "B1234567" -> "****4567". */
    static String maskLicence(String number) {
        if (number == null || number.isBlank()) {
            return number;
        }
        String n = number.trim();
        return n.length() <= 4 ? "****" : "****" + n.substring(n.length() - 4);
    }

    // Manual entity -> DTO conversion (no MapStruct). Used for lists, so the
    // licence number is masked: whole lists of people should not carry it.
    @Override
    public UserResponse toResponse(User u) {
        UserResponse r = new UserResponse(u.getUserId(), u.getEmail(), u.getFullName(), u.getRole().name());
        r.setActive(u.isActive());
        r.setEmailVerified(u.isEmailVerified());
        if (u instanceof Customer) {
            Customer c = (Customer) u;
            r.setLicenseVerified(c.isLicenseVerified());
            r.setDrivingLicenseNumber(maskLicence(c.getDrivingLicenseNumber()));
            r.setLicenseExpiryDate(c.getLicenseExpiryDate());
        } else if (u instanceof Staff) {
            Staff s = (Staff) u;
            r.setBranchId(s.getBranchId());
            r.setEmployeeCode(s.getEmployeeCode());
            r.setPosition(s.getPosition());
        }
        return r;
    }
}
