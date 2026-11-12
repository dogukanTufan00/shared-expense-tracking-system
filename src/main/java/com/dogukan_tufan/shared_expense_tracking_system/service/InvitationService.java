package com.dogukan_tufan.shared_expense_tracking_system.service;

import com.dogukan_tufan.shared_expense_tracking_system.dto.household.HouseholdResponse;
import com.dogukan_tufan.shared_expense_tracking_system.dto.invitation.InviteRequest;
import com.dogukan_tufan.shared_expense_tracking_system.dto.invitation.InvitationResponse;
import com.dogukan_tufan.shared_expense_tracking_system.entity.Invitation;
import com.dogukan_tufan.shared_expense_tracking_system.entity.InvitationStatus;
import com.dogukan_tufan.shared_expense_tracking_system.entity.Membership;
import com.dogukan_tufan.shared_expense_tracking_system.entity.MembershipRole;
import com.dogukan_tufan.shared_expense_tracking_system.entity.User;
import com.dogukan_tufan.shared_expense_tracking_system.exception.ConflictException;
import com.dogukan_tufan.shared_expense_tracking_system.exception.ResourceNotFoundException;
import com.dogukan_tufan.shared_expense_tracking_system.repository.InvitationRepository;
import com.dogukan_tufan.shared_expense_tracking_system.repository.MembershipRepository;
import com.dogukan_tufan.shared_expense_tracking_system.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

// Adding a member is now a two-step process: an admin invites, then the invitee accepts or
// declines. Nobody joins a household without agreeing to it. @Transactional: each method is one
// database transaction, so e.g. accepting creates the Membership and marks the invitation
// ACCEPTED together or not at all.
@Service
@RequiredArgsConstructor
@Transactional
public class InvitationService {

    private final UserRepository userRepository;
    private final MembershipRepository membershipRepository;
    private final InvitationRepository invitationRepository;
    private final HouseholdAccessService accessService;

    // ADMIN only. Creates a PENDING invitation; no Membership is created yet.
    public InvitationResponse invite(Long householdId, InviteRequest request, String currentUserEmail) {
        User currentUser = getUser(currentUserEmail);
        Membership requesterMembership = accessService.requireActiveMembership(currentUser.getId(), householdId);
        accessService.requireAdmin(requesterMembership);

        User target = userRepository.findByEmail(request.email())
                .orElseThrow(() -> new ResourceNotFoundException("No user with email: " + request.email()));

        // Covers inviting yourself too, since an admin is always an active member.
        boolean alreadyActive = membershipRepository
                .findByUserIdAndHouseholdIdAndLeftAtIsNull(target.getId(), householdId)
                .isPresent();
        if (alreadyActive) {
            throw new ConflictException("User is already an active member of this household");
        }

        boolean alreadyPending = invitationRepository
                .existsByHouseholdIdAndInvitedUserIdAndStatus(householdId, target.getId(), InvitationStatus.PENDING);
        if (alreadyPending) {
            throw new ConflictException("User already has a pending invitation to this household");
        }

        // Pending invitations count toward the limit on purpose: otherwise an admin could send
        // 10 invitations to a 3-person household, and they would all get accepted and overfill it.
        long seatsUsed = membershipRepository.countByHouseholdIdAndLeftAtIsNull(householdId)
                + invitationRepository.countByHouseholdIdAndStatus(householdId, InvitationStatus.PENDING);
        if (seatsUsed >= HouseholdLimits.MAX_ACTIVE_MEMBERS) {
            throw new ConflictException("Household already has the maximum of "
                    + HouseholdLimits.MAX_ACTIVE_MEMBERS + " members (including pending invitations)");
        }

        Invitation invitation = new Invitation(requesterMembership.getHousehold(), target, currentUser);
        invitationRepository.save(invitation);

        return InvitationResponse.from(invitation);
    }

    // Any active member can see what is still waiting for an answer.
    public List<InvitationResponse> listPendingForHousehold(Long householdId, String currentUserEmail) {
        User currentUser = getUser(currentUserEmail);
        accessService.requireActiveMembership(currentUser.getId(), householdId);

        return invitationRepository.findByHouseholdIdAndStatusOrderByCreatedAtDesc(householdId, InvitationStatus.PENDING)
                .stream()
                .map(InvitationResponse::from)
                .toList();
    }

    // ADMIN only. Keeps the row and marks it CANCELLED, so the history is preserved.
    public void cancel(Long householdId, Long invitationId, String currentUserEmail) {
        User currentUser = getUser(currentUserEmail);
        Membership requesterMembership = accessService.requireActiveMembership(currentUser.getId(), householdId);
        accessService.requireAdmin(requesterMembership);

        Invitation invitation = invitationRepository.findByIdAndHouseholdId(invitationId, householdId)
                .orElseThrow(() -> new ResourceNotFoundException("Invitation not found: " + invitationId));
        requirePending(invitation);

        invitation.setStatus(InvitationStatus.CANCELLED);
        invitation.setRespondedAt(Instant.now());
    }

    // The current user's own pending invitations, newest first.
    public List<InvitationResponse> listMyPending(String currentUserEmail) {
        User currentUser = getUser(currentUserEmail);

        return invitationRepository
                .findByInvitedUserIdAndStatusOrderByCreatedAtDesc(currentUser.getId(), InvitationStatus.PENDING)
                .stream()
                .map(InvitationResponse::from)
                .toList();
    }

    // Returns the household the user just joined.
    public HouseholdResponse accept(Long invitationId, String currentUserEmail) {
        User currentUser = getUser(currentUserEmail);
        Invitation invitation = findOwnInvitation(invitationId, currentUser);
        requirePending(invitation);

        // The admin's checks at invite time may be outdated by now (other people may have joined
        // or accepted in the meantime), so the rules are checked again here. Only ACTIVE members
        // count at this point: pending invitations do not take a seat when someone accepts.
        Long householdId = invitation.getHousehold().getId();
        boolean alreadyActive = membershipRepository
                .findByUserIdAndHouseholdIdAndLeftAtIsNull(currentUser.getId(), householdId)
                .isPresent();
        if (alreadyActive) {
            throw new ConflictException("User is already an active member of this household");
        }

        long activeMembers = membershipRepository.countByHouseholdIdAndLeftAtIsNull(householdId);
        if (activeMembers >= HouseholdLimits.MAX_ACTIVE_MEMBERS) {
            throw new ConflictException("Household is full");
        }

        Membership membership = new Membership(currentUser, invitation.getHousehold(), MembershipRole.MEMBER);
        membershipRepository.save(membership);

        invitation.setStatus(InvitationStatus.ACCEPTED);
        invitation.setRespondedAt(Instant.now());

        return HouseholdResponse.from(invitation.getHousehold());
    }

    public void decline(Long invitationId, String currentUserEmail) {
        User currentUser = getUser(currentUserEmail);
        Invitation invitation = findOwnInvitation(invitationId, currentUser);
        requirePending(invitation);

        invitation.setStatus(InvitationStatus.DECLINED);
        invitation.setRespondedAt(Instant.now());
    }

    // An invitation addressed to someone else is reported as "not found", not "forbidden",
    // so a user cannot discover other people's invitation ids.
    private Invitation findOwnInvitation(Long invitationId, User currentUser) {
        return invitationRepository.findByIdAndInvitedUserId(invitationId, currentUser.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Invitation not found: " + invitationId));
    }

    // Only PENDING invitations can be answered or cancelled; the others are final.
    private void requirePending(Invitation invitation) {
        if (invitation.getStatus() != InvitationStatus.PENDING) {
            throw new ConflictException("Invitation is no longer pending");
        }
    }

    private User getUser(String email) {
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + email));
    }
}
