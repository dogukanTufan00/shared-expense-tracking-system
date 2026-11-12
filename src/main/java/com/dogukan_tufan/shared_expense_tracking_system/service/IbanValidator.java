package com.dogukan_tufan.shared_expense_tracking_system.service;

import com.dogukan_tufan.shared_expense_tracking_system.exception.BadRequestException;
import org.springframework.stereotype.Component;

import java.math.BigInteger;
import java.util.Locale;

// Checks Turkish IBANs (TR + 24 digits = 26 characters). Used on register and profile update so
// that junk never reaches the database and housemates can copy a number they can actually pay to.
//
// The check digits use ISO 13616 "mod-97": move the first 4 characters to the end, turn letters
// into numbers (A=10 ... Z=35), and the resulting big number must give remainder 1 when divided
// by 97. A single mistyped or swapped digit almost always breaks this, so typos are caught.
@Component
public class IbanValidator {

    private static final String INVALID_MESSAGE =
            "Invalid IBAN: must be a Turkish IBAN (TR + 24 digits) with a valid check digit";

    // Returns the cleaned IBAN (no spaces, uppercase), or null when the input is blank.
    // A blank value means "the user removed their IBAN", so we store null instead of an empty string.
    public String normalize(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }

        String iban = raw.replaceAll("\\s", "").toUpperCase(Locale.ROOT);
        if (!iban.matches("TR\\d{24}") || !passesMod97(iban)) {
            throw new BadRequestException(INVALID_MESSAGE);
        }
        return iban;
    }

    private boolean passesMod97(String iban) {
        // Move "TR25" (the first four characters) to the end: "TR25..." -> "...TR25"
        String rearranged = iban.substring(4) + iban.substring(0, 4);

        // Expand letters to numbers: A=10, B=11, ..., Z=35. Digits stay as they are.
        StringBuilder numeric = new StringBuilder();
        for (char c : rearranged.toCharArray()) {
            if (Character.isDigit(c)) {
                numeric.append(c);
            } else {
                numeric.append(c - 'A' + 10);
            }
        }

        // The number is around 30 digits long, too big for a long, so BigInteger does the modulo.
        return new BigInteger(numeric.toString()).mod(BigInteger.valueOf(97)).intValue() == 1;
    }
}
