package com.dogukan_tufan.shared_expense_tracking_system.controller;

import com.dogukan_tufan.shared_expense_tracking_system.dto.chore.ChoreReminderResponse;
import com.dogukan_tufan.shared_expense_tracking_system.dto.chore.ChoreResponse;
import com.dogukan_tufan.shared_expense_tracking_system.dto.chore.CreateChoreRequest;
import com.dogukan_tufan.shared_expense_tracking_system.dto.chore.UpdateChoreRequest;
import com.dogukan_tufan.shared_expense_tracking_system.service.ChoreService;
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
@RequestMapping("/api/households/{householdId}/chores")
@RequiredArgsConstructor
@Tag(name = "Chores")
public class ChoreController {

    private final ChoreService choreService;

    @GetMapping
    @Operation(summary = "List the household's chores with their rotation, current person and recent completions")
    public List<ChoreResponse> list(@PathVariable Long householdId, Authentication authentication) {
        return choreService.list(householdId, authentication.getName());
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Add a chore with its rotation (the first person in the list starts)")
    public ChoreResponse create(@PathVariable Long householdId, @Valid @RequestBody CreateChoreRequest request,
                                Authentication authentication) {
        return choreService.create(householdId, request, authentication.getName());
    }

    @PutMapping("/{choreId}")
    @Operation(summary = "Edit a chore: name, rotation order and optionally whose turn it is")
    public ChoreResponse update(@PathVariable Long householdId, @PathVariable Long choreId,
                                @Valid @RequestBody UpdateChoreRequest request, Authentication authentication) {
        return choreService.update(householdId, choreId, request, authentication.getName());
    }

    @DeleteMapping("/{choreId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Delete a chore together with its history")
    public void delete(@PathVariable Long householdId, @PathVariable Long choreId, Authentication authentication) {
        choreService.delete(householdId, choreId, authentication.getName());
    }

    @PostMapping("/{choreId}/done")
    @Operation(summary = "Mark the chore as done (any member may do it); the turn moves to the next person")
    public ChoreResponse markDone(@PathVariable Long householdId, @PathVariable Long choreId,
                                  Authentication authentication) {
        return choreService.markDone(householdId, choreId, authentication.getName());
    }

    @PostMapping("/{choreId}/remind")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Remind the person whose turn it is (max once per 24 hours per chore and person)")
    public ChoreReminderResponse remind(@PathVariable Long householdId, @PathVariable Long choreId,
                                        Authentication authentication) {
        return choreService.remind(householdId, choreId, authentication.getName());
    }
}
