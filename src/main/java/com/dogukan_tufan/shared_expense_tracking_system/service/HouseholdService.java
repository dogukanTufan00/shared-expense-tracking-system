package com.dogukan_tufan.shared_expense_tracking_system.service;

import com.dogukan_tufan.shared_expense_tracking_system.dto.household.CreateHouseholdRequest;
import com.dogukan_tufan.shared_expense_tracking_system.dto.household.HouseholdResponse;
import com.dogukan_tufan.shared_expense_tracking_system.dto.household.UpdateHouseholdRequest;
import com.dogukan_tufan.shared_expense_tracking_system.entity.Household;
import com.dogukan_tufan.shared_expense_tracking_system.entity.Membership;
import com.dogukan_tufan.shared_expense_tracking_system.entity.MembershipRole;
import com.dogukan_tufan.shared_expense_tracking_system.entity.User;
import com.dogukan_tufan.shared_expense_tracking_system.exception.ResourceNotFoundException;
import com.dogukan_tufan.shared_expense_tracking_system.repository.ExpenseRepository;
import com.dogukan_tufan.shared_expense_tracking_system.repository.ExpenseShareRepository;
import com.dogukan_tufan.shared_expense_tracking_system.repository.HouseholdRepository;
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

    public void delete(Long householdId, String currentUserEmail) {
        User currentUser = getUser(currentUserEmail);
        Membership membership = accessService.requireActiveMembership(currentUser.getId(), householdId);
        accessService.requireAdmin(membership);

        // Everything below holds a foreign key back to this household (ExpenseShare to its
        // Expense, which holds one to the household), so the order matters: shares, then
        // expenses and payments, then memberships, then the household itself.
        expenseShareRepository.deleteByExpenseHouseholdId(householdId);
        expenseRepository.deleteByHouseholdId(householdId);
        paymentRepository.deleteByHouseholdId(householdId);
        membershipRepository.deleteByHouseholdId(householdId);
        householdRepository.delete(membership.getHousehold());
    }

    private User getUser(String email) {
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + email));
    }
}
