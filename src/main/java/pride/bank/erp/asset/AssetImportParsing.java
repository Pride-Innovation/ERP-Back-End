/*
13.9 Pride's Standard Copyright Notice:
Copyright ©20XX. Management of Pride Bank Limited (PBL). All Rights Reserved. Permission to use, copy, modify,
and distribute this software and its documentation for any purpose is prohibited unless authorized in writing by the
Managing Director
*/

package pride.bank.erp.asset;

import pride.bank.erp.exception.BadRequestException;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.List;

/**
 * Turns spreadsheet cells into values, tolerantly.
 *
 * <p>A migration file is typed by people, exported from an older system, and edited in Excel — which
 * silently reformats things on its own. Every one of these accepts the several shapes the same value
 * genuinely arrives in, and fails with a message naming the offending text when it truly cannot tell.
 *
 * <p>The failures matter as much as the successes: each one throws {@link BadRequestException},
 * which the row loop catches and reports against that row's number. Nothing here may throw anything
 * that would escape the loop and take the rest of the file with it.
 */
final class AssetImportParsing {

    /**
     * Excel's day zero.
     *
     * <p>1899-12-30, not 1899-12-31, because Excel deliberately reproduces a Lotus 1-2-3 bug that
     * treats 1900 as a leap year. Counting from the 30th absorbs the phantom day.
     */
    private static final LocalDate EXCEL_EPOCH = LocalDate.of(1899, 12, 30);

    /** How the same date arrives depending on locale, source system and who typed it. */
    private static final List<DateTimeFormatter> DATE_FORMATS = List.of(
            DateTimeFormatter.ofPattern("yyyy-MM-dd"),
            DateTimeFormatter.ofPattern("dd/MM/yyyy"),
            DateTimeFormatter.ofPattern("d/M/yyyy"),
            DateTimeFormatter.ofPattern("dd-MM-yyyy"),
            DateTimeFormatter.ofPattern("d-M-yyyy"),
            DateTimeFormatter.ofPattern("dd/MM/yy"),
            DateTimeFormatter.ofPattern("dd-MMM-yyyy"),
            DateTimeFormatter.ofPattern("d MMM yyyy"),
            DateTimeFormatter.ofPattern("MMM d, yyyy")
    );

    private static final DateTimeFormatter ISO_DATE = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    private AssetImportParsing() {
    }

    static boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    static String trimToNull(String value) {
        return isBlank(value) ? null : value.trim();
    }

    /**
     * A date received, as the {@code yyyy-MM-dd} string {@code Asset.dateReceipt} stores.
     *
     * <p>Accepts a plain Excel serial number as well as text, because a properly formatted date cell
     * reaches the browser as a number and there is no way to tell it apart from someone typing one.
     */
    static String parseDate(String raw, String columnLabel) {
        String value = trimToNull(raw);
        if (value == null) {
            return null;
        }

        // An Excel serial. Bounded because a bare "2019" is far more likely to be a year someone
        // typed than day 2019 (1905) — better to reject it and say so than to file it silently.
        if (value.matches("^\\d+(\\.\\d+)?$")) {
            long serial = (long) Double.parseDouble(value);
            if (serial < 20000 || serial > 80000) {
                throw new BadRequestException(columnLabel + " '" + value
                        + "' is not a date. Use dd/mm/yyyy, or format the cell as a date.");
            }
            return EXCEL_EPOCH.plusDays(serial).format(ISO_DATE);
        }

        // Excel and some exports append a midnight time; the date is all that is stored.
        String dateOnly = value.contains("T") ? value.substring(0, value.indexOf('T')) : value;
        for (DateTimeFormatter format : DATE_FORMATS) {
            try {
                return LocalDate.parse(dateOnly, format).format(ISO_DATE);
            } catch (DateTimeParseException ignored) {
                // Try the next shape.
            }
        }

        throw new BadRequestException(columnLabel + " '" + value
                + "' is not a date we recognise. Use dd/mm/yyyy.");
    }

    /**
     * Money, with the decoration people put in spreadsheets removed.
     *
     * <p>Currency prefixes, thousands separators, stray spaces and parenthesised negatives all
     * appear in real files. A blank cell is null, not zero — "we do not know what this cost" and
     * "this cost nothing" are different facts and only one of them is usually true.
     */
    static BigDecimal parseMoney(String raw, String columnLabel) {
        String value = trimToNull(raw);
        if (value == null) {
            return null;
        }

        boolean negative = value.startsWith("(") && value.endsWith(")");
        String cleaned = value.replaceAll("(?i)(ugx|ushs|shs|usd)", "")
                .replaceAll("[,\\s()]", "")
                .trim();
        if (cleaned.isEmpty()) {
            return null;
        }

        try {
            BigDecimal amount = new BigDecimal(cleaned);
            return negative ? amount.negate() : amount;
        } catch (NumberFormatException e) {
            throw new BadRequestException(columnLabel + " '" + value + "' is not a number.");
        }
    }

    /**
     * A MAC address, normalised to colon-separated upper case.
     *
     * <p>Stored as free text on the asset, so normalising here is what makes two records of the same
     * machine comparable at all — {@code 00-1B-44-11-3A-B7} and {@code 001b44113ab7} are the same
     * address and would otherwise never match.
     */
    static String parseMacAddress(String raw) {
        String value = trimToNull(raw);
        if (value == null) {
            return null;
        }
        String hex = value.replaceAll("[^0-9A-Fa-f]", "").toUpperCase();
        if (hex.length() != 12) {
            throw new BadRequestException("MAC Address '" + value + "' is not a valid MAC address.");
        }
        StringBuilder formatted = new StringBuilder(hex.substring(0, 2));
        for (int i = 2; i < 12; i += 2) {
            formatted.append(':').append(hex, i, i + 2);
        }
        return formatted.toString();
    }

    /** Basic IPv4 shape check — enough to catch a serial number typed into the wrong column. */
    static String parseIpAddress(String raw) {
        String value = trimToNull(raw);
        if (value == null) {
            return null;
        }
        if (!value.matches("^((25[0-5]|2[0-4]\\d|1\\d\\d|[1-9]?\\d)\\.){3}(25[0-5]|2[0-4]\\d|1\\d\\d|[1-9]?\\d)$")) {
            throw new BadRequestException("IP Address '" + value + "' is not a valid IPv4 address.");
        }
        return value;
    }
}
