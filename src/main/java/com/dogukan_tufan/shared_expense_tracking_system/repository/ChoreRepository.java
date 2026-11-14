package com.dogukan_tufan.shared_expense_tracking_system.repository;

import com.dogukan_tufan.shared_expense_tracking_system.entity.Chore;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ChoreRepository extends JpaRepository<Chore, Long> {

    List<Chore> findByHouseholdId(Long householdId);

    // An id from another household comes back empty, so it answers 404 like a missing chore.
    Optional<Chore> findByIdAndHouseholdId(Long id, Long householdId);

    // Enforces the per-household limit (HouseholdLimits.MAX_CHORES_PER_HOUSEHOLD).
    long countByHouseholdId(Long householdId);

    // Used when deleting a household: chores hold a foreign key to it.
    void deleteByHouseholdId(Long householdId);
}
