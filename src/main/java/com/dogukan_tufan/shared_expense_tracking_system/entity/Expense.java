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
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;

@Entity
@Table(name = "expenses")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Expense {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "household_id", nullable = false)
    private Household household;

    // Has a setter (unlike household, which never changes once an expense is created): the
    // payer can be corrected via PUT, e.g. "I logged it as paid by me but it was actually Ahmet".
    @Setter
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "paid_by", nullable = false)
    private User paidBy;

    @Setter
    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal amount;

    @Setter
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ExpenseCategory category;

    @Setter
    @Column
    private String description;

    @Setter
    @Column(nullable = false)
    private boolean recurring;

    @Setter
    @Column(name = "expense_date", nullable = false)
    private LocalDateTime expenseDate;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    public Expense(Household household, User paidBy, BigDecimal amount, ExpenseCategory category,
                   String description, boolean recurring, LocalDateTime expenseDate) {
        this.household = household;
        this.paidBy = paidBy;
        this.amount = amount;
        this.category = category;
        this.description = description;
        this.recurring = recurring;
        this.expenseDate = expenseDate;
        this.createdAt = Instant.now();
    }
}
