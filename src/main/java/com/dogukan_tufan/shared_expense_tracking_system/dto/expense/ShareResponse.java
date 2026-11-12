package com.dogukan_tufan.shared_expense_tracking_system.dto.expense;

import com.dogukan_tufan.shared_expense_tracking_system.entity.ExpenseShare;

import java.math.BigDecimal;

public record ShareResponse(
        Long userId,
        String name,
        BigDecimal owedAmount,
        BigDecimal percent
) {

    public static ShareResponse from(ExpenseShare share) {
        return new ShareResponse(
                share.getUser().getId(),
                share.getUser().getName(),
                share.getOwedAmount(),
                share.getPercent());
    }
}
