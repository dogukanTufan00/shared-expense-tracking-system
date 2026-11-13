package com.dogukan_tufan.shared_expense_tracking_system.entity;

// What happened in a household. The app turns this into a sentence in the user's language (TR/EN),
// so the server stores only the type and the data, never the text. Same idea as NotificationType.
public enum ActivityType {
    HOUSEHOLD_CREATED,
    HOUSEHOLD_RENAMED,
    OWNERSHIP_TRANSFERRED,
    MEMBER_JOINED,
    MEMBER_LEFT,
    MEMBER_REMOVED,
    MEMBER_ROLE_CHANGED,
    EXPENSE_ADDED,
    EXPENSE_UPDATED,
    EXPENSE_DELETED,
    RECURRING_EXPENSE_GENERATED,
    PAYMENT_ADDED,
    PAYMENT_UPDATED,
    PAYMENT_DELETED
}
