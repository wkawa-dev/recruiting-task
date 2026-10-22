package com.venn.velocity.load;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.temporal.TemporalAdjusters;

/**
 * Half-open UTC interval {@code [from, to)} used for limit aggregation.
 */
public record TimeWindow(Instant from, Instant to) {

    /** The UTC calendar day containing {@code time}. */
    public static TimeWindow dayOf(Instant time) {
        LocalDate day = LocalDate.ofInstant(time, ZoneOffset.UTC);
        return new TimeWindow(startOf(day), startOf(day.plusDays(1)));
    }

    /** The Monday-to-Sunday UTC week containing {@code time}. */
    public static TimeWindow weekOf(Instant time) {
        LocalDate monday = LocalDate.ofInstant(time, ZoneOffset.UTC)
                .with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        return new TimeWindow(startOf(monday), startOf(monday.plusWeeks(1)));
    }

    private static Instant startOf(LocalDate date) {
        return date.atStartOfDay(ZoneOffset.UTC).toInstant();
    }
}
