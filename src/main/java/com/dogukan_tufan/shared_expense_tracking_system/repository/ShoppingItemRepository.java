package com.dogukan_tufan.shared_expense_tracking_system.repository;

import com.dogukan_tufan.shared_expense_tracking_system.entity.ShoppingItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface ShoppingItemRepository extends JpaRepository<ShoppingItem, Long> {

    List<ShoppingItem> findByHouseholdId(Long householdId);

    // An id from another household comes back empty, so it answers 404 like a missing item.
    Optional<ShoppingItem> findByIdAndHouseholdId(Long id, Long householdId);

    // Enforces the per-household limit (HouseholdLimits.MAX_SHOPPING_ITEMS_PER_HOUSEHOLD).
    long countByHouseholdId(Long householdId);

    // Bulk deletes: one JPQL statement each, no entities are loaded first.
    @Modifying
    @Query("delete from ShoppingItem i where i.household.id = :householdId and i.boughtAt is not null and i.boughtAt < :cutoff")
    int deleteBoughtBefore(@Param("householdId") Long householdId, @Param("cutoff") Instant cutoff);

    @Modifying
    @Query("delete from ShoppingItem i where i.household.id = :householdId and i.boughtAt is not null")
    int deleteBought(@Param("householdId") Long householdId);

    // Used when deleting a household: items hold foreign keys to it and to expenses.
    @Modifying
    @Query("delete from ShoppingItem i where i.household.id = :householdId")
    int deleteByHouseholdId(@Param("householdId") Long householdId);

    // Used when an expense is deleted: the items stay (as bought), only their link to the expense is removed.
    @Modifying
    @Query("update ShoppingItem i set i.expense = null where i.expense.id = :expenseId")
    int clearExpenseLink(@Param("expenseId") Long expenseId);
}
