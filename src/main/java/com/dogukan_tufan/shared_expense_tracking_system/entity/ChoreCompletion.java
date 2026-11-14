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

import java.time.Instant;

// One time someone marked a chore as done. Used for the "recent completions" list on the chore.
//
// Why a table: the recent list needs a real history. The activity log deliberately does not get these
// rows (see ChoreService.markDone). The index serves "latest rows of one chore".
@Entity
@Table(name = "chore_completions",
        indexes = @Index(name = "idx_chore_completions_chore_done_at", columnList = "chore_id, done_at"))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ChoreCompletion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "chore_id", nullable = false)
    private Chore chore;

    // Who did the work. It may not be the person whose turn it was.
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "done_by_id", nullable = false)
    private User doneBy;

    @Column(name = "done_at", nullable = false, updatable = false)
    private Instant doneAt;

    public ChoreCompletion(Chore chore, User doneBy) {
        this.chore = chore;
        this.doneBy = doneBy;
        this.doneAt = Instant.now();
    }
}
