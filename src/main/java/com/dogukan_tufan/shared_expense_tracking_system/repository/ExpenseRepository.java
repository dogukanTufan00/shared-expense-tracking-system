package com.dogukan_tufan.shared_expense_tracking_system.repository;

import com.dogukan_tufan.shared_expense_tracking_system.entity.Expense;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ExpenseRepository extends JpaRepository<Expense, Long> {

    List<Expense> findByHouseholdId(Long householdId);

    // Scoped by householdId too, so a valid expense id from another household can't be read/edited through this path.
    Optional<Expense> findByIdAndHouseholdId(Long id, Long householdId);

    // Used when deleting a household: its expenses hold a foreign key to it, so they must go first.
    void deleteByHouseholdId(Long householdId);
}
