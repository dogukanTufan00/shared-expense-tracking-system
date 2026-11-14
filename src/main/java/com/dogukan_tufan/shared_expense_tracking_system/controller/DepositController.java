package com.dogukan_tufan.shared_expense_tracking_system.controller;

import com.dogukan_tufan.shared_expense_tracking_system.dto.deposit.DepositResponse;
import com.dogukan_tufan.shared_expense_tracking_system.dto.deposit.JoinDepositRequest;
import com.dogukan_tufan.shared_expense_tracking_system.dto.deposit.MoveOutRequest;
import com.dogukan_tufan.shared_expense_tracking_system.dto.deposit.UpdateDepositRequest;
import com.dogukan_tufan.shared_expense_tracking_system.service.DepositService;
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

// Thin controller: validates the request, calls DepositService, returns the DTO.
@RestController
@RequestMapping("/api/households/{householdId}/deposit")
@RequiredArgsConstructor
@Tag(name = "Deposit")
public class DepositController {

    private final DepositService depositService;

    @GetMapping
    @Operation(summary = "Get the rent deposit: total, each person's share, and the hand-over history")
    public DepositResponse get(@PathVariable Long householdId, Authentication authentication) {
        return depositService.get(householdId, authentication.getName());
    }

    @PutMapping
    @Operation(summary = "Set or correct the deposit by hand (admin only). Shares must add up to the total")
    public DepositResponse update(@PathVariable Long householdId, @Valid @RequestBody UpdateDepositRequest request,
                                  Authentication authentication) {
        return depositService.update(householdId, request, authentication.getName());
    }

    @DeleteMapping
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Remove the deposit record (admin only). Refused while deposit transfers exist")
    public void delete(@PathVariable Long householdId, Authentication authentication) {
        depositService.delete(householdId, authentication.getName());
    }

    @PostMapping("/move-out")
    @Operation(summary = "Hand a person's whole deposit share to the other active members (the person or an admin)")
    public DepositResponse moveOut(@PathVariable Long householdId, @Valid @RequestBody MoveOutRequest request,
                                   Authentication authentication) {
        return depositService.moveOut(householdId, request, authentication.getName());
    }

    @PostMapping("/join")
    @Operation(summary = "Let a new member buy into the deposit from the current owners (the newcomer or an admin)")
    public DepositResponse join(@PathVariable Long householdId, @Valid @RequestBody JoinDepositRequest request,
                                Authentication authentication) {
        return depositService.join(householdId, request, authentication.getName());
    }

    @DeleteMapping("/transfers/{transferId}")
    @Operation(summary = "Undo one deposit transfer: the amount goes back to the giver (admin only)")
    public DepositResponse deleteTransfer(@PathVariable Long householdId, @PathVariable Long transferId,
                                          Authentication authentication) {
        return depositService.deleteTransfer(householdId, transferId, authentication.getName());
    }
}
