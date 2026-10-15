package com.dogukan_tufan.shared_expense_tracking_system.repository;

import com.dogukan_tufan.shared_expense_tracking_system.entity.ExpenseShare;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ExpenseShareRepository extends JpaRepository<ExpenseShare, Long> {

    // Used to recompute an expense's shares from scratch on create/update (ExpenseService).
    void deleteByExpenseId(Long expenseId);

    // On update, tells ExpenseService who originally shared this expense.
    List<ExpenseShare> findByExpenseId(Long expenseId);

    // All shares across a household -- the input to balance calculation (BalanceService), and
    // what must be cleared before that household's expenses can be deleted.
    List<ExpenseShare> findByExpenseHouseholdId(Long householdId);

    void deleteByExpenseHouseholdId(Long householdId);
}
