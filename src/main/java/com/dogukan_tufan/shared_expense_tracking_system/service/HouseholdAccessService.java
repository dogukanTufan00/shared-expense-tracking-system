package com.dogukan_tufan.shared_expense_tracking_system.service;

import com.dogukan_tufan.shared_expense_tracking_system.entity.Membership;
import com.dogukan_tufan.shared_expense_tracking_system.entity.MembershipRole;
import com.dogukan_tufan.shared_expense_tracking_system.exception.ForbiddenActionException;
import com.dogukan_tufan.shared_expense_tracking_system.exception.ResourceNotFoundException;
import com.dogukan_tufan.shared_expense_tracking_system.repository.MembershipRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

// The two authorization checks every household-scoped endpoint needs (households, members,
// and later expenses): "can this user see this household at all" and "are they an admin of it".
// Pulled out of the individual services because all three need exactly this, not because it
// might be useful "someday".
@Service
@RequiredArgsConstructor
public class HouseholdAccessService {

    private final MembershipRepository membershipRepository;

    public Membership requireActiveMembership(Long userId, Long householdId) {
        return membershipRepository.findByUserIdAndHouseholdIdAndLeftAtIsNull(userId, householdId)
                .orElseThrow(() -> new ResourceNotFoundException("Household not found: " + householdId));
    }

    public void requireAdmin(Membership membership) {
        if (membership.getRole() != MembershipRole.ADMIN) {
            throw new ForbiddenActionException("Only an admin of this household can do that");
        }
    }

    // Founder protection: while the founder is still an active member, nobody else may change
    // their role or remove them. Callers must pass an ACTIVE target membership (they look it up
    // first), so "target is the founder" already implies the founder is still living here.
    // If the founder has left, the lookup fails with 404 before this check runs -- which is the
    // point: a house must not stay locked to someone who moved out, so all admins are equal then.
    public void requireFounderProtectionAllows(Membership target, Long actingUserId) {
        Long targetUserId = target.getUser().getId();
        boolean targetIsFounder = target.getHousehold().getCreatedBy().getId().equals(targetUserId);
        if (targetIsFounder && !actingUserId.equals(targetUserId)) {
            throw new ForbiddenActionException("The household founder can only be changed by the founder");
        }
    }
}
