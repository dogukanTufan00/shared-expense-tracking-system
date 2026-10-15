package com.dogukan_tufan.shared_expense_tracking_system.dto.expense;

import com.dogukan_tufan.shared_expense_tracking_system.entity.ExpenseCategory;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.LocalDateTime;

// Shared by both create and update -- the two operations need exactly the same fields validated
// the same way, so one record covers both instead of two near-identical copies.
public record ExpenseRequest(

        @NotNull(message = "paidByUserId is required")
        Long paidByUserId,

        @NotNull(message = "Amount is required")
        @DecimalMin(value = "0.01", message = "Amount must be greater than 0")
        @Digits(integer = 10, fraction = 2, message = "Amount can have at most 2 decimal places")
        BigDecimal amount,

        @NotNull(message = "Category is required")
        ExpenseCategory category,

        String description,

        boolean recurring,

        @NotNull(message = "Expense date is required")
        LocalDateTime expenseDate
) {
}
