package com.vehiclerental.security;

import com.vehiclerental.dao.BranchDao;
import com.vehiclerental.dao.UserDao;
import com.vehiclerental.enums.Role;
import com.vehiclerental.exception.ResourceNotFoundException;
import com.vehiclerental.exception.UnauthorizedActionException;
import com.vehiclerental.model.Branch;
import com.vehiclerental.model.Staff;
import com.vehiclerental.model.User;
import org.springframework.stereotype.Component;

/**
 * Staff act for their own branch; administrators act for all of them.
 *
 * A Galle clerk must not approve, cancel, hand over or take money for a
 * Colombo booking. Handovers always checked this; approvals, cancellations and
 * payments now go through the same rule, and staff lists default to the
 * caller's own branch.
 */
@Component
public class BranchGuard {

    private final UserDao userDao;
    private final BranchDao branchDao;

    public BranchGuard(UserDao userDao, BranchDao branchDao) {
        this.userDao = userDao;
        this.branchDao = branchDao;
    }

    /** Throws unless the actor is an administrator or staff at this branch. */
    public void requireStaffAtBranch(int actorUserId, int branchId, String action) {
        User u = userDao.findById(actorUserId)
            .orElseThrow(() -> new ResourceNotFoundException("Staff member not found: " + actorUserId));
        if (u.getRole() == Role.ADMINISTRATOR) {
            return;
        }
        if (!(u instanceof Staff staff)) {
            throw new UnauthorizedActionException("Only staff can " + action);
        }
        Integer own = staff.getBranchId();
        if (own == null || own != branchId) {
            String name = branchDao.findById(branchId).map(Branch::getName).orElse("another branch");
            throw new UnauthorizedActionException(
                "This booking belongs to " + name + ". Only staff at that branch can " + action + ".");
        }
    }

    /**
     * The branch a person's lists are limited to: their own for staff, none
     * (null = every branch) for administrators and anyone else.
     */
    public Integer scopeFor(int actorUserId) {
        return userDao.findById(actorUserId)
            .filter(u -> u.getRole() == Role.STAFF && u instanceof Staff)
            .map(u -> ((Staff) u).getBranchId())
            .orElse(null);
    }
}
