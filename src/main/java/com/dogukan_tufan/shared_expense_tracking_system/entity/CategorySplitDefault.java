package com.dogukan_tufan.shared_expense_tracking_system.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

// A saved default split for one expense category in one household, e.g. "GROCERIES: Ahmet 60%,
// Ayse 40%". One row per (household, category, user). Used by ExpenseService when an expense is
// created without explicit shares -- and only while its users are still exactly the active members.
@Entity
@Table(name = "category_split_defaults",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_split_default_household_category_user",
                columnNames = {"household_id", "category", "user_id"}))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CategorySplitDefault {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "household_id", nullable = false)
    private Household household;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ExpenseCategory category;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(nullable = false, precision = 5, scale = 2)
    private BigDecimal percent;

    public CategorySplitDefault(Household household, ExpenseCategory category, User user, BigDecimal percent) {
        this.household = household;
        this.category = category;
        this.user = user;
        this.percent = percent;
    }
}
