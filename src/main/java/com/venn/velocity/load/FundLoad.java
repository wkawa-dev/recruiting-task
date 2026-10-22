package com.venn.velocity.load;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * A validated load attempt. The bounds here mirror the {@code load_attempt} columns; anything that still slips
 * past is rejected by the database and skipped by the processor.
 */
public record FundLoad(String id, String customerId, BigDecimal amount, Instant time) {

    private static final int ID_MAX_LENGTH = 255;

    /** {@code $} followed by up to 17 significant integer digits and up to 2 decimals; leading zeros ignored. */
    private static final Pattern AMOUNT = Pattern.compile("^\\$0*(\\d{1,17}(?:\\.\\d{1,2})?)$");

    /** Years 0001-9999: what SQL timestamps support. */
    static final Instant MIN_TIME = LocalDate.of(1, 1, 1).atStartOfDay(ZoneOffset.UTC).toInstant();
    static final Instant MAX_TIME_EXCLUSIVE = LocalDate.of(10_000, 1, 1).atStartOfDay(ZoneOffset.UTC).toInstant();

    public static FundLoad from(LoadRequest request) {
        if (request == null) {
            throw new InvalidLoadRequestException("Load request is empty");
        }
        return new FundLoad(
                requireId(request.id(), "id"),
                requireId(request.customerId(), "customer_id"),
                parseAmount(requireText(request.loadAmount(), "load_amount")),
                parseTime(requireText(request.time(), "time")));
    }

    static BigDecimal parseAmount(String raw) {
        Matcher matcher = AMOUNT.matcher(raw.trim());
        if (!matcher.matches()) {
            throw new InvalidLoadRequestException("Invalid load_amount, expected $<dollars>[.<cents>] with at most "
                    + "17 integer digits");
        }
        BigDecimal amount = new BigDecimal(matcher.group(1)).setScale(2);
        if (amount.signum() == 0) {
            // A zero load moves no money but would still use up one of the customer's daily loads.
            throw new InvalidLoadRequestException("Invalid load_amount, must be greater than zero");
        }
        return amount;
    }

    static Instant parseTime(String raw) {
        Instant time;
        try {
            time = Instant.parse(raw.trim());
        } catch (DateTimeParseException e) {
            throw new InvalidLoadRequestException("Invalid time, expected ISO-8601 UTC instant", e);
        }
        if (time.isBefore(MIN_TIME) || !time.isBefore(MAX_TIME_EXCLUSIVE)) {
            throw new InvalidLoadRequestException("Invalid time, year must be between 0001 and 9999");
        }
        // The database stores microseconds; truncate here so the stored time and the evaluated window agree.
        return time.truncatedTo(ChronoUnit.MICROS);
    }

    private static String requireId(String value, String field) {
        String id = requireText(value, field);
        if (id.length() > ID_MAX_LENGTH) {
            throw new InvalidLoadRequestException(
                    "Field '" + field + "' exceeds " + ID_MAX_LENGTH + " characters (" + id.length() + ")");
        }
        return id;
    }

    private static String requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new InvalidLoadRequestException("Missing required field '" + field + "'");
        }
        return value.trim();
    }
}
