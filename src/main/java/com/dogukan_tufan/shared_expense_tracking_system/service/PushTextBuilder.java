package com.dogukan_tufan.shared_expense_tracking_system.service;

import com.dogukan_tufan.shared_expense_tracking_system.entity.Notification;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.Locale;

// Builds the title and body of a push message, in the language of the device (tr or en).
//
// Why the server builds the text: the phone only receives the message; a push shown by the OS
// cannot run app code to translate it. The wording matches the in-app texts of the Flutter app.
// Why Unicode escapes for ₺, Ö, → and ·: the source stays plain ASCII, so it compiles the same
// on any Windows code page.
@Component
public class PushTextBuilder {

    private static final String LIRA = "₺";
    private static final String ARROW = "→";
    private static final String DOT = "·";

    public record PushText(String title, String body) {
    }

    public PushText build(Notification notification, String language) {
        boolean tr = "tr".equals(language);
        String household = notification.getHouseholdName();
        String actor = notification.getActorName();
        String target = notification.getTargetName();
        String detail = notification.getDetail();
        String amount = formatMoney(notification.getAmount(), tr);
        boolean hasDetail = detail != null && !detail.isBlank();
        // Only for edits where the amount changed: ": ₺750,00 → ₺1.050,00". Empty otherwise.
        String amountChange = notification.getPreviousAmount() == null
                ? ""
                : ": " + formatMoney(notification.getPreviousAmount(), tr) + " " + ARROW + " " + amount;

        return switch (notification.getType()) {
            case EXPENSE_ADDED -> tr
                    ? new PushText("Yeni gider " + DOT + " " + household,
                            hasDetail ? actor + " " + amount + " " + detail + " ekledi"
                                      : actor + " " + amount + " gider ekledi")
                    : new PushText("New expense " + DOT + " " + household,
                            hasDetail ? actor + " added " + detail + ": " + amount
                                      : actor + " added an expense: " + amount);

            case PAYMENT_RECORDED -> tr
                    ? new PushText("Ödeme " + DOT + " " + household,
                            actor + " " + ARROW + " " + target + ": " + amount + " ödeme kaydedildi")
                    : new PushText("Payment " + DOT + " " + household,
                            actor + " " + ARROW + " " + target + ": " + amount + " payment recorded");

            case INVITATION_RECEIVED -> tr
                    ? new PushText("Yeni davet",
                            actor + " seni bir eve davet etti: " + household)
                    : new PushText("New invitation",
                            actor + " invited you to a household: " + household);

            case INVITATION_ACCEPTED -> tr
                    ? new PushText("Yeni ev arkadaşı " + DOT + " " + household,
                            actor + " davetini kabul etti")
                    : new PushText("New housemate " + DOT + " " + household,
                            actor + " accepted the invitation");

            case RECURRING_EXPENSE_ADDED -> tr
                    ? new PushText("Tekrarlayan gider " + DOT + " " + household,
                            hasDetail ? detail + " " + amount + " bu ay da eklendi"
                                      : amount + " bu ay da eklendi")
                    : new PushText("Recurring expense " + DOT + " " + household,
                            hasDetail ? detail + " " + amount + " was added this month too"
                                      : amount + " was added this month too");

            // Polite on purpose: a reminder is a nudge, not an accusation. The note (if any) is shown in
            // quotes on a new line, so it reads as the creditor's own words.
            case PAYMENT_REMINDER -> tr
                    ? new PushText("Ödeme hatırlatması",
                            actor + " nazikçe hatırlatıyor: " + household + " evinde ona "
                                    + amount + " borcun var." + (hasDetail ? quoted(detail) : ""))
                    : new PushText("Payment reminder",
                            actor + " is gently reminding you that you owe them " + amount
                                    + " in " + household + "." + (hasDetail ? quoted(detail) : ""));

            case EXPENSE_UPDATED -> tr
                    ? new PushText("Gider düzenlendi " + DOT + " " + household,
                            hasDetail ? actor + " \"" + detail + "\" giderini düzenledi" + amountChange
                                      : actor + " bir gideri düzenledi" + amountChange)
                    : new PushText("Expense edited " + DOT + " " + household,
                            hasDetail ? actor + " edited \"" + detail + "\"" + amountChange
                                      : actor + " edited an expense" + amountChange);

            case PAYMENT_UPDATED -> tr
                    ? new PushText("Ödeme düzenlendi " + DOT + " " + household,
                            actor + " bir ödemeyi düzenledi" + amountChange)
                    : new PushText("Payment edited " + DOT + " " + household,
                            actor + " edited a payment" + amountChange);

            // detail holds the chore name here. The text quotes it, like an expense description.
            case CHORE_TURN -> tr
                    ? new PushText("Sıra sende " + DOT + " " + household,
                            actor + " \"" + detail + "\" işini yaptı, sıra sende.")
                    : new PushText("Your turn " + DOT + " " + household,
                            actor + " did \"" + detail + "\", it's your turn now.");

            case CHORE_REMINDER -> tr
                    ? new PushText("Hatırlatma " + DOT + " " + household,
                            actor + " nazikçe hatırlatıyor: \"" + detail + "\" sırası sende.")
                    : new PushText("Reminder " + DOT + " " + household,
                            actor + " is gently reminding you: it's your turn for \"" + detail + "\".");
        };
    }

    // Puts the note on its own line inside curly quotes (U+201C and U+201D).
    private static String quoted(String detail) {
        return "\n“" + detail + "”";
    }

    // 1234.5 -> "₺1.234,50" in Turkish, "₺1,234.50" in English.
    // The symbols are set by hand instead of taken from the JVM locale, so the result does not depend on the server's settings.
    private static String formatMoney(BigDecimal amount, boolean tr) {
        if (amount == null) {
            return "";
        }
        DecimalFormatSymbols symbols = new DecimalFormatSymbols(Locale.ROOT);
        symbols.setGroupingSeparator(tr ? '.' : ',');
        symbols.setDecimalSeparator(tr ? ',' : '.');
        return LIRA + new DecimalFormat("#,##0.00", symbols).format(amount);
    }
}
