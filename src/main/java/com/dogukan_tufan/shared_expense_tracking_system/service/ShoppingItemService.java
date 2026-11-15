package com.dogukan_tufan.shared_expense_tracking_system.service;

import com.dogukan_tufan.shared_expense_tracking_system.dto.expense.ExpenseRequest;
import com.dogukan_tufan.shared_expense_tracking_system.dto.expense.ExpenseResponse;
import com.dogukan_tufan.shared_expense_tracking_system.dto.shopping.ShoppingCheckoutRequest;
import com.dogukan_tufan.shared_expense_tracking_system.dto.shopping.ShoppingItemRequest;
import com.dogukan_tufan.shared_expense_tracking_system.dto.shopping.ShoppingItemResponse;
import com.dogukan_tufan.shared_expense_tracking_system.entity.Expense;
import com.dogukan_tufan.shared_expense_tracking_system.entity.Membership;
import com.dogukan_tufan.shared_expense_tracking_system.entity.ShoppingItem;
import com.dogukan_tufan.shared_expense_tracking_system.entity.User;
import com.dogukan_tufan.shared_expense_tracking_system.exception.BadRequestException;
import com.dogukan_tufan.shared_expense_tracking_system.exception.ConflictException;
import com.dogukan_tufan.shared_expense_tracking_system.exception.ResourceNotFoundException;
import com.dogukan_tufan.shared_expense_tracking_system.repository.ExpenseRepository;
import com.dogukan_tufan.shared_expense_tracking_system.repository.ShoppingItemRepository;
import com.dogukan_tufan.shared_expense_tracking_system.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

// The shared shopping list ("Sut", "Deterjan"). Anyone in the household can add, buy and remove items.
//
// Why no activity log and no notifications: shopping items change too often; they would flood the history
// and the roommates' notification lists.
// Why checkout calls ExpenseService: saving the shopping into an expense must follow the normal expense
// rules (payer, split, amount). Reusing the service keeps those rules in one place. The reverse is not
// true: ExpenseService never calls this service, so there is no circular dependency.
@Service
@RequiredArgsConstructor
@Transactional
public class ShoppingItemService {

    // A bought item stays on the list for a week (so people can still see what was bought), then it goes.
    // No scheduler is needed: the list deletes old bought items itself whenever it is opened.
    static final Duration BOUGHT_ITEMS_KEPT_FOR = Duration.ofDays(7);

    private final UserRepository userRepository;
    private final ExpenseRepository expenseRepository;
    private final ShoppingItemRepository shoppingItemRepository;
    private final HouseholdAccessService accessService;
    private final ExpenseService expenseService;

    // Not read-only: the first step deletes old bought items.
    public List<ShoppingItemResponse> list(Long householdId, String currentUserEmail) {
        User caller = getUser(currentUserEmail);
        accessService.requireActiveMembership(caller.getId(), householdId);

        // Cleanup first, so the response never contains items that are already too old.
        shoppingItemRepository.deleteBoughtBefore(householdId, Instant.now().minus(BOUGHT_ITEMS_KEPT_FOR));

        List<ShoppingItem> items = shoppingItemRepository.findByHouseholdId(householdId);

        // Open items: oldest first, so the list reads like a shopping list. Bought items: newest purchase first.
        // The id breaks ties so the order is always the same.
        List<ShoppingItem> open = items.stream()
                .filter(item -> !item.isBought())
                .sorted(Comparator.comparing(ShoppingItem::getCreatedAt).thenComparing(ShoppingItem::getId))
                .toList();
        List<ShoppingItem> bought = items.stream()
                .filter(ShoppingItem::isBought)
                .sorted(Comparator.comparing(ShoppingItem::getBoughtAt).reversed().thenComparing(ShoppingItem::getId))
                .toList();

        return Stream.concat(open.stream(), bought.stream())
                .map(this::toResponse)
                .toList();
    }

    public ShoppingItemResponse create(Long householdId, ShoppingItemRequest request, String currentUserEmail) {
        User caller = getUser(currentUserEmail);
        Membership membership = accessService.requireActiveMembership(caller.getId(), householdId);

        if (shoppingItemRepository.countByHouseholdId(householdId) >= HouseholdLimits.MAX_SHOPPING_ITEMS_PER_HOUSEHOLD) {
            throw new ConflictException("A household can have at most "
                    + HouseholdLimits.MAX_SHOPPING_ITEMS_PER_HOUSEHOLD + " shopping items");
        }

        ShoppingItem item = new ShoppingItem(membership.getHousehold(), requireName(request.name()),
                cleanNote(request.note()), caller);
        shoppingItemRepository.save(item);
        return toResponse(item);
    }

    public ShoppingItemResponse update(Long householdId, Long itemId, ShoppingItemRequest request,
                                       String currentUserEmail) {
        User caller = getUser(currentUserEmail);
        accessService.requireActiveMembership(caller.getId(), householdId);
        ShoppingItem item = findItem(householdId, itemId);

        item.setName(requireName(request.name()));
        item.setNote(cleanNote(request.note()));
        return toResponse(item);
    }

    public void delete(Long householdId, Long itemId, String currentUserEmail) {
        User caller = getUser(currentUserEmail);
        accessService.requireActiveMembership(caller.getId(), householdId);
        ShoppingItem item = findItem(householdId, itemId);

        // An item saved into an expense can be deleted too: the expense keeps its own data.
        shoppingItemRepository.delete(item);
    }

    // "I bought it". Two roommates may tap at the same time; the first buyer is kept, so a second tap changes nothing.
    public ShoppingItemResponse markBought(Long householdId, Long itemId, String currentUserEmail) {
        User caller = getUser(currentUserEmail);
        accessService.requireActiveMembership(caller.getId(), householdId);
        ShoppingItem item = findItem(householdId, itemId);

        if (!item.isBought()) {
            item.markBought(caller, Instant.now());
        }
        return toResponse(item);
    }

    // Back to the open list (e.g. someone tapped "bought" by mistake).
    // An item already saved into an expense cannot come back: it could then be saved a second time and
    // the same goods would be charged twice. The app offers "add again" instead, which adds a new item.
    public ShoppingItemResponse restore(Long householdId, Long itemId, String currentUserEmail) {
        User caller = getUser(currentUserEmail);
        accessService.requireActiveMembership(caller.getId(), householdId);
        ShoppingItem item = findItem(householdId, itemId);

        if (item.getExpense() != null) {
            throw new ConflictException("This item is saved as an expense; add it to the list again instead");
        }
        if (item.isBought()) {
            item.restore();
        }
        return toResponse(item);
    }

    // Removes every bought item at once, e.g. after the shopping is put away.
    public void clearBought(Long householdId, String currentUserEmail) {
        User caller = getUser(currentUserEmail);
        accessService.requireActiveMembership(caller.getId(), householdId);

        shoppingItemRepository.deleteBought(householdId);
    }

    // Saves the checked items as ONE expense. The expense is created by ExpenseService in this same
    // transaction, so an invalid expense rolls everything back and no item changes.
    public ExpenseResponse checkout(Long householdId, ShoppingCheckoutRequest request, String currentUserEmail) {
        User caller = getUser(currentUserEmail);
        accessService.requireActiveMembership(caller.getId(), householdId);

        // The same item twice would be linked twice and counted twice in the expense.
        List<Long> itemIds = request.itemIds();
        if (new HashSet<>(itemIds).size() != itemIds.size()) {
            throw new BadRequestException("An item can appear only once in a checkout");
        }
        List<ShoppingItem> items = itemIds.stream()
                .map(id -> findItem(householdId, id))
                .toList();

        // An item belongs to at most one expense. Saving it twice would split the same goods twice.
        if (items.stream().anyMatch(item -> item.getExpense() != null)) {
            throw new ConflictException("Some items are already saved as an expense");
        }

        ExpenseResponse saved = expenseService.create(householdId,
                withItemsTextIfMissing(request.expense(), items), currentUserEmail);
        Expense expense = expenseRepository.findById(saved.id())
                .orElseThrow(() -> new ResourceNotFoundException("Expense not found: " + saved.id()));

        // Items nobody has bought yet are marked bought by the caller now, because they went into the expense.
        Instant now = Instant.now();
        for (ShoppingItem item : items) {
            if (!item.isBought()) {
                item.markBought(caller, now);
            }
            item.linkToExpense(expense);
        }
        return saved;
    }

    // ---------- Rules and helpers ----------

    // A checkout whose expense has no product list gets the item names, one per line, in list order
    // (oldest first, as the open list shows them). A product list sent by the app is kept as it is.
    private static ExpenseRequest withItemsTextIfMissing(ExpenseRequest expense, List<ShoppingItem> items) {
        if (ExpenseService.normalizeItemsText(expense.itemsText()) != null) {
            return expense;
        }
        String names = items.stream()
                .sorted(Comparator.comparing(ShoppingItem::getCreatedAt).thenComparing(ShoppingItem::getId))
                .map(item -> item.getName().replace("\r", " ").replace("\n", " "))
                .collect(Collectors.joining("\n"));
        return new ExpenseRequest(
                expense.paidByUserId(),
                expense.amount(),
                expense.category(),
                expense.description(),
                expense.recurring(),
                expense.expenseDate(),
                expense.splitType(),
                expense.shares(),
                expense.reminderDaysBefore(),
                names);
    }

    // Items of ANOTHER household answer 404, the same as a missing item.
    private ShoppingItem findItem(Long householdId, Long itemId) {
        return shoppingItemRepository.findByIdAndHouseholdId(itemId, householdId)
                .orElseThrow(() -> new ResourceNotFoundException("Shopping item not found: " + itemId));
    }

    // The request is validated with @NotBlank, which is stricter than trim(); this catches the rest.
    private static String requireName(String raw) {
        String name = raw.trim();
        if (name.isEmpty()) {
            throw new BadRequestException("Name is required");
        }
        return name;
    }

    // A note that is blank after trimming is stored as null, so "no note" is always the same value.
    private static String cleanNote(String raw) {
        if (raw == null) {
            return null;
        }
        String note = raw.trim();
        return note.isEmpty() ? null : note;
    }

    private ShoppingItemResponse toResponse(ShoppingItem item) {
        User addedBy = item.getAddedBy();
        User boughtBy = item.getBoughtBy();
        Expense expense = item.getExpense();
        return new ShoppingItemResponse(
                item.getId(),
                item.getName(),
                item.getNote(),
                addedBy.getId(),
                addedBy.getName(),
                item.getCreatedAt(),
                item.isBought(),
                boughtBy == null ? null : boughtBy.getId(),
                boughtBy == null ? null : boughtBy.getName(),
                item.getBoughtAt(),
                expense == null ? null : expense.getId());
    }

    private User getUser(String email) {
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + email));
    }
}
