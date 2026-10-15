package com.dogukan_tufan.shared_expense_tracking_system.controller;

import com.dogukan_tufan.shared_expense_tracking_system.dto.household.AddMemberRequest;
import com.dogukan_tufan.shared_expense_tracking_system.dto.household.MemberResponse;
import com.dogukan_tufan.shared_expense_tracking_system.service.MembershipService;
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

@RestController
@RequestMapping("/api/households/{householdId}/members")
@RequiredArgsConstructor
@Tag(name = "Members")
public class MemberController {

    private final MembershipService membershipService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public MemberResponse add(@PathVariable Long householdId, @Valid @RequestBody AddMemberRequest request,
                               Authentication authentication) {
        return membershipService.addMember(householdId, request, authentication.getName());
    }

    @GetMapping
    public List<MemberResponse> list(@PathVariable Long householdId, Authentication authentication) {
        return membershipService.listActiveMembers(householdId, authentication.getName());
    }

    @DeleteMapping("/{userId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void remove(@PathVariable Long householdId, @PathVariable Long userId, Authentication authentication) {
        membershipService.removeMember(householdId, userId, authentication.getName());
    }
}
