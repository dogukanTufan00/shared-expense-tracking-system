package com.dogukan_tufan.shared_expense_tracking_system.event;

// Published by ExpenseService.createRecurringCopy when the nightly job (or the post-save catch-up)
// creates the next month of a recurring expense. Kept separate from ExpenseAddedEvent because nobody
// typed this expense in: the people who owe money get told, not "someone added an expense".
public record RecurringExpenseGeneratedEvent(Long expenseId) {
}
