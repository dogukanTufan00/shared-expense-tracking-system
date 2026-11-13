package com.dogukan_tufan.shared_expense_tracking_system.dto.reminder;

import java.math.BigDecimal;
import java.time.Instant;

public record ReminderResponse(
        Long id,
        Long debtorUserId,
        String debtorName,
        BigDecimal amount,
        String note,
        Instant sentAt,
        // The first moment the creditor may send another reminder to this debtor (sentAt + 24 hours).
        Instant nextAllowedAt
) {
}
