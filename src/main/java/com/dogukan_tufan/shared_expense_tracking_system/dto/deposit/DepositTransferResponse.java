package com.dogukan_tufan.shared_expense_tracking_system.dto.deposit;

import com.dogukan_tufan.shared_expense_tracking_system.entity.DepositTransfer;

import java.math.BigDecimal;
import java.time.Instant;

// One hand-over. fromUser gave up the amount, toUser took it over (so toUser owes fromUser).
// Names are the users' current names.
public record DepositTransferResponse(
        Long id,
        Long fromUserId,
        String fromName,
        Long toUserId,
        String toName,
        BigDecimal amount,
        Instant createdAt,
        String createdByName
) {

    public static DepositTransferResponse from(DepositTransfer transfer) {
        return new DepositTransferResponse(
                transfer.getId(),
                transfer.getFromUser().getId(),
                transfer.getFromUser().getName(),
                transfer.getToUser().getId(),
                transfer.getToUser().getName(),
                transfer.getAmount(),
                transfer.getCreatedAt(),
                transfer.getCreatedBy().getName());
    }
}
