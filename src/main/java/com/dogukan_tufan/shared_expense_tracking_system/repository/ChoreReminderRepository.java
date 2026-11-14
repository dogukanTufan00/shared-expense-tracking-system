package com.dogukan_tufan.shared_expense_tracking_system.repository;

import com.dogukan_tufan.shared_expense_tracking_system.entity.ChoreReminder;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;

public interface ChoreReminderRepository extends JpaRepository<ChoreReminder, Long> {

    // The sender's latest reminder to this person for this chore, if it is still inside the 24-hour window.
    Optional<ChoreReminder> findFirstByChoreIdAndSenderIdAndTargetIdAndSentAtAfterOrderBySentAtDesc(
            Long choreId, Long senderId, Long targetId, Instant after);

    void deleteByChoreId(Long choreId);

    // Used when deleting a household.
    void deleteByChoreHouseholdId(Long householdId);

    // Account deletion: removes every chore reminder the user sent or received. One bulk JPQL delete.
    @Modifying
    @Query("delete from ChoreReminder r where r.sender.id = :userId or r.target.id = :userId")
    int deleteByUserInvolved(@Param("userId") Long userId);
}
