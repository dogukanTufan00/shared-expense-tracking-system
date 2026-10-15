package com.dogukan_tufan.shared_expense_tracking_system.service;

import com.dogukan_tufan.shared_expense_tracking_system.dto.household.AddMemberRequest;
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

    // Easy to change later (CLAUDE.md section 7: "a constant, easy to change later").
    private static final int MAX_ACTIVE_MEMBERS = 3;

    private final UserRepository userRepository;
    private final MembershipRepository membershipRepository;
    private final HouseholdAccessService accessService;

    public MemberResponse addMember(Long householdId, AddMemberRequest request, String currentUserEmail) {
        User currentUser = getUser(currentUserEmail);
        Membership requesterMembership = accessService.requireActiveMembership(currentUser.getId(), householdId);
        accessService.requireAdmin(requesterMembership);

        User target = userRepository.findByEmail(request.email())
                .orElseThrow(() -> new ResourceNotFoundException("No user with email: " + request.email()));

        boolean alreadyActive = membershipRepository
                .findByUserIdAndHouseholdIdAndLeftAtIsNull(target.getId(), householdId)
                .isPresent();
        if (alreadyActive) {
            throw new ConflictException("User is already an active member of this household");
        }

        long activeCount = membershipRepository.countByHouseholdIdAndLeftAtIsNull(householdId);
        if (activeCount >= MAX_ACTIVE_MEMBERS) {
            throw new ConflictException("Household already has the maximum of " + MAX_ACTIVE_MEMBERS + " active members");
        }

        Membership membership = new Membership(target, requesterMembership.getHousehold(), MembershipRole.MEMBER);
        membershipRepository.save(membership);

        return MemberResponse.from(membership);
    }

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

        // Soft delete: leftAt set, row kept. Past expenses still point at a real Membership/User.
        targetMembership.setLeftAt(Instant.now());
    }

    private User getUser(String email) {
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + email));
    }
}
