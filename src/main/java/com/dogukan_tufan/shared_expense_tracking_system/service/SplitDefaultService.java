package com.dogukan_tufan.shared_expense_tracking_system.service;

import com.dogukan_tufan.shared_expense_tracking_system.dto.splitdefault.CategorySplitDefaultResponse;
import com.dogukan_tufan.shared_expense_tracking_system.dto.splitdefault.SplitDefaultRequest;
import com.dogukan_tufan.shared_expense_tracking_system.entity.CategorySplitDefault;
import com.dogukan_tufan.shared_expense_tracking_system.entity.ExpenseCategory;
import com.dogukan_tufan.shared_expense_tracking_system.entity.Household;
import com.dogukan_tufan.shared_expense_tracking_system.entity.Membership;
import com.dogukan_tufan.shared_expense_tracking_system.entity.User;
import com.dogukan_tufan.shared_expense_tracking_system.exception.ResourceNotFoundException;
import com.dogukan_tufan.shared_expense_tracking_system.repository.CategorySplitDefaultRepository;
import com.dogukan_tufan.shared_expense_tracking_system.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

// Admin-managed default percentages per expense category. Members can read them; only admins change them.
@Service
@RequiredArgsConstructor
@Transactional
public class SplitDefaultService {

    private final UserRepository userRepository;
    private final CategorySplitDefaultRepository splitDefaultRepository;
    private final HouseholdAccessService accessService;
    private final ShareValidator shareValidator;

    public List<CategorySplitDefaultResponse> listDefaults(Long householdId, String currentUserEmail) {
        User currentUser = getUser(currentUserEmail);
        accessService.requireActiveMembership(currentUser.getId(), householdId);

        // Group rows by category. EnumMap keeps the categories in their enum order (RENT, UTILITIES, ...).
        Map<ExpenseCategory, List<CategorySplitDefault>> grouped = splitDefaultRepository
                .findByHouseholdId(householdId).stream()
                .collect(Collectors.groupingBy(CategorySplitDefault::getCategory,
                        () -> new EnumMap<>(ExpenseCategory.class), Collectors.toList()));

        return grouped.entrySet().stream()
                .map(entry -> CategorySplitDefaultResponse.of(entry.getKey(), entry.getValue()))
                .toList();
    }

    // Replaces the whole default for one category. The admin sends the full list each time, so the
    // saved default always adds up to 100 and always matches exactly what was sent.
    public CategorySplitDefaultResponse replaceDefault(Long householdId, ExpenseCategory category,
                                                       SplitDefaultRequest request, String currentUserEmail) {
        User currentUser = getUser(currentUserEmail);
        Membership membership = accessService.requireActiveMembership(currentUser.getId(), householdId);
        accessService.requireAdmin(membership);

        // Same rules as an expense split: active members only, no duplicates, percentages add up to 100.
        List<User> users = shareValidator.requireValidParticipants(householdId, request.shares());
        shareValidator.requirePercentages(request.shares());

        splitDefaultRepository.deleteByHouseholdIdAndCategory(householdId, category);
        // Hibernate runs INSERTs before DELETEs unless told otherwise. Flushing here makes the delete
        // reach the database first, so re-inserting the same (household, category, user) cannot hit the unique key.
        splitDefaultRepository.flush();

        Household household = membership.getHousehold();
        List<CategorySplitDefault> saved = new ArrayList<>();
        for (int i = 0; i < users.size(); i++) {
            CategorySplitDefault row = new CategorySplitDefault(
                    household, category, users.get(i), request.shares().get(i).value().setScale(2));
            saved.add(splitDefaultRepository.save(row));
        }
        return CategorySplitDefaultResponse.of(category, saved);
    }

    public void deleteDefault(Long householdId, ExpenseCategory category, String currentUserEmail) {
        User currentUser = getUser(currentUserEmail);
        Membership membership = accessService.requireActiveMembership(currentUser.getId(), householdId);
        accessService.requireAdmin(membership);

        splitDefaultRepository.deleteByHouseholdIdAndCategory(householdId, category);
    }

    private User getUser(String email) {
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + email));
    }
}
