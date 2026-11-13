package com.dogukan_tufan.shared_expense_tracking_system.repository;

import com.dogukan_tufan.shared_expense_tracking_system.entity.Notification;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface NotificationRepository extends JpaRepository<Notification, Long> {

    // The inbox, newest first. Id is the tie-breaker so rows created in the same moment keep a stable order.
    List<Notification> findByRecipientIdOrderByCreatedAtDescIdDesc(Long recipientId, Pageable pageable);

    // Filtering by the recipient in the query means someone else's notification id comes back empty (404).
    Optional<Notification> findByIdAndRecipientId(Long id, Long recipientId);

    List<Notification> findByRecipientIdAndReadFalse(Long recipientId);

    long countByRecipientIdAndReadFalse(Long recipientId);

    // Used when deleting a household (see HouseholdService.delete).
    void deleteByHouseholdId(Long householdId);

    // Used when deleting an account: the inbox of that user goes with them.
    void deleteByRecipientId(Long recipientId);

    // Account deletion: names are copied into notifications as history, so a deleted user's current
    // name is replaced in the notices other people received in the households they shared.
    // Bulk JPQL update (one SQL statement) instead of loading every row.
    @Modifying
    @Query("update Notification n set n.actorName = :placeholder "
            + "where n.household.id in :householdIds and n.actorName = :name")
    int replaceActorName(@Param("householdIds") List<Long> householdIds,
                         @Param("name") String name,
                         @Param("placeholder") String placeholder);

    @Modifying
    @Query("update Notification n set n.targetName = :placeholder "
            + "where n.household.id in :householdIds and n.targetName = :name")
    int replaceTargetName(@Param("householdIds") List<Long> householdIds,
                          @Param("name") String name,
                          @Param("placeholder") String placeholder);

    // Nightly cleanup of old history. Returns how many rows were deleted.
    @Modifying
    @Query("delete from Notification n where n.createdAt < :cutoff")
    int deleteOlderThan(@Param("cutoff") Instant cutoff);
}
