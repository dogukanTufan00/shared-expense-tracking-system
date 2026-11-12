package com.dogukan_tufan.shared_expense_tracking_system.repository;

import com.dogukan_tufan.shared_expense_tracking_system.entity.CategorySplitDefault;
import com.dogukan_tufan.shared_expense_tracking_system.entity.ExpenseCategory;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CategorySplitDefaultRepository extends JpaRepository<CategorySplitDefault, Long> {

    // The saved default for one category (used when creating an expense without shares).
    List<CategorySplitDefault> findByHouseholdIdAndCategory(Long householdId, ExpenseCategory category);

    // All defaults of a household (GET /split-defaults).
    List<CategorySplitDefault> findByHouseholdId(Long householdId);

    // Replacing or deleting one category's default.
    void deleteByHouseholdIdAndCategory(Long householdId, ExpenseCategory category);

    // Used when deleting a household: these rows hold a foreign key to it.
    void deleteByHouseholdId(Long householdId);
}
