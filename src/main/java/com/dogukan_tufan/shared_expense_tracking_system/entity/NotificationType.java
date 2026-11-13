package com.dogukan_tufan.shared_expense_tracking_system.entity;

// What happened. The app turns this into a sentence in the user's language (TR/EN), so the server
// stores only the type and the data, never the text.
public enum NotificationType {
    EXPENSE_ADDED,
    PAYMENT_RECORDED,
    INVITATION_RECEIVED,
    INVITATION_ACCEPTED,
    RECURRING_EXPENSE_ADDED,
    PAYMENT_REMINDER
}
