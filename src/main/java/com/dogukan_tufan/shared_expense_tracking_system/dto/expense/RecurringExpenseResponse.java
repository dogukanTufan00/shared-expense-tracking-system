package com.dogukan_tufan.shared_expense_tracking_system.dto.expense;

import com.dogukan_tufan.shared_expense_tracking_system.entity.ExpenseCategory;

import java.math.BigDecimal;
import java.time.LocalDateTime;

// One recurring chain, shown by its latest expense: lastDate is that expense's date and
// nextDate is when the next copy will be created.
public record RecurringExpenseResponse(
        Long expenseId,
        String description,
        ExpenseCategory category,
        BigDecimal amount,
        Long paidByUserId,
        String paidByName,
        int recurrenceDay,
        LocalDateTime lastDate,
        LocalDateTime nextDate
) {
}
