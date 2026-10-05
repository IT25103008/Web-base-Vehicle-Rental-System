package com.vehiclerental.service;

import com.vehiclerental.dto.request.CreateAdministratorRequest;
import com.vehiclerental.dto.request.CreateStaffRequest;
import com.vehiclerental.dto.request.RegisterCustomerRequest;
import com.vehiclerental.dto.response.UserResponse;
import com.vehiclerental.model.Customer;
import com.vehiclerental.model.User;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface UserService {

    UserResponse registerCustomer(RegisterCustomerRequest request);

    UserResponse createStaff(CreateStaffRequest request);

    UserResponse createAdministrator(CreateAdministratorRequest request);

    /** Mark a customer's driving licence as checked. Refuses an expired licence. */
    void verifyLicense(int customerId, int actorUserId);

    /** Withdraw a licence verification (licence expired, document turned out to be false). */
    void unverifyLicense(int customerId, int actorUserId, String reason);

    /** Enable or disable an account. A disabled account cannot sign in. */
    void setActive(int userId, boolean active, int actorUserId, String reason);

    Optional<User> findById(int userId);

    List<UserResponse> listUsers(String role);     // role == null -> everyone

    /**
     * Throws unless this customer is allowed to take a vehicle out over the
     * given dates: account enabled, old enough, licence verified and valid for
     * the whole rental.
     */
    Customer requireEligibleToBook(int customerId, LocalDate pickupDate, LocalDate returnDate);

    UserResponse toResponse(User user);
    UserResponse toFullResponse(User user);
    UserResponse getForStaff(int userId, int actorUserId);
    com.vehiclerental.dto.response.PageResponse<UserResponse> listPage(String role, String text, Integer page, Integer size);
}
