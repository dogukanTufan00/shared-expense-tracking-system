package com.dogukan_tufan.shared_expense_tracking_system.dto.balance;

import java.math.BigDecimal;

// One suggested payment: fromUser should pay toUser this amount.
// Suggestions only: nothing is stored, the list is recomputed from the balances on every call.
public record SettlementResponse(
        Long fromUserId,
        String fromName,
        Long toUserId,
        String toName,
        // The creditor's IBAN, so the app can show a copy button. Null if they have not added one.
        String toIban,
        BigDecimal amount
) {
}
