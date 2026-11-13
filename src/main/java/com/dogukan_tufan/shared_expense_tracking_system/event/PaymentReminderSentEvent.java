package com.dogukan_tufan.shared_expense_tracking_system.event;

// Published by ReminderService.send after a reminder is saved. The reminder id is enough: the listener
// reads the row again, so it works with the saved values.
public record PaymentReminderSentEvent(Long reminderId) {
}
