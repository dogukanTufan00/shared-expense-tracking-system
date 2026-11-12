package com.dogukan_tufan.shared_expense_tracking_system.repository;

import com.dogukan_tufan.shared_expense_tracking_system.entity.Expense;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface ExpenseRepository extends JpaRepository<Expense, Long> {

    List<Expense> findByHouseholdId(Long householdId);

    // Expenses whose expenseDate is in [start, end). The statement passes midnight of the first day
    // and midnight of the day AFTER the last day, so both ends of the period are included exactly once.
    List<Expense> findByHouseholdIdAndExpenseDateGreaterThanEqualAndExpenseDateLessThan(
            Long householdId, LocalDateTime start, LocalDateTime end);

    // Scoped by householdId too, so a valid expense id from another household can't be read/edited through this path.
    Optional<Expense> findByIdAndHouseholdId(Long id, Long householdId);

    // Used when deleting a household: its expenses hold a foreign key to it, so they must go first.
    void deleteByHouseholdId(Long householdId);

    // Last expense of each recurring chain (the flag sits only on the newest one).
    List<Expense> findByRecurringTrue();

    List<Expense> findByHouseholdIdAndRecurringTrue(Long householdId);

    // True when some expense was generated from this one, i.e. it is not the end of its chain.
    boolean existsByGeneratedFromId(Long generatedFromId);

    // Locks the row until the transaction ends. Why: the daily job and the manual "run now" endpoint
    // could otherwise read the same chain end at the same time and both create the next month.
    // With the lock, the second one waits, then sees recurring=false and creates nothing.
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select e from Expense e where e.id = :id")
    Optional<Expense> findByIdForUpdate(@Param("id") Long id);

    // Detaches children before their parent is deleted, otherwise the foreign key blocks the delete.
    @Modifying
    @Query("update Expense e set e.generatedFrom = null where e.generatedFrom = :parent")
    void clearGeneratedFrom(@Param("parent") Expense parent);

    // Same as above for a whole household, used before deleteByHouseholdId.
    @Modifying
    @Query("update Expense e set e.generatedFrom = null where e.household.id = :householdId")
    void clearGeneratedFromForHousehold(@Param("householdId") Long householdId);
}
