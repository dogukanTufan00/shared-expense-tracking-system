package com.dogukan_tufan.shared_expense_tracking_system.entity;

// The expense categories. Stored by name (e.g. "EATING_OUT"), so the order here does not
// matter for existing data; new values only need to be added to this list
// (EnumCheckConstraintUpdater updates the database CHECK constraints at startup).
public enum ExpenseCategory {
    RENT,
    UTILITIES,
    GROCERIES,
    OTHER,
    // Added in C-13 for everyday roommate spending.
    CLEANING,
    EATING_OUT,
    SUBSCRIPTIONS,
    HOME_REPAIR,
    PETS,
    ENTERTAINMENT,
    TRANSPORT,
    HEALTH_BEAUTY
}
