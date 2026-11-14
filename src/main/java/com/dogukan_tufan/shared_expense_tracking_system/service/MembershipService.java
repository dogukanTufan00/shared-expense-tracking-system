package com.dogukan_tufan.shared_expense_tracking_system.service;

import com.dogukan_tufan.shared_expense_tracking_system.dto.household.MemberResponse;
import com.dogukan_tufan.shared_expense_tracking_system.dto.household.UpdateMemberRoleRequest;
import com.dogukan_tufan.shared_expense_tracking_system.entity.Membership;
import com.dogukan_tufan.shared_expense_tracking_system.entity.MembershipRole;
import com.dogukan_tufan.shared_expense_tracking_system.entity.User;
import com.dogukan_tufan.shared_expense_tracking_system.exception.ConflictException;
import com.dogukan_tufan.shared_expense_tracking_system.exception.ResourceNotFoundException;
import com.dogukan_tufan.shared_expense_tracking_system.repository.MembershipRepository;
import com.dogukan_tufan.shared_expense_tracking_system.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional
public class MembershipService {

    private final UserRepository userRepository;
    private final MembershipRepository membershipRepository;
    private final HouseholdAccessService accessService;
    private final ActivityLogService activityLogService;
    private final ChoreService choreService;

    public List<MemberResponse> listActiveMembers(Long householdId, String currentUserEmail) {
        User currentUser = getUser(currentUserEmail);
        // Any active member may view the member list, not just admins -- only visibility is checked here.
        accessService.requireActiveMembership(currentUser.getId(), householdId);

        return membershipRepository.findByHouseholdIdAndLeftAtIsNull(householdId).stream()
                .map(MemberResponse::from)
                .toList();
    }

    // Removing someone else is an admin action. Leaving (userId == your own id) is a member's own
    // choice, so no admin role is needed. Their debts stay in the balances after they leave, because
    // leaving is only a soft delete (leftAt is set and the row is kept).
    public void removeMember(Long householdId, Long targetUserId, String currentUserEmail) {
        User currentUser = getUser(currentUserEmail);
        Membership requesterMembership = accessService.requireActiveMembership(currentUser.getId(), householdId);
        boolean isLeaving = currentUser.getId().equals(targetUserId);
        if (!isLeaving) {
            accessService.requireAdmin(requesterMembership);
        }

        Membership targetMembership = membershipRepository
                .findByUserIdAndHouseholdIdAndLeftAtIsNull(targetUserId, householdId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "No active member with user id " + targetUserId + " in this household"));

        accessService.requireFounderProtectionAllows(targetMembership, currentUser.getId());

        // Without at least one admin, nobody could add/rename/delete the household anymore.
        if (targetMembership.getRole() == MembershipRole.ADMIN
                && membershipRepository.countByHouseholdIdAndRoleAndLeftAtIsNull(householdId, MembershipRole.ADMIN) <= 1) {
            throw new ConflictException(isLeaving
                    ? "The last admin cannot leave; make someone else admin first"
                    : "The last admin of a household cannot be removed");
        }

        // Soft delete: leftAt set, row kept. Past expenses still point at a real Membership/User.
        targetMembership.setLeftAt(Instant.now());
        // Takes the person off the household's chore rotations, so they are not expected to do chores anymore.
        choreService.removeFromRotations(householdId, targetUserId);

        if (isLeaving) {
            activityLogService.memberLeft(targetMembership.getHousehold(), currentUser);
        } else {
            activityLogService.memberRemoved(targetMembership.getHousehold(), currentUser, targetMembership.getUser());
        }
    }

    // Promote or demote a member. Admin rights can be shared this way: promote someone else, then
    // the old admin can demote themselves or leave. Full founder handover is HouseholdService.transferOwnership.
    public MemberResponse changeRole(Long householdId, Long targetUserId, UpdateMemberRoleRequest request,
                                     String currentUserEmail) {
        User currentUser = getUser(currentUserEmail);
        Membership requesterMembership = accessService.requireActiveMembership(currentUser.getId(), householdId);
        accessService.requireAdmin(requesterMembership);

        Membership targetMembership = membershipRepository
                .findByUserIdAndHouseholdIdAndLeftAtIsNull(targetUserId, householdId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "No active member with user id " + targetUserId + " in this household"));

        accessService.requireFounderProtectionAllows(targetMembership, currentUser.getId());

        // Same role requested (including promoting an existing admin): nothing to change, still 200.
        if (targetMembership.getRole() == request.role()) {
            return MemberResponse.from(targetMembership);
        }

        // Demoting the only admin would leave the household without anyone who can manage it.
        if (targetMembership.getRole() == MembershipRole.ADMIN
                && membershipRepository.countByHouseholdIdAndRoleAndLeftAtIsNull(householdId, MembershipRole.ADMIN) <= 1) {
            throw new ConflictException("The last admin of a household cannot be demoted; make someone else admin first");
        }

        MembershipRole oldRole = targetMembership.getRole();
        targetMembership.setRole(request.role());
        activityLogService.memberRoleChanged(targetMembership.getHousehold(), currentUser,
                targetMembership.getUser(), oldRole, request.role());
        return MemberResponse.from(targetMembership);
    }

    private User getUser(String email) {
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + email));
    }
}
