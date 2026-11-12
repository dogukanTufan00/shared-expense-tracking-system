package com.dogukan_tufan.shared_expense_tracking_system.dto.household;

import jakarta.validation.constraints.NotNull;

public record TransferOwnershipRequest(

        @NotNull(message = "User id is required")
        Long userId
) {
}
