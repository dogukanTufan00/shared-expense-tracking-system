package com.dogukan_tufan.shared_expense_tracking_system.dto.chore;

import java.time.Instant;

// Returned after a reminder is sent. nextAllowedAt is the first moment the sender may remind this person again.
public record ChoreReminderResponse(
        Long choreId,
        Long targetUserId,
        Instant nextAllowedAt
) {
}
