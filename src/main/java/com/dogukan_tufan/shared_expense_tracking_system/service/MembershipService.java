package com.dogukan_tufan.shared_expense_tracking_system.service;

import com.dogukan_tufan.shared_expense_tracking_system.dto.household.MemberResponse;
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

    public List<MemberResponse> listActiveMembers(Long householdId, String currentUserEmail) {
        User currentUser = getUser(currentUserEmail);
        // Any active member may view the member list, not just admins -- only visibility is checked here.
        accessService.requireActiveMembership(currentUser.getId(), householdId);

        return membershipRepository.findByHouseholdIdAndLeftAtIsNull(householdId).stream()
                .map(MemberResponse::from)
                .toList();
    }

    public void removeMember(Long householdId, Long targetUserId, String currentUserEmail) {
        User currentUser = getUser(currentUserEmail);
        Membership requesterMembership = accessService.requireActiveMembership(currentUser.getId(), householdId);
        accessService.requireAdmin(requesterMembership);

        Membership targetMembership = membershipRepository
                .findByUserIdAndHouseholdIdAndLeftAtIsNull(targetUserId, householdId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "No active member with user id " + targetUserId + " in this household"));

        // Without at least one admin, nobody could add/rename/delete the household anymore.
        if (targetMembership.getRole() == MembershipRole.ADMIN
                && membershipRepository.countByHouseholdIdAndRoleAndLeftAtIsNull(householdId, MembershipRole.ADMIN) <= 1) {
            throw new ConflictException("The last admin of a household cannot be removed");
        }

        // Soft delete: leftAt set, row kept. Past expenses still point at a real Membership/User.
        targetMembership.setLeftAt(Instant.now());
    }

    private User getUser(String email) {
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + email));
    }
}
