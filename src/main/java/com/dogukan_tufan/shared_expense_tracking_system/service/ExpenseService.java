package com.dogukan_tufan.shared_expense_tracking_system.service;

import com.dogukan_tufan.shared_expense_tracking_system.dto.expense.ExpenseRequest;
import com.dogukan_tufan.shared_expense_tracking_system.dto.expense.ExpenseResponse;
import com.dogukan_tufan.shared_expense_tracking_system.entity.Expense;
import com.dogukan_tufan.shared_expense_tracking_system.entity.Membership;
import com.dogukan_tufan.shared_expense_tracking_system.entity.MembershipRole;
import com.dogukan_tufan.shared_expense_tracking_system.entity.User;
import com.dogukan_tufan.shared_expense_tracking_system.exception.BadRequestException;
import com.dogukan_tufan.shared_expense_tracking_system.exception.ForbiddenActionException;
import com.dogukan_tufan.shared_expense_tracking_system.exception.ResourceNotFoundException;
import com.dogukan_tufan.shared_expense_tracking_system.repository.ExpenseRepository;
import com.dogukan_tufan.shared_expense_tracking_system.repository.MembershipRepository;
import com.dogukan_tufan.shared_expense_tracking_system.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional
public class ExpenseService {

    private final UserRepository userRepository;
    private final ExpenseRepository expenseRepository;
    private final MembershipRepository membershipRepository;
    private final HouseholdAccessService accessService;

    public ExpenseResponse create(Long householdId, ExpenseRequest request, String currentUserEmail) {
        User currentUser = getUser(currentUserEmail);
        Membership requesterMembership = accessService.requireActiveMembership(currentUser.getId(), householdId);

        Membership payerMembership = requireActivePayer(householdId, request.paidByUserId());

        Expense expense = new Expense(
                requesterMembership.getHousehold(),
                payerMembership.getUser(),
                request.amount(),
                request.category(),
                request.description(),
                request.recurring(),
                request.expenseDate());
        expenseRepository.save(expense);

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
        expense.setAmount(request.amount());
        expense.setCategory(request.category());
        expense.setDescription(request.description());
        expense.setRecurring(request.recurring());
        expense.setExpenseDate(request.expenseDate());

        return ExpenseResponse.from(expense);
    }

    public void delete(Long householdId, Long expenseId, String currentUserEmail) {
        User currentUser = getUser(currentUserEmail);
        Membership requesterMembership = accessService.requireActiveMembership(currentUser.getId(), householdId);

        Expense expense = findExpense(householdId, expenseId);
        requirePayerOrAdmin(expense, currentUser, requesterMembership);

        expenseRepository.delete(expense);
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
