package com.dogukan_tufan.shared_expense_tracking_system.repository;

import com.dogukan_tufan.shared_expense_tracking_system.entity.Invitation;
import com.dogukan_tufan.shared_expense_tracking_system.entity.InvitationStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface InvitationRepository extends JpaRepository<Invitation, Long> {

    // The invitee's own invitation. Filtering by the user in the query means someone else's
    // invitation id simply comes back empty (404), instead of being loaded and then rejected.
    Optional<Invitation> findByIdAndInvitedUserId(Long id, Long invitedUserId);

    // An invitation inside a specific household (admin cancel: an id from another household must not work).
    Optional<Invitation> findByIdAndHouseholdId(Long id, Long householdId);

    // "My invitations" (GET /api/invitations): the invitee's pending ones, newest first.
    List<Invitation> findByInvitedUserIdAndStatusOrderByCreatedAtDesc(Long invitedUserId, InvitationStatus status);

    // Pending invitations of one household (GET /api/households/{id}/invitations).
    List<Invitation> findByHouseholdIdAndStatusOrderByCreatedAtDesc(Long householdId, InvitationStatus status);

    // Blocks a second pending invitation to the same person for the same household.
    boolean existsByHouseholdIdAndInvitedUserIdAndStatus(Long householdId, Long invitedUserId, InvitationStatus status);

    // Pending invitations take up a seat in the household limit (see InvitationService.invite).
    long countByHouseholdIdAndStatus(Long householdId, InvitationStatus status);

    // Used when deleting a household: every invitation holds a foreign key to it.
    void deleteByHouseholdId(Long householdId);
}
