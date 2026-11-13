package com.dogukan_tufan.shared_expense_tracking_system.event;

import java.math.BigDecimal;
import java.util.Set;

// Published by ExpenseService.update, only when ActivityLogService really wrote a change.
// previousAmount is the amount before the edit, or null when the amount did not change.
// affectedUserIds are the payer and the people with a share, both before and after the edit, so a
// person who was taken out of the split still hears about it. Only ids are carried; the listener
// loads fresh data in its own transaction.
public record ExpenseUpdatedEvent(Long expenseId, Long actorUserId, BigDecimal previousAmount,
                                  Set<Long> affectedUserIds) {
}
