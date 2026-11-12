package com.dogukan_tufan.shared_expense_tracking_system.dto.splitdefault;

import com.dogukan_tufan.shared_expense_tracking_system.entity.CategorySplitDefault;
import com.dogukan_tufan.shared_expense_tracking_system.entity.ExpenseCategory;

import java.util.List;

// All saved shares of one category, e.g. GROCERIES -> [Ahmet 60.00, Ayse 40.00].
public record CategorySplitDefaultResponse(
        ExpenseCategory category,
        List<DefaultShareResponse> shares
) {

    public static CategorySplitDefaultResponse of(ExpenseCategory category, List<CategorySplitDefault> rows) {
        return new CategorySplitDefaultResponse(
                category,
                rows.stream().map(DefaultShareResponse::from).toList());
    }
}
