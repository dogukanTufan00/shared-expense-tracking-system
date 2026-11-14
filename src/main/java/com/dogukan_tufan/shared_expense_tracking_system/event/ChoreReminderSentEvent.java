package com.dogukan_tufan.shared_expense_tracking_system.event;

// Published by ChoreService.remind after the reminder row is saved. The listener reads the row again.
public record ChoreReminderSentEvent(Long reminderId) {
}
