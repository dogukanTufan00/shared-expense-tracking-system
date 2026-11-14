package com.dogukan_tufan.shared_expense_tracking_system.dto.shopping;

import com.dogukan_tufan.shared_expense_tracking_system.dto.expense.ExpenseRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

// "Shopping done": the checked items are saved as ONE expense. The expense part is the normal expense
// form, so the same rules (payer, split, amount) apply as for any other expense.
public record ShoppingCheckoutRequest(

        @NotEmpty(message = "itemIds must contain at least one item")
        @Size(max = 100, message = "At most 100 items per checkout")
        List<@NotNull(message = "itemIds must not contain null") Long> itemIds,

        @NotNull(message = "Expense is required")
        @Valid
        ExpenseRequest expense
) {
}
