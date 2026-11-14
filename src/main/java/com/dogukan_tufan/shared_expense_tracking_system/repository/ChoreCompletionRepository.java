package com.dogukan_tufan.shared_expense_tracking_system.repository;

import com.dogukan_tufan.shared_expense_tracking_system.entity.ChoreCompletion;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ChoreCompletionRepository extends JpaRepository<ChoreCompletion, Long> {

    // The newest 5 completions of one chore (the "recent" list on the chore).
    List<ChoreCompletion> findTop5ByChoreIdOrderByDoneAtDescIdDesc(Long choreId);

    void deleteByChoreId(Long choreId);

    // Used when deleting a household.
    void deleteByChoreHouseholdId(Long householdId);
}
