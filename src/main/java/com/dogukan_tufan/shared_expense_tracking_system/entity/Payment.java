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

import java.math.BigDecimal;
import java.time.Instant;

// A real-world repayment from one member to another (e.g. "Ayse paid Dogukan 50 TL back").
// No setters anywhere: a payment is an append-only ledger entry, like a bank transaction -- you
// don't edit history, you remove a mistaken one (PaymentService.delete) or record a correction.
@Entity
@Table(name = "payments")
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

    public Payment(Household household, User fromUser, User toUser, BigDecimal amount) {
        this.household = household;
        this.fromUser = fromUser;
        this.toUser = toUser;
        this.amount = amount;
        this.paidAt = Instant.now();
    }
}
