package com.dogukan_tufan.shared_expense_tracking_system.dto.auth;

import com.dogukan_tufan.shared_expense_tracking_system.entity.User;

import java.time.Instant;

// What we return for a User -- notice passwordHash is NOT a field here. Controllers must never
// return the entity itself (CLAUDE.md section 5): this response DTO is the boundary that keeps
// the hash from ever reaching a client.
public record UserResponse(
        Long id,
        String name,
        String email,
        String iban,
        Instant createdAt
) {

    public static UserResponse from(User user) {
        return new UserResponse(user.getId(), user.getName(), user.getEmail(), user.getIban(), user.getCreatedAt());
    }
}
