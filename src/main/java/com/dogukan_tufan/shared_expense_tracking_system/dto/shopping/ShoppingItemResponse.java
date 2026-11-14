package com.dogukan_tufan.shared_expense_tracking_system.dto.shopping;

import java.time.Instant;

// "bought" is true when someone has taken the item. The boughtBy fields, boughtAt and expenseId are null while open.
public record ShoppingItemResponse(
        Long id,
        String name,
        String note,
        Long addedByUserId,
        String addedByName,
        Instant createdAt,
        boolean bought,
        Long boughtByUserId,
        String boughtByName,
        Instant boughtAt,
        Long expenseId
) {
}
