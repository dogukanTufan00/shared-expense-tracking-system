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

// A household chore that passes between members in turn (e.g. "Bulasik", "Cop").
//
// Why currentUser is nullable: it is null only when the rotation became empty (everyone left).
// Why the order of people is not stored here: the rotation is a list, kept in ChoreRotationMember rows
// with a position, so the order survives edits and the same person cannot appear twice.
@Entity
// Indexed by household: every chore request lists or checks the chores of one household.
@Table(name = "chores", indexes = @Index(name = "idx_chores_household_id", columnList = "household_id"))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Chore {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "household_id", nullable = false)
    private Household household;

    @Setter
    @Column(nullable = false, length = 60)
    private String name;

    // The person whose turn it is now. Changed when someone marks the chore done or when the rotation is edited.
    @Setter
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "current_user_id")
    private User currentUser;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    public Chore(Household household, String name, User currentUser) {
        this.household = household;
        this.name = name;
        this.currentUser = currentUser;
        this.createdAt = Instant.now();
    }
}
