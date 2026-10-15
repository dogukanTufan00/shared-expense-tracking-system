package com.dogukan_tufan.shared_expense_tracking_system.dto.expense;

import com.dogukan_tufan.shared_expense_tracking_system.entity.Expense;
import com.dogukan_tufan.shared_expense_tracking_system.entity.ExpenseCategory;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;

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
        Instant createdAt
) {

    public static ExpenseResponse from(Expense expense) {
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
                expense.getCreatedAt());
    }
}
