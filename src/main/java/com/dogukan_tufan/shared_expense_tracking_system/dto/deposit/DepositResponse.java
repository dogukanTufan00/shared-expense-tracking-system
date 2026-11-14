package com.dogukan_tufan.shared_expense_tracking_system.dto.deposit;

import java.math.BigDecimal;
import java.util.List;

// The whole deposit picture for one household. Returned by every deposit endpoint.
// totalAmount is null (and unassigned too) when no deposit has been recorded yet.
// unassigned = totalAmount - sum of all shares. It is 0 while the shares add up, which the rules keep true.
public record DepositResponse(
        BigDecimal totalAmount,
        List<DepositShareResponse> shares,
        BigDecimal unassigned,
        List<DepositTransferResponse> transfers
) {
}
