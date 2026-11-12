package com.dogukan_tufan.shared_expense_tracking_system.dto.statement;

import com.dogukan_tufan.shared_expense_tracking_system.entity.ExpenseCategory;
import com.dogukan_tufan.shared_expense_tracking_system.entity.SplitType;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

// The period statement ("hesap dokumu") for one household. Everything here is computed on the fly
// from expenses, shares and payments; nothing is stored. The nested records are the building blocks
// of the top-level response, kept in this one file so the statement's shape is easy to read in one place.
public record StatementResponse(
        LocalDate from,
        LocalDate to,
        String householdName,
        BigDecimal totalExpenses,
        int expenseCount,
        BigDecimal totalPayments,
        int paymentCount,
        List<MemberStat> members,
        // Null when nobody in the period is clearly carrying the household (see StatementService).
        DominantPayer dominantPayer,
        List<CategoryStat> categories,
        List<MonthStat> trend,
        List<StatementExpense> expenses,
        List<StatementPayment> payments
) {

    // One row per person. "paid" is what they fronted, "share" is what they owe, and "net" is the
    // difference. Payments are shown separately and are NOT part of net (they are the way debts get settled).
    public record MemberStat(
            Long userId,
            String name,
            boolean active,
            BigDecimal paid,
            BigDecimal share,
            BigDecimal net,
            BigDecimal paymentsSent,
            BigDecimal paymentsReceived,
            BigDecimal paidPercent
    ) {
    }

    public record DominantPayer(
            Long userId,
            String name,
            BigDecimal paidPercent
    ) {
    }

    public record CategoryStat(
            ExpenseCategory category,
            BigDecimal total,
            int count,
            BigDecimal percent
    ) {
    }

    // One calendar month of the 6-month trend. month is the first day of that month.
    public record MonthStat(
            LocalDate month,
            BigDecimal total,
            List<MonthMemberPaid> perMember
    ) {
    }

    public record MonthMemberPaid(
            Long userId,
            String name,
            BigDecimal paid
    ) {
    }

    public record StatementExpense(
            Long id,
            LocalDateTime date,
            String description,
            ExpenseCategory category,
            BigDecimal amount,
            String paidByName,
            SplitType splitType,
            List<StatementShare> shares
    ) {
    }

    public record StatementShare(
            String name,
            BigDecimal owedAmount
    ) {
    }

    public record StatementPayment(
            Long id,
            Instant paidAt,
            String fromName,
            String toName,
            BigDecimal amount,
            String note
    ) {
    }
}
