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
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

// One person in a chore's rotation. Position 0 is first in line; the order is what "the next person" means.
//
// Why one row per person (and not a list column): the database can enforce that nobody appears twice
// (the unique constraint below), and each row keeps its own position.
@Entity
@Table(name = "chore_rotation_members",
        uniqueConstraints = @UniqueConstraint(name = "uk_chore_rotation_members_chore_user",
                columnNames = {"chore_id", "user_id"}))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ChoreRotationMember {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "chore_id", nullable = false)
    private Chore chore;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    // Setter is used when a member leaves: the rest of the rotation is renumbered 0..n-1.
    @Setter
    @Column(nullable = false)
    private int position;

    public ChoreRotationMember(Chore chore, User user, int position) {
        this.chore = chore;
        this.user = user;
        this.position = position;
    }
}
