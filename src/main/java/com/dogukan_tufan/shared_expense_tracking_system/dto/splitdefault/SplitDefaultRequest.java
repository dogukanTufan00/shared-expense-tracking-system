package com.dogukan_tufan.shared_expense_tracking_system.dto.splitdefault;

import com.dogukan_tufan.shared_expense_tracking_system.dto.expense.ShareInput;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;

import java.util.List;

// Body of PUT /api/households/{householdId}/split-defaults/{category}. `value` is the percentage.
public record SplitDefaultRequest(
        @NotEmpty(message = "shares are required")
        @Valid
        List<ShareInput> shares
) {
}
