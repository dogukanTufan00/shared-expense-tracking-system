package com.dogukan_tufan.shared_expense_tracking_system.repository;

import com.dogukan_tufan.shared_expense_tracking_system.entity.ActivityLog;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ActivityLogRepository extends JpaRepository<ActivityLog, Long> {

    // The newest page of a household's history. Ordered by id because ids grow with every insert.
    List<ActivityLog> findByHouseholdIdOrderByIdDesc(Long householdId, Pageable pageable);

    // The next page: only rows older than the last id the app already has (cursor paging).
    List<ActivityLog> findByHouseholdIdAndIdLessThanOrderByIdDesc(Long householdId, Long id, Pageable pageable);

    // Used when deleting a household: its history rows hold a foreign key to it, so they go first.
    void deleteByHouseholdId(Long householdId);
}
