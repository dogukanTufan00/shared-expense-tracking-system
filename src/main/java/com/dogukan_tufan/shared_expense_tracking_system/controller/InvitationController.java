package com.dogukan_tufan.shared_expense_tracking_system.controller;

import com.dogukan_tufan.shared_expense_tracking_system.dto.invitation.InviteRequest;
import com.dogukan_tufan.shared_expense_tracking_system.dto.invitation.InvitationResponse;
import com.dogukan_tufan.shared_expense_tracking_system.service.InvitationService;
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
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

// Household-side invitation endpoints (invite, list pending, cancel). The invitee's own
// endpoints live in MyInvitationController.
@RestController
@RequestMapping("/api/households/{householdId}/invitations")
@RequiredArgsConstructor
@Tag(name = "Invitations")
public class InvitationController {

    private final InvitationService invitationService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Invite an existing user by email (ADMIN only)")
    public InvitationResponse invite(@PathVariable Long householdId, @Valid @RequestBody InviteRequest request,
                                     Authentication authentication) {
        return invitationService.invite(householdId, request, authentication.getName());
    }

    @GetMapping
    @Operation(summary = "List the pending invitations of this household (any active member)")
    public List<InvitationResponse> listPending(@PathVariable Long householdId, Authentication authentication) {
        return invitationService.listPendingForHousehold(householdId, authentication.getName());
    }

    @DeleteMapping("/{invitationId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Cancel a pending invitation (ADMIN only)")
    public void cancel(@PathVariable Long householdId, @PathVariable Long invitationId,
                       Authentication authentication) {
        invitationService.cancel(householdId, invitationId, authentication.getName());
    }
}
