package com.dogukan_tufan.shared_expense_tracking_system.repository;

import com.dogukan_tufan.shared_expense_tracking_system.entity.Payment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface PaymentRepository extends JpaRepository<Payment, Long> {

    List<Payment> findByHouseholdId(Long householdId);

    // Payments whose paidAt is in [start, end). The service turns the Turkish calendar dates into Instants.
    List<Payment> findByHouseholdIdAndPaidAtGreaterThanEqualAndPaidAtLessThan(
            Long householdId, Instant start, Instant end);

    Optional<Payment> findByIdAndHouseholdId(Long id, Long householdId);

    // Used when deleting a household: its payments hold a foreign key to it.
    void deleteByHouseholdId(Long householdId);
}
