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

// A hand-over of part of the deposit share from one person to another.
// fromUser gives up the amount, toUser takes it over, so toUser owes fromUser that amount.
// That is why BalanceService counts it: the giver is owed money, the taker owes it.
// Like Payment, a transfer is never edited. A wrong one is undone (deleted) and the right one is made again.
// Why the index: PostgreSQL does not index foreign keys by itself, and the list and balances filter by household.
@Entity
@Table(name = "deposit_transfers", indexes = {
        @Index(name = "idx_deposit_transfers_household_id", columnList = "household_id")
})
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class DepositTransfer {

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

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by_user_id", nullable = false)
    private User createdBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    public DepositTransfer(Household household, User fromUser, User toUser, BigDecimal amount, User createdBy) {
        this.household = household;
        this.fromUser = fromUser;
        this.toUser = toUser;
        this.amount = amount;
        this.createdBy = createdBy;
        this.createdAt = Instant.now();
    }
}
