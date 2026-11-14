package com.dogukan_tufan.shared_expense_tracking_system.service;

// Household-wide limits in one place, so the number is never copied into two services.
// Change it here and every rule that uses it follows.
public final class HouseholdLimits {

    public static final int MAX_ACTIVE_MEMBERS = 3;

    // Chores per household. Keeps the chore list short enough to load every rotation in one query.
    public static final int MAX_CHORES_PER_HOUSEHOLD = 20;

    private HouseholdLimits() {
    }
}
