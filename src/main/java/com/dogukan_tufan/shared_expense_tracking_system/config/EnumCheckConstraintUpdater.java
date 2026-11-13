package com.dogukan_tufan.shared_expense_tracking_system.config;

import com.dogukan_tufan.shared_expense_tracking_system.entity.ActivityType;
import com.dogukan_tufan.shared_expense_tracking_system.entity.NotificationType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

// Keeps the database's CHECK constraints on enum columns in step with the Java enums.
//
// Why this exists: when Hibernate creates a table, it adds a CHECK constraint listing the enum values
// of that moment. ddl-auto=update never changes an existing constraint, so a new value (such as
// PAYMENT_REMINDER) was rejected by the database and the row was silently lost. At startup this
// rebuilds each constraint from its enum, so adding a value in Java is enough.
// It runs on every start; the statements are cheap and do the same thing each time.
@Component
@Slf4j
@RequiredArgsConstructor
public class EnumCheckConstraintUpdater implements ApplicationRunner {

    private final JdbcTemplate jdbcTemplate;

    @Override
    public void run(ApplicationArguments args) {
        List<EnumColumn> columns = List.of(
                new EnumColumn("notifications", "type", "notifications_type_check", NotificationType.values()),
                new EnumColumn("activity_logs", "type", "activity_logs_type_check", ActivityType.values()));
        // Each column is handled on its own, so a problem with one does not stop the other.
        for (EnumColumn column : columns) {
            rebuildConstraint(column);
        }
    }

    private void rebuildConstraint(EnumColumn column) {
        // Values come from our own enums (letters and underscores only), so building the SQL text is safe.
        String allowed = Arrays.stream(column.values())
                .map(value -> "'" + value.name() + "'")
                .collect(Collectors.joining(", "));
        try {
            jdbcTemplate.execute("ALTER TABLE " + column.table() + " DROP CONSTRAINT IF EXISTS " + column.constraint());
            jdbcTemplate.execute("ALTER TABLE " + column.table() + " ADD CONSTRAINT " + column.constraint()
                    + " CHECK (" + column.column() + " IN (" + allowed + "))");
        } catch (RuntimeException e) {
            // Never stop the app over this: the worst case is the old behavior (new values not saved).
            log.error("Could not update {} on the {} table", column.constraint(), column.table(), e);
        }
    }

    // One enum-typed column: which table and column, the constraint's name, and the allowed enum values.
    private record EnumColumn(String table, String column, String constraint, Enum<?>[] values) {
    }
}
