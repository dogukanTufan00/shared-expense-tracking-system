package com.dogukan_tufan.shared_expense_tracking_system.repository;

import com.dogukan_tufan.shared_expense_tracking_system.entity.PaymentReminder;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface PaymentReminderRepository extends JpaRepository<PaymentReminder, Long> {

    // The most recent reminder from this creditor to this debtor in this household, sent after the
    // given time. Present means "still inside the cooldown".
    Optional<PaymentReminder> findFirstByHouseholdIdAndCreditorIdAndDebtorIdAndSentAtAfterOrderBySentAtDesc(
            Long householdId, Long creditorId, Long debtorId, Instant after);

    // All reminders this creditor sent in this household after the given time. The service keeps only
    // the latest one per debtor.
    List<PaymentReminder> findByHouseholdIdAndCreditorIdAndSentAtAfterOrderBySentAtDesc(
            Long householdId, Long creditorId, Instant after);

    // Used when deleting a household: its reminders hold a foreign key to it.
    void deleteByHouseholdId(Long householdId);

    // Account deletion: removes every reminder the user sent or received, because the note is personal.
    // One bulk JPQL delete instead of loading each row.
    @Modifying
    @Query("delete from PaymentReminder r where r.creditor.id = :userId or r.debtor.id = :userId")
    int deleteByUserInvolved(@Param("userId") Long userId);
}
