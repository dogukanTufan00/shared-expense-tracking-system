package com.dogukan_tufan.shared_expense_tracking_system.controller;

import com.dogukan_tufan.shared_expense_tracking_system.dto.household.HouseholdResponse;
import com.dogukan_tufan.shared_expense_tracking_system.dto.invitation.InvitationResponse;
import com.dogukan_tufan.shared_expense_tracking_system.service.InvitationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

// The logged-in user's side of invitations: see what they were invited to, then accept or decline.
@RestController
@RequestMapping("/api/invitations")
@RequiredArgsConstructor
@Tag(name = "My invitations")
public class MyInvitationController {

    private final InvitationService invitationService;

    @GetMapping
    @Operation(summary = "List my pending invitations, newest first")
    public List<InvitationResponse> listMine(Authentication authentication) {
        return invitationService.listMyPending(authentication.getName());
    }

    @PostMapping("/{invitationId}/accept")
    @Operation(summary = "Accept an invitation and join the household")
    public HouseholdResponse accept(@PathVariable Long invitationId, Authentication authentication) {
        return invitationService.accept(invitationId, authentication.getName());
    }

    @PostMapping("/{invitationId}/decline")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Decline an invitation")
    public void decline(@PathVariable Long invitationId, Authentication authentication) {
        invitationService.decline(invitationId, authentication.getName());
    }
}
