package com.dogukan_tufan.shared_expense_tracking_system.event;

// Published by PaymentService.create after a payment is saved. actorUserId is the person who
// logged it, so they can be left out of the notification (they already know).
public record PaymentRecordedEvent(Long paymentId, Long actorUserId) {
}
