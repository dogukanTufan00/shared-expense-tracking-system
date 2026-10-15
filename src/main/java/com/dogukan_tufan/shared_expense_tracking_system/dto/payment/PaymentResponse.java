package com.dogukan_tufan.shared_expense_tracking_system.dto.payment;

import com.dogukan_tufan.shared_expense_tracking_system.entity.Payment;

import java.math.BigDecimal;
import java.time.Instant;

public record PaymentResponse(
        Long id,
        Long householdId,
        Long fromUserId,
        String fromUserName,
        Long toUserId,
        String toUserName,
        BigDecimal amount,
        Instant paidAt
) {

    public static PaymentResponse from(Payment payment) {
        return new PaymentResponse(
                payment.getId(),
                payment.getHousehold().getId(),
                payment.getFromUser().getId(),
                payment.getFromUser().getName(),
                payment.getToUser().getId(),
                payment.getToUser().getName(),
                payment.getAmount(),
                payment.getPaidAt());
    }
}
