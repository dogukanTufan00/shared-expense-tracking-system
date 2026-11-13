package com.dogukan_tufan.shared_expense_tracking_system.event;

import java.math.BigDecimal;
import java.util.Set;

// Published by PaymentService.update, only when ActivityLogService really wrote a change.
// previousAmount is the amount before the edit, or null when the amount did not change.
// affectedUserIds are the sender and receiver, both before and after the edit. Only ids are carried;
// the listener loads fresh data in its own transaction.
public record PaymentUpdatedEvent(Long paymentId, Long actorUserId, BigDecimal previousAmount,
                                  Set<Long> affectedUserIds) {
}
