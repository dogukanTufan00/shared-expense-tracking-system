package com.dogukan_tufan.shared_expense_tracking_system.service;

// Published by ExpenseService when an expense marked "recurring" is created or updated.
// RecurringExpenseService listens for it and fills in any months that are already due (for example
// a rent entered with a date two months ago), so the user doesn't wait for the nightly run.
// An event instead of a direct call: RecurringExpenseService already depends on ExpenseService, and
// calling back the other way would make the two services depend on each other.
public record RecurringExpenseSavedEvent(Long expenseId) {
}
