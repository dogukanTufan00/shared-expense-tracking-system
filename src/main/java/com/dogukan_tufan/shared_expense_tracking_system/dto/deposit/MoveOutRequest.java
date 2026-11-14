package com.dogukan_tufan.shared_expense_tracking_system.dto.deposit;

import jakarta.validation.constraints.NotNull;

// The person whose whole deposit share is handed over to the other active members.
public record MoveOutRequest(

        @NotNull(message = "userId is required")
        Long userId
) {
}
