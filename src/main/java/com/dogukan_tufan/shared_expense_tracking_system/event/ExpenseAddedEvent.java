package com.dogukan_tufan.shared_expense_tracking_system.event;

// Published by ExpenseService.create after a new expense is saved. Not published for generated
// copies (those have RecurringExpenseGeneratedEvent). Only ids are carried, never entities: the
// listener loads fresh data in its own transaction.
public record ExpenseAddedEvent(Long expenseId, Long actorUserId) {
}
