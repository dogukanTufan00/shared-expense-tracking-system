package com.dogukan_tufan.shared_expense_tracking_system.dto.balance;

import java.math.BigDecimal;

// netBalance > 0: the household owes this person money (they overpaid their fair share).
// netBalance < 0: this person owes the household money.
// netBalance == 0: settled up.
public record BalanceResponse(
        Long userId,
        String name,
        BigDecimal netBalance
) {
}
