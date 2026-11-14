package com.dogukan_tufan.shared_expense_tracking_system.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

// One line on the household's shared shopping list (e.g. "Sut", "Deterjan").
//
// Why "bought" is a timestamp (boughtAt) and not only a flag: the list needs WHEN it was bought, to sort
// bought items and to delete them a week later. boughtBy is null exactly when boughtAt is null.
// Why expense is set: after checkout, the item points at the expense it was saved into.
@Entity
// Indexed by household (every list request) and by expense (deleting an expense clears these links).
@Table(name = "shopping_items", indexes = {
        @Index(name = "idx_shopping_items_household_id", columnList = "household_id"),
        @Index(name = "idx_shopping_items_expense_id", columnList = "expense_id")
})
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ShoppingItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "household_id", nullable = false)
    private Household household;

    @Setter
    @Column(nullable = false, length = 60)
    private String name;

    @Setter
    @Column(length = 100)
    private String note;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "added_by_user_id", nullable = false)
    private User addedBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "bought_by_user_id")
    private User boughtBy;

    @Column(name = "bought_at")
    private Instant boughtAt;

    // The expense this item was saved into at checkout. Null while the item is not part of an expense.
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "expense_id")
    private Expense expense;

    public ShoppingItem(Household household, String name, String note, User addedBy) {
        this.household = household;
        this.name = name;
        this.note = note;
        this.addedBy = addedBy;
        this.createdAt = Instant.now();
    }

    public boolean isBought() {
        return boughtAt != null;
    }

    public void markBought(User buyer, Instant when) {
        this.boughtBy = buyer;
        this.boughtAt = when;
    }

    public void linkToExpense(Expense expense) {
        this.expense = expense;
    }

    // Back to the open list. The expense itself is not touched; only the link goes.
    public void restore() {
        this.boughtBy = null;
        this.boughtAt = null;
        this.expense = null;
    }
}
