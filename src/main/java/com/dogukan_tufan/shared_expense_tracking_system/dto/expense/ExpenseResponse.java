package com.dogukan_tufan.shared_expense_tracking_system.dto.expense;

import com.dogukan_tufan.shared_expense_tracking_system.entity.Expense;
import com.dogukan_tufan.shared_expense_tracking_system.entity.ExpenseCategory;
import com.dogukan_tufan.shared_expense_tracking_system.entity.ExpenseShare;
import com.dogukan_tufan.shared_expense_tracking_system.entity.SplitType;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;

public record ExpenseResponse(
        Long id,
        Long householdId,
        Long paidByUserId,
        String paidByName,
        BigDecimal amount,
        ExpenseCategory category,
        String description,
        boolean recurring,
        LocalDateTime expenseDate,
        Instant createdAt,
        SplitType splitType,
        List<ShareResponse> shares
) {

    // The shares are passed in (not loaded here) so the service can fetch them efficiently,
    // e.g. all shares of a household in one query for the list endpoint.
    public static ExpenseResponse from(Expense expense, List<ExpenseShare> shares) {
        return new ExpenseResponse(
                expense.getId(),
                expense.getHousehold().getId(),
                expense.getPaidBy().getId(),
                expense.getPaidBy().getName(),
                expense.getAmount(),
                expense.getCategory(),
                expense.getDescription(),
                expense.isRecurring(),
                expense.getExpenseDate(),
                expense.getCreatedAt(),
                expense.getSplitType(),
                shares.stream().map(ShareResponse::from).toList());
    }
}
