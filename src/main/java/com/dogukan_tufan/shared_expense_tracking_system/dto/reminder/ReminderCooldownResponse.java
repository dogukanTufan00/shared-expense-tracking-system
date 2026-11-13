package com.dogukan_tufan.shared_expense_tracking_system.dto.reminder;

import java.time.Instant;

// Tells the app when a reminder button may be pressed again for one debtor. Only reminders still
// inside the 24-hour window are listed, so an empty list means every button is enabled.
public record ReminderCooldownResponse(
        Long debtorUserId,
        Instant nextAllowedAt
) {
}
