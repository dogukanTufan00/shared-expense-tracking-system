package com.dogukan_tufan.shared_expense_tracking_system.dto.expense;

import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

// One person's part of a custom split. What `value` means depends on the split type:
// a percentage for PERCENT, an amount for AMOUNT, and ignored for EQUAL.
public record ShareInput(
        @NotNull(message = "userId is required in each share")
        Long userId,

        BigDecimal value
) {
}
