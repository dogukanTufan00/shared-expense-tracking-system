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
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

// How much of the household deposit one person owns right now. Amounts are never negative (the service
// checks this). A former member can keep a row with 0 or a positive amount until they hand it over.
// Why the unique pair (household, user): one share per person per household, so updates always change
// the same row. Why the index on household_id: PostgreSQL does not index foreign keys by itself.
@Entity
@Table(name = "deposit_shares",
        uniqueConstraints = @UniqueConstraint(name = "uk_deposit_shares_household_user",
                columnNames = {"household_id", "user_id"}),
        indexes = @Index(name = "idx_deposit_shares_household_id", columnList = "household_id"))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class DepositShare {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "household_id", nullable = false)
    private Household household;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Setter
    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal amount;

    public DepositShare(Household household, User user, BigDecimal amount) {
        this.household = household;
        this.user = user;
        this.amount = amount;
    }
}
