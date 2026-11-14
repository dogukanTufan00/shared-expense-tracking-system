package com.dogukan_tufan.shared_expense_tracking_system.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;

// The rent deposit ("depozito") the landlord holds for the household. One row per household.
// Why OneToOne with a unique household_id: a household has exactly one deposit record, so a second
// one must not be possible.
// The per-person split lives in DepositShare; this row only holds the total.
@Entity
@Table(name = "household_deposits")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class HouseholdDeposit {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "household_id", nullable = false, unique = true)
    private Household household;

    @Column(name = "total_amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal totalAmount;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public HouseholdDeposit(Household household, BigDecimal totalAmount) {
        this.household = household;
        this.totalAmount = totalAmount;
        this.updatedAt = Instant.now();
    }

    // Called by DepositService when the total is corrected by hand.
    public void changeTotal(BigDecimal newTotal) {
        this.totalAmount = newTotal;
        this.updatedAt = Instant.now();
    }
}
