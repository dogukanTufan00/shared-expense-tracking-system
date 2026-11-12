package com.dogukan_tufan.shared_expense_tracking_system.service;

import com.dogukan_tufan.shared_expense_tracking_system.dto.household.CreateHouseholdRequest;
import com.dogukan_tufan.shared_expense_tracking_system.dto.household.HouseholdResponse;
import com.dogukan_tufan.shared_expense_tracking_system.dto.household.TransferOwnershipRequest;
import com.dogukan_tufan.shared_expense_tracking_system.dto.household.UpdateHouseholdRequest;
import com.dogukan_tufan.shared_expense_tracking_system.entity.Household;
import com.dogukan_tufan.shared_expense_tracking_system.entity.Membership;
import com.dogukan_tufan.shared_expense_tracking_system.entity.MembershipRole;
import com.dogukan_tufan.shared_expense_tracking_system.entity.User;
import com.dogukan_tufan.shared_expense_tracking_system.exception.BadRequestException;
import com.dogukan_tufan.shared_expense_tracking_system.exception.ForbiddenActionException;
import com.dogukan_tufan.shared_expense_tracking_system.exception.ResourceNotFoundException;
import com.dogukan_tufan.shared_expense_tracking_system.repository.CategorySplitDefaultRepository;
import com.dogukan_tufan.shared_expense_tracking_system.repository.ExpenseRepository;
import com.dogukan_tufan.shared_expense_tracking_system.repository.ExpenseShareRepository;
import com.dogukan_tufan.shared_expense_tracking_system.repository.HouseholdRepository;
import com.dogukan_tufan.shared_expense_tracking_system.repository.InvitationRepository;
import com.dogukan_tufan.shared_expense_tracking_system.repository.MembershipRepository;
import com.dogukan_tufan.shared_expense_tracking_system.repository.PaymentRepository;
import com.dogukan_tufan.shared_expense_tracking_system.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

// @Transactional: every public method here runs as one database transaction. That matters for
// two reasons: (1) if something fails halfway through (e.g. the membership save), everything
// already done in that method rolls back instead of leaving half-written data; (2) entities
// stay "managed" for the whole method, so e.g. calling household.setName(...) is enough --
// Hibernate notices the change and writes it back when the method returns, no explicit save()
// needed for an update.
@Service
@RequiredArgsConstructor
@Transactional
public class HouseholdService {

    private final UserRepository userRepository;
    private final HouseholdRepository householdRepository;
    private final MembershipRepository membershipRepository;
    private final ExpenseRepository expenseRepository;
    private final ExpenseShareRepository expenseShareRepository;
    private final PaymentRepository paymentRepository;
    private final CategorySplitDefaultRepository categorySplitDefaultRepository;
    private final InvitationRepository invitationRepository;
    private final HouseholdAccessService accessService;

    public HouseholdResponse create(CreateHouseholdRequest request, String currentUserEmail) {
        User currentUser = getUser(currentUserEmail);

        Household household = new Household(request.name(), currentUser);
        householdRepository.save(household);

        Membership membership = new Membership(currentUser, household, MembershipRole.ADMIN);
        membershipRepository.save(membership);

        return HouseholdResponse.from(household);
    }

    public List<HouseholdResponse> listMine(String currentUserEmail) {
        User currentUser = getUser(currentUserEmail);

        return membershipRepository.findByUserIdAndLeftAtIsNull(currentUser.getId()).stream()
                .map(Membership::getHousehold)
                .map(HouseholdResponse::from)
                .toList();
    }

    public HouseholdResponse getById(Long householdId, String currentUserEmail) {
        User currentUser = getUser(currentUserEmail);
        Membership membership = accessService.requireActiveMembership(currentUser.getId(), householdId);

        return HouseholdResponse.from(membership.getHousehold());
    }

    public HouseholdResponse rename(Long householdId, UpdateHouseholdRequest request, String currentUserEmail) {
        User currentUser = getUser(currentUserEmail);
        Membership membership = accessService.requireActiveMembership(currentUser.getId(), householdId);
        accessService.requireAdmin(membership);

        Household household = membership.getHousehold();
        household.setName(request.name());

        return HouseholdResponse.from(household);
    }

    // Hands the founder role to another active member. Only the current founder can do this.
    // The new founder must be an ADMIN (a founder who cannot manage the house makes no sense), so
    // we promote them in the same transaction. The old founder stays ADMIN; they can demote
    // themselves afterwards (the last-admin rule still protects the house).
    public HouseholdResponse transferOwnership(Long householdId, TransferOwnershipRequest request,
                                               String currentUserEmail) {
        User currentUser = getUser(currentUserEmail);
        Membership membership = accessService.requireActiveMembership(currentUser.getId(), householdId);
        Household household = membership.getHousehold();

        if (!household.getCreatedBy().getId().equals(currentUser.getId())) {
            throw new ForbiddenActionException("Only the founder can transfer ownership");
        }
        if (request.userId().equals(currentUser.getId())) {
            throw new BadRequestException("You are already the founder");
        }

        Membership newFounderMembership = membershipRepository
                .findByUserIdAndHouseholdIdAndLeftAtIsNull(request.userId(), householdId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "No active member with user id " + request.userId() + " in this household"));

        if (newFounderMembership.getRole() != MembershipRole.ADMIN) {
            newFounderMembership.setRole(MembershipRole.ADMIN);
        }
        household.setCreatedBy(newFounderMembership.getUser());

        return HouseholdResponse.from(household);
    }

    public void delete(Long householdId, String currentUserEmail) {
        User currentUser = getUser(currentUserEmail);
        Membership membership = accessService.requireActiveMembership(currentUser.getId(), householdId);
        // Deleting wipes the whole history for everyone, so while the founder still lives here it
        // is their call alone -- even if they stepped down as admin (otherwise nobody could delete
        // the home). If the founder has moved out, any admin may delete (same rule as founder
        // protection: the house must not stay locked to someone who left).
        Long founderId = membership.getHousehold().getCreatedBy().getId();
        boolean founderActive = membershipRepository
                .findByUserIdAndHouseholdIdAndLeftAtIsNull(founderId, householdId).isPresent();
        if (founderActive) {
            if (!founderId.equals(currentUser.getId())) {
                throw new ForbiddenActionException("Only the household founder can delete the household");
            }
        } else {
            accessService.requireAdmin(membership);
        }

        // Everything below holds a foreign key back to this household (ExpenseShare to its
        // Expense, which holds one to the household), so the order matters: shares, then
        // expenses, payments, category defaults and invitations, then memberships, then the household itself.
        expenseShareRepository.deleteByExpenseHouseholdId(householdId);
        expenseRepository.deleteByHouseholdId(householdId);
        paymentRepository.deleteByHouseholdId(householdId);
        categorySplitDefaultRepository.deleteByHouseholdId(householdId);
        invitationRepository.deleteByHouseholdId(householdId);
        membershipRepository.deleteByHouseholdId(householdId);
        householdRepository.delete(membership.getHousehold());
    }

    private User getUser(String email) {
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + email));
    }
}
