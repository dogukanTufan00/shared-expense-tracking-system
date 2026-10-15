package com.dogukan_tufan.shared_expense_tracking_system.repository;

import com.dogukan_tufan.shared_expense_tracking_system.entity.Membership;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface MembershipRepository extends JpaRepository<Membership, Long> {

    // "Active" = leftAt is null. Used for authorization checks and the duplicate-member rule.
    Optional<Membership> findByUserIdAndHouseholdIdAndLeftAtIsNull(Long userId, Long householdId);

    // Active members of a household (GET /households/{id}/members, and the max-3-members rule).
    List<Membership> findByHouseholdIdAndLeftAtIsNull(Long householdId);

    long countByHouseholdIdAndLeftAtIsNull(Long householdId);

    // Households a given user currently belongs to (GET /households).
    List<Membership> findByUserIdAndLeftAtIsNull(Long userId);

    // Every membership, including people who have left (balances: a former member can still owe money).
    List<Membership> findByHouseholdId(Long householdId);

    // Used when deleting a household: every membership (active or not) holds a foreign key to it.
    void deleteByHouseholdId(Long householdId);
}
