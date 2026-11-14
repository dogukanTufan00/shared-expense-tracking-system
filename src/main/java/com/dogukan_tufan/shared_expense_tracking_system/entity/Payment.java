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

import java.math.BigDecimal;
import java.time.Instant;

// A real-world repayment from one member to another (e.g. "Ayse paid Dogukan 50 TL back").
// No setters anywhere: changes go through update(...) below, which is the only place a payment's
// details can change. That keeps the edit rules in one explicit spot instead of scattered setters.
// Why the index: PostgreSQL does not index foreign keys by itself, and the payment list and balances
// filter by household.
@Entity
@Table(name = "payments", indexes = {
        @Index(name = "idx_payments_household_id", columnList = "household_id")
})
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Payment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "household_id", nullable = false)
    private Household household;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "from_user_id", nullable = false)
    private User fromUser;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "to_user_id", nullable = false)
    private User toUser;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal amount;

    @Column(name = "paid_at", nullable = false, updatable = false)
    private Instant paidAt;

    // Optional free text such as "sent via FAST". Nullable on purpose: most payments have no note.
    @Column(length = 200)
    private String note;

    public Payment(Household household, User fromUser, User toUser, BigDecimal amount, String note) {
        this.household = household;
        this.fromUser = fromUser;
        this.toUser = toUser;
        this.amount = amount;
        this.note = note;
        this.paidAt = Instant.now();
    }

    // Edits the payment in place. paidAt and household are deliberately NOT changed: the time the
    // money actually moved stays what it was, and a payment cannot be moved to another household.
    public void update(User fromUser, User toUser, BigDecimal amount, String note) {
        this.fromUser = fromUser;
        this.toUser = toUser;
        this.amount = amount;
        this.note = note;
    }
}
