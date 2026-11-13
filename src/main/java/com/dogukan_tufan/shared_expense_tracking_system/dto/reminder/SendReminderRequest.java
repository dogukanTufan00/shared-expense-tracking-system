package com.dogukan_tufan.shared_expense_tracking_system.dto.reminder;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record SendReminderRequest(

        @NotNull(message = "debtorUserId is required")
        Long debtorUserId,

        @Size(max = 200, message = "Note must be at most 200 characters")
        String note
) {
}
