package com.dogukan_tufan.shared_expense_tracking_system.service;

import com.dogukan_tufan.shared_expense_tracking_system.dto.expense.ExpenseRequest;
import com.dogukan_tufan.shared_expense_tracking_system.dto.expense.ExpenseResponse;
import com.dogukan_tufan.shared_expense_tracking_system.entity.Expense;
import com.dogukan_tufan.shared_expense_tracking_system.entity.ExpenseShare;
import com.dogukan_tufan.shared_expense_tracking_system.entity.Membership;
import com.dogukan_tufan.shared_expense_tracking_system.entity.MembershipRole;
import com.dogukan_tufan.shared_expense_tracking_system.entity.User;
import com.dogukan_tufan.shared_expense_tracking_system.exception.BadRequestException;
import com.dogukan_tufan.shared_expense_tracking_system.exception.ForbiddenActionException;
import com.dogukan_tufan.shared_expense_tracking_system.exception.ResourceNotFoundException;
import com.dogukan_tufan.shared_expense_tracking_system.repository.ExpenseRepository;
import com.dogukan_tufan.shared_expense_tracking_system.repository.ExpenseShareRepository;
import com.dogukan_tufan.shared_expense_tracking_system.repository.MembershipRepository;
import com.dogukan_tufan.shared_expense_tracking_system.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional
public class ExpenseService {

    private final UserRepository userRepository;
    private final ExpenseRepository expenseRepository;
    private final ExpenseShareRepository expenseShareRepository;
    private final MembershipRepository membershipRepository;
    private final HouseholdAccessService accessService;

    public ExpenseResponse create(Long householdId, ExpenseRequest request, String currentUserEmail) {
        User currentUser = getUser(currentUserEmail);
        Membership requesterMembership = accessService.requireActiveMembership(currentUser.getId(), householdId);

        Membership payerMembership = requireActivePayer(householdId, request.paidByUserId());
        BigDecimal amount = request.amount().setScale(2, RoundingMode.HALF_UP);

        Expense expense = new Expense(
                requesterMembership.getHousehold(),
                payerMembership.getUser(),
                amount,
                request.category(),
                request.description(),
                request.recurring(),
                request.expenseDate());
        expenseRepository.save(expense);

        recalculateShares(expense, activeMemberUsers(householdId));

        return ExpenseResponse.from(expense);
    }

    public List<ExpenseResponse> list(Long householdId, String currentUserEmail) {
        User currentUser = getUser(currentUserEmail);
        accessService.requireActiveMembership(currentUser.getId(), householdId);

        return expenseRepository.findByHouseholdId(householdId).stream()
                .map(ExpenseResponse::from)
                .toList();
    }

    public ExpenseResponse getById(Long householdId, Long expenseId, String currentUserEmail) {
        User currentUser = getUser(currentUserEmail);
        accessService.requireActiveMembership(currentUser.getId(), householdId);

        Expense expense = findExpense(householdId, expenseId);
        return ExpenseResponse.from(expense);
    }

    public ExpenseResponse update(Long householdId, Long expenseId, ExpenseRequest request, String currentUserEmail) {
        User currentUser = getUser(currentUserEmail);
        Membership requesterMembership = accessService.requireActiveMembership(currentUser.getId(), householdId);

        Expense expense = findExpense(householdId, expenseId);
        requirePayerOrAdmin(expense, currentUser, requesterMembership);

        Membership payerMembership = requireActivePayer(householdId, request.paidByUserId());

        expense.setPaidBy(payerMembership.getUser());
        expense.setAmount(request.amount().setScale(2, RoundingMode.HALF_UP));
        expense.setCategory(request.category());
        expense.setDescription(request.description());
        expense.setRecurring(request.recurring());
        expense.setExpenseDate(request.expenseDate());

        // The amount may have changed, so the shares are rebuilt rather than patched. They are split
        // among the people who originally shared this expense, not today's members: if someone
        // moved out since, editing an old bill must not shift their part onto the others.
        List<User> participants = expenseShareRepository.findByExpenseId(expense.getId()).stream()
                .map(ExpenseShare::getUser)
                .toList();
        if (participants.isEmpty()) {
            // Expenses created before shares existed have none yet; fall back to current members.
            participants = activeMemberUsers(householdId);
        }
        recalculateShares(expense, participants);

        return ExpenseResponse.from(expense);
    }

    public void delete(Long householdId, Long expenseId, String currentUserEmail) {
        User currentUser = getUser(currentUserEmail);
        Membership requesterMembership = accessService.requireActiveMembership(currentUser.getId(), householdId);

        Expense expense = findExpense(householdId, expenseId);
        requirePayerOrAdmin(expense, currentUser, requesterMembership);

        // Its shares hold a foreign key to this expense, so they go first.
        expenseShareRepository.deleteByExpenseId(expense.getId());
        expenseRepository.delete(expense);
    }

    // Rebuilds this expense's ExpenseShare rows from scratch: an equal split of its amount
    // across the given participants. Equal splitting is deliberately the simple default --
    // custom, unequal splits are a v2 idea, not needed for the core "who owes what" flow.
    private void recalculateShares(Expense expense, List<User> participants) {
        expenseShareRepository.deleteByExpenseId(expense.getId());

        List<BigDecimal> shares = splitEqually(expense.getAmount(), participants.size());

        for (int i = 0; i < participants.size(); i++) {
            ExpenseShare share = new ExpenseShare(expense, participants.get(i), shares.get(i));
            expenseShareRepository.save(share);
        }
    }

    private List<User> activeMemberUsers(Long householdId) {
        return membershipRepository.findByHouseholdIdAndLeftAtIsNull(householdId).stream()
                .map(Membership::getUser)
                .toList();
    }

    // Plain amount/count division loses or gains cents to rounding (e.g. 150.50 / 3 = 50.1666...).
    // This splits in whole cents instead, so the shares always sum back to exactly `amount`; any
    // leftover cent goes to the first few shares, one each, rather than being dropped or duplicated.
    private List<BigDecimal> splitEqually(BigDecimal amount, int count) {
        long totalCents = amount.movePointRight(2).longValueExact();
        long baseCents = totalCents / count;
        long remainderCents = totalCents % count;

        List<BigDecimal> shares = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            long cents = baseCents + (i < remainderCents ? 1 : 0);
            shares.add(BigDecimal.valueOf(cents, 2));
        }
        return shares;
    }

    private Expense findExpense(Long householdId, Long expenseId) {
        return expenseRepository.findByIdAndHouseholdId(expenseId, householdId)
                .orElseThrow(() -> new ResourceNotFoundException("Expense not found: " + expenseId));
    }

    private Membership requireActivePayer(Long householdId, Long userId) {
        return membershipRepository.findByUserIdAndHouseholdIdAndLeftAtIsNull(userId, householdId)
                .orElseThrow(() -> new BadRequestException(
                        "paidByUserId " + userId + " is not an active member of this household"));
    }

    private void requirePayerOrAdmin(Expense expense, User currentUser, Membership requesterMembership) {
        boolean isPayer = expense.getPaidBy().getId().equals(currentUser.getId());
        boolean isAdmin = requesterMembership.getRole() == MembershipRole.ADMIN;
        if (!isPayer && !isAdmin) {
            throw new ForbiddenActionException("Only the payer or a household admin can modify this expense");
        }
    }

    private User getUser(String email) {
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + email));
    }
}
