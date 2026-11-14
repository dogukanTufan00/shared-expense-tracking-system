package com.dogukan_tufan.shared_expense_tracking_system.repository;

import com.dogukan_tufan.shared_expense_tracking_system.entity.DepositShare;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface DepositShareRepository extends JpaRepository<DepositShare, Long> {

    // All shares of a household (current and former members). Input for the response and for changes.
    List<DepositShare> findByHouseholdId(Long householdId);

    // One person's share in a household, if they have a row yet.
    Optional<DepositShare> findByHouseholdIdAndUserId(Long householdId, Long userId);

    // Used when deleting a household, and when the whole deposit is removed.
    void deleteByHouseholdId(Long householdId);
}
