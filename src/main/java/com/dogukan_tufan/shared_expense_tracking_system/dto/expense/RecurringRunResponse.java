package com.dogukan_tufan.shared_expense_tracking_system.dto.expense;

// How many expenses the "run now" request created, and how many bill reminders it sent.
public record RecurringRunResponse(int generated, int remindersSent) {
}
