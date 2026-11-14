package com.dogukan_tufan.shared_expense_tracking_system.controller;

import com.dogukan_tufan.shared_expense_tracking_system.dto.expense.ExpenseResponse;
import com.dogukan_tufan.shared_expense_tracking_system.dto.shopping.ShoppingCheckoutRequest;
import com.dogukan_tufan.shared_expense_tracking_system.dto.shopping.ShoppingItemRequest;
import com.dogukan_tufan.shared_expense_tracking_system.dto.shopping.ShoppingItemResponse;
import com.dogukan_tufan.shared_expense_tracking_system.service.ShoppingItemService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/households/{householdId}/shopping-items")
@RequiredArgsConstructor
@Tag(name = "Shopping list")
public class ShoppingItemController {

    private final ShoppingItemService shoppingItemService;

    // The item id is limited to digits, so "/bought" (DELETE) is never mistaken for an item id.
    @GetMapping
    @Operation(summary = "List the shopping list: open items first, then bought items (bought ones older than 7 days are removed)")
    public List<ShoppingItemResponse> list(@PathVariable Long householdId, Authentication authentication) {
        return shoppingItemService.list(householdId, authentication.getName());
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Add an item to the shopping list")
    public ShoppingItemResponse create(@PathVariable Long householdId, @Valid @RequestBody ShoppingItemRequest request,
                                       Authentication authentication) {
        return shoppingItemService.create(householdId, request, authentication.getName());
    }

    @PutMapping("/{itemId:\\d+}")
    @Operation(summary = "Edit an item's name and note")
    public ShoppingItemResponse update(@PathVariable Long householdId, @PathVariable Long itemId,
                                       @Valid @RequestBody ShoppingItemRequest request, Authentication authentication) {
        return shoppingItemService.update(householdId, itemId, request, authentication.getName());
    }

    @DeleteMapping("/{itemId:\\d+}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Remove an item from the shopping list")
    public void delete(@PathVariable Long householdId, @PathVariable Long itemId, Authentication authentication) {
        shoppingItemService.delete(householdId, itemId, authentication.getName());
    }

    @PostMapping("/{itemId:\\d+}/bought")
    @Operation(summary = "Mark an item as bought (if it is already bought, the first buyer is kept)")
    public ShoppingItemResponse markBought(@PathVariable Long householdId, @PathVariable Long itemId,
                                           Authentication authentication) {
        return shoppingItemService.markBought(householdId, itemId, authentication.getName());
    }

    @PostMapping("/{itemId:\\d+}/restore")
    @Operation(summary = "Move a bought item back to the open list (the expense it was saved into is kept)")
    public ShoppingItemResponse restore(@PathVariable Long householdId, @PathVariable Long itemId,
                                        Authentication authentication) {
        return shoppingItemService.restore(householdId, itemId, authentication.getName());
    }

    @DeleteMapping("/bought")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Remove all bought items from the shopping list")
    public void clearBought(@PathVariable Long householdId, Authentication authentication) {
        shoppingItemService.clearBought(householdId, authentication.getName());
    }

    @PostMapping("/checkout")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Save the checked items as one expense; the items become bought and are linked to it")
    public ExpenseResponse checkout(@PathVariable Long householdId, @Valid @RequestBody ShoppingCheckoutRequest request,
                                    Authentication authentication) {
        return shoppingItemService.checkout(householdId, request, authentication.getName());
    }
}
