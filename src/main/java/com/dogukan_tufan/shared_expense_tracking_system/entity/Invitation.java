package com.dogukan_tufan.shared_expense_tracking_system.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
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

// An admin asking a user to join a household. The user must accept before a Membership is created,
// so nobody lands in a household without agreeing to it.
// Rows are never deleted when an invitation is declined or cancelled; only the status changes.
// That keeps an audit trail of who invited whom and what the answer was -- the same idea as
// Membership.leftAt, where a left member's row is kept instead of being removed.
// Why the indexes: PostgreSQL does not index foreign keys by itself. Invitations are listed per
// household and per invited user ("my invitations"), so both columns get an index.
@Entity
@Table(name = "invitations", indexes = {
        @Index(name = "idx_invitations_household_id", columnList = "household_id"),
        @Index(name = "idx_invitations_invited_user_id", columnList = "invited_user_id")
})
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Invitation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "household_id", nullable = false)
    private Household household;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "invited_user_id", nullable = false)
    private User invitedUser;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "invited_by_id", nullable = false)
    private User invitedBy;

    @Setter
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private InvitationStatus status;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    // null while the invitation is pending; set when it is accepted, declined or cancelled
    @Setter
    @Column(name = "responded_at")
    private Instant respondedAt;

    public Invitation(Household household, User invitedUser, User invitedBy) {
        this.household = household;
        this.invitedUser = invitedUser;
        this.invitedBy = invitedBy;
        this.status = InvitationStatus.PENDING;
        this.createdAt = Instant.now();
    }
}
