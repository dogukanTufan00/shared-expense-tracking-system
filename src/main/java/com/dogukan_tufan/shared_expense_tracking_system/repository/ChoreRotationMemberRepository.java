package com.dogukan_tufan.shared_expense_tracking_system.repository;

import com.dogukan_tufan.shared_expense_tracking_system.entity.ChoreRotationMember;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ChoreRotationMemberRepository extends JpaRepository<ChoreRotationMember, Long> {

    // One chore's rotation in turn order.
    List<ChoreRotationMember> findByChoreIdOrderByPositionAsc(Long choreId);

    // All rotations of a household in one query (the chore list), grouped by chore in the service.
    List<ChoreRotationMember> findByChoreHouseholdIdOrderByChoreIdAscPositionAsc(Long householdId);

    void deleteByChoreId(Long choreId);

    // Used when deleting a household.
    void deleteByChoreHouseholdId(Long householdId);
}
