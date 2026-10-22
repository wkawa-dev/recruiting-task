package com.venn.velocity.load;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;

import org.junit.jupiter.api.Test;

class TimeWindowTest {

    @Test
    void dayEndsAtMidnightUtc() {
        TimeWindow window = TimeWindow.dayOf(Instant.parse("2000-01-01T23:59:59Z"));

        assertThat(window.from()).isEqualTo(Instant.parse("2000-01-01T00:00:00Z"));
        assertThat(window.to()).isEqualTo(Instant.parse("2000-01-02T00:00:00Z"));
    }

    @Test
    void weekStartsOnMonday() {
        // 2000-01-05 was a Wednesday
        TimeWindow window = TimeWindow.weekOf(Instant.parse("2000-01-05T12:00:00Z"));

        assertThat(window.from()).isEqualTo(Instant.parse("2000-01-03T00:00:00Z"));
        assertThat(window.to()).isEqualTo(Instant.parse("2000-01-10T00:00:00Z"));
    }

    @Test
    void sundayBelongsToPrecedingWeekAndMondayStartsANewOne() {
        TimeWindow sunday = TimeWindow.weekOf(Instant.parse("2000-01-09T23:59:59Z"));
        TimeWindow monday = TimeWindow.weekOf(Instant.parse("2000-01-10T00:00:00Z"));

        assertThat(sunday.from()).isEqualTo(Instant.parse("2000-01-03T00:00:00Z"));
        assertThat(monday.from()).isEqualTo(Instant.parse("2000-01-10T00:00:00Z"));
    }
}
