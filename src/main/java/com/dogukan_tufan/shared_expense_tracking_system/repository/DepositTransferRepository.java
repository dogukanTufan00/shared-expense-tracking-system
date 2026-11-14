package com.dogukan_tufan.shared_expense_tracking_system.repository;

import com.dogukan_tufan.shared_expense_tracking_system.entity.DepositTransfer;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface DepositTransferRepository extends JpaRepository<DepositTransfer, Long> {

    // Newest first (the app's list). The id breaks ties for transfers made in the same instant.
    List<DepositTransfer> findByHouseholdIdOrderByCreatedAtDescIdDesc(Long householdId);

    // Every transfer of a household, for balances (BalanceService).
    List<DepositTransfer> findByHouseholdId(Long householdId);

    // An id from another household comes back empty, so it answers 404.
    Optional<DepositTransfer> findByIdAndHouseholdId(Long id, Long householdId);

    // Deleting the whole deposit is refused while any transfer exists (balances would lose their source).
    long countByHouseholdId(Long householdId);

    // Used when deleting a household.
    void deleteByHouseholdId(Long householdId);
}
