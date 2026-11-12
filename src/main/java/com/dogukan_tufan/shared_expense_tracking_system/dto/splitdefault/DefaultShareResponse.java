package com.dogukan_tufan.shared_expense_tracking_system.dto.splitdefault;

import com.dogukan_tufan.shared_expense_tracking_system.entity.CategorySplitDefault;

import java.math.BigDecimal;

public record DefaultShareResponse(
        Long userId,
        String name,
        BigDecimal percent
) {

    public static DefaultShareResponse from(CategorySplitDefault row) {
        return new DefaultShareResponse(row.getUser().getId(), row.getUser().getName(), row.getPercent());
    }
}
