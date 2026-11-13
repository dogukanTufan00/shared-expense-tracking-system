package com.dogukan_tufan.shared_expense_tracking_system.config;

import com.dogukan_tufan.shared_expense_tracking_system.entity.NotificationType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.stream.Collectors;

// Keeps the database's list of allowed notification types in step with the NotificationType enum.
//
// Why this exists: when Hibernate creates the notifications table, it adds a CHECK constraint
// listing the enum values of that moment. ddl-auto=update never changes an existing constraint, so a
// new type (PAYMENT_REMINDER) was rejected by the database and the notification was silently lost.
// At startup this rebuilds the constraint from the enum, so adding a type in Java is enough.
// It runs on every start; the statements are cheap and do the same thing each time.
@Component
@Slf4j
@RequiredArgsConstructor
public class NotificationTypeConstraintUpdater implements ApplicationRunner {

    private static final String CONSTRAINT = "notifications_type_check";

    private final JdbcTemplate jdbcTemplate;

    @Override
    public void run(ApplicationArguments args) {
        // Values come from our own enum (letters and underscores only), so building the SQL text is safe.
        String allowed = Arrays.stream(NotificationType.values())
                .map(type -> "'" + type.name() + "'")
                .collect(Collectors.joining(", "));
        try {
            jdbcTemplate.execute("ALTER TABLE notifications DROP CONSTRAINT IF EXISTS " + CONSTRAINT);
            jdbcTemplate.execute("ALTER TABLE notifications ADD CONSTRAINT " + CONSTRAINT
                    + " CHECK (type IN (" + allowed + "))");
        } catch (RuntimeException e) {
            // Never stop the app over this: the worst case is the old behavior (new types not saved).
            log.error("Could not update {} on the notifications table", CONSTRAINT, e);
        }
    }
}
