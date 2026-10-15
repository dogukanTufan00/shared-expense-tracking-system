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
}
