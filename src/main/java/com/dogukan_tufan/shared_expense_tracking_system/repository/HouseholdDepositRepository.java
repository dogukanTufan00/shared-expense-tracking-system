package com.dogukan_tufan.shared_expense_tracking_system.repository;

import com.dogukan_tufan.shared_expense_tracking_system.entity.HouseholdDeposit;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface HouseholdDepositRepository extends JpaRepository<HouseholdDeposit, Long> {

    // A household has at most one deposit record, so this is either empty or one row.
    Optional<HouseholdDeposit> findByHouseholdId(Long householdId);

    // Used when deleting a household: the deposit row holds a foreign key to it.
    void deleteByHouseholdId(Long householdId);
}
