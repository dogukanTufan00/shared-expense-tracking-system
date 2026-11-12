package com.dogukan_tufan.shared_expense_tracking_system.entity;

// How an expense's cost is divided between the people who share it.
// EQUAL: everyone pays the same amount (the default).
// PERCENT: each person pays a percentage of the amount; the percentages add up to 100.
// AMOUNT: each person pays an exact amount; the amounts add up to the expense amount.
public enum SplitType {
    EQUAL,
    PERCENT,
    AMOUNT
}
