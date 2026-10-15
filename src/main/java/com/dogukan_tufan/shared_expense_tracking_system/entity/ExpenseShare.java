package com.dogukan_tufan.shared_expense_tracking_system.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

// Schema only for now: how much each user owes for a given Expense.
// The logic that creates these rows (splitting an expense among members) and the
// balance calculation on top of them are built in the next checkpoint (see CLAUDE.md section 7).
@Entity
@Table(name = "expense_shares")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ExpenseShare {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "expense_id", nullable = false)
    private Expense expense;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Setter
    @Column(name = "owed_amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal owedAmount;

    public ExpenseShare(Expense expense, User user, BigDecimal owedAmount) {
        this.expense = expense;
        this.user = user;
        this.owedAmount = owedAmount;
    }
}
