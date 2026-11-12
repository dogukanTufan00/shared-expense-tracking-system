package com.dogukan_tufan.shared_expense_tracking_system.dto.expense;

import com.dogukan_tufan.shared_expense_tracking_system.entity.ExpenseCategory;
import com.dogukan_tufan.shared_expense_tracking_system.entity.SplitType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

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
        LocalDateTime expenseDate,

        // Optional. Missing = EQUAL. The service checks the rules that depend on the other fields.
        SplitType splitType,

        // Optional. EQUAL: who shares it. PERCENT/AMOUNT: each person's percentage or amount.
        @Valid
        List<ShareInput> shares
) {
}
