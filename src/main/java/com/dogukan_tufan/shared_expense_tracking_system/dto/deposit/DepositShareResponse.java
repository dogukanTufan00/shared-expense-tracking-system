package com.dogukan_tufan.shared_expense_tracking_system.dto.deposit;

import java.math.BigDecimal;

// One person's part of the deposit. active = still a member, so the app can show "no share yet"
// for an active member with amount 0. Former members only appear while they still own something.
public record DepositShareResponse(
        Long userId,
        String name,
        BigDecimal amount,
        boolean active
) {
}
