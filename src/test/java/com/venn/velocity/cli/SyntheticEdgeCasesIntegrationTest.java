package com.venn.velocity.cli;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.StringReader;
import java.io.StringWriter;
import java.util.List;

import com.venn.velocity.domain.LoadAttemptRepository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * Synthetic NDJSON scenarios for the boundary cases the task defines: totals landing exactly on a limit, the
 * midnight UTC and Monday week edges, duplicate load ids, and the amount shapes real payloads carry.
 * <p>
 * Every test feeds the full pipeline (parse, validate, decide, persist) and asserts the exact response lines,
 * the same contract as diffing stdout against {@code output.txt}. The scenarios follow the spec's own examples
 * and stay within what it defines; unrelated customers keep the scenarios readable in one shared database.
 */
@SpringBootTest(properties = "velocity.cli.enabled=false")
class SyntheticEdgeCasesIntegrationTest {

    @Autowired
    private FundLoadProcessor processor;

    @Autowired
    private LoadAttemptRepository repository;

    @BeforeEach
    void cleanDatabase() {
        repository.deleteAll();
    }

    @Test
    void dailyAmountLimitIsMeasuredToTheCent() throws IOException {
        Run run = process(
                load("cent-1", "cent", "$4999.99", "2018-01-01T00:00:00Z"),
                load("cent-2", "cent", "$0.02", "2018-01-01T00:00:01Z"),
                load("cent-3", "cent", "$0.01", "2018-01-01T00:00:02Z"),
                load("cent-4", "cent", "$0.01", "2018-01-01T00:00:03Z"));

        assertThat(run.responses()).containsExactly(
                accepted("cent-1", "cent"),
                declined("cent-2", "cent"),
                accepted("cent-3", "cent"),
                declined("cent-4", "cent"));
    }

    @Test
    void fourthLoadOfDayIsDeclinedRegardlessOfAmount() throws IOException {
        Run run = process(
                load("slots-1", "slots", "$1.00", "2018-01-01T03:00:00Z"),
                load("slots-2", "slots", "$1.00", "2018-01-01T03:00:01Z"),
                load("slots-3", "slots", "$1.00", "2018-01-01T03:00:02Z"),
                load("slots-4", "slots", "$1.00", "2018-01-01T03:00:03Z"),
                load("slots-5", "slots", "$1.00", "2018-01-02T03:00:00Z"));

        assertThat(run.responses()).containsExactly(
                accepted("slots-1", "slots"),
                accepted("slots-2", "slots"),
                accepted("slots-3", "slots"),
                declined("slots-4", "slots"),
                accepted("slots-5", "slots"));
    }

    @Test
    void declinedLoadUsesUpNeitherCountNorAmount() throws IOException {
        Run run = process(
                load("spent-1", "spent", "$4000.00", "2018-01-01T02:00:00Z"),
                load("spent-2", "spent", "$2000.00", "2018-01-01T02:00:01Z"),
                load("spent-3", "spent", "$500.00", "2018-01-01T02:00:02Z"),
                load("spent-4", "spent", "$500.00", "2018-01-01T02:00:03Z"),
                load("spent-5", "spent", "$0.01", "2018-01-01T02:00:04Z"));

        // spent-2 is declined, yet spent-3 and spent-4 still fit both the day's count and its $5,000.
        assertThat(run.responses()).containsExactly(
                accepted("spent-1", "spent"),
                declined("spent-2", "spent"),
                accepted("spent-3", "spent"),
                accepted("spent-4", "spent"),
                declined("spent-5", "spent"));
    }

    @Test
    void dailyLimitsRestartOneSecondAfterMidnightUtc() throws IOException {
        Run run = process(
                load("midnight-1", "midnight", "$5000.00", "2018-01-01T23:59:59Z"),
                load("midnight-2", "midnight", "$5000.00", "2018-01-02T00:00:00Z"),
                load("midnight-3", "midnight", "$0.01", "2018-01-02T00:00:01Z"));

        assertThat(run.responses()).containsExactly(
                accepted("midnight-1", "midnight"),
                accepted("midnight-2", "midnight"),
                declined("midnight-3", "midnight"));
    }

    @Test
    void weeklyLimitAccruesAcrossDaysAndResetsOnMonday() throws IOException {
        Run run = process(
                load("weekly-1", "weekly", "$5000.00", "2018-01-01T09:00:00Z"), // Monday
                load("weekly-2", "weekly", "$5000.00", "2018-01-02T09:00:00Z"), // Tuesday
                load("weekly-3", "weekly", "$5000.00", "2018-01-03T09:00:00Z"), // Wednesday
                load("weekly-4", "weekly", "$5000.00", "2018-01-04T09:00:00Z"), // Thursday: $20,000 exactly
                load("weekly-5", "weekly", "$0.01", "2018-01-05T09:00:00Z"), // Friday: day allows it, week does not
                load("weekly-6", "weekly", "$0.01", "2018-01-07T23:59:59Z"), // Sunday, still the same week
                load("weekly-7", "weekly", "$5000.00", "2018-01-08T00:00:00Z")); // Monday: fresh week

        assertThat(run.responses()).containsExactly(
                accepted("weekly-1", "weekly"),
                accepted("weekly-2", "weekly"),
                accepted("weekly-3", "weekly"),
                accepted("weekly-4", "weekly"),
                declined("weekly-5", "weekly"),
                declined("weekly-6", "weekly"),
                accepted("weekly-7", "weekly"));
    }

    @Test
    void weekSpansTheYearBoundary() throws IOException {
        // Monday 2018-12-31 to Sunday 2019-01-06 is one week, despite crossing the year.
        Run run = process(
                load("newyear-1", "newyear", "$5000.00", "2018-12-31T23:59:59Z"),
                load("newyear-2", "newyear", "$5000.00", "2019-01-01T00:00:00Z"),
                load("newyear-3", "newyear", "$5000.00", "2019-01-03T12:00:00Z"),
                load("newyear-4", "newyear", "$5000.00", "2019-01-04T12:00:00Z"), // $20,000 exactly
                load("newyear-5", "newyear", "$0.01", "2019-01-06T23:59:59Z"), // Sunday, same cross-year week
                load("newyear-6", "newyear", "$5000.00", "2019-01-07T00:00:00Z")); // fresh week

        assertThat(run.responses()).containsExactly(
                accepted("newyear-1", "newyear"),
                accepted("newyear-2", "newyear"),
                accepted("newyear-3", "newyear"),
                accepted("newyear-4", "newyear"),
                declined("newyear-5", "newyear"),
                accepted("newyear-6", "newyear"));
    }

    @Test
    void duplicateLoadIdsAreIgnoredEvenWhenTheFirstAttemptWasDeclined() throws IOException {
        Run run = process(
                load("dup-1", "dup", "$100.00", "2018-01-01T03:00:00Z"),
                load("dup-1", "dup", "$9999.99", "2018-01-01T03:00:01Z"),
                load("dup-1", "dup", "$1.00", "2018-01-01T03:00:02Z"),
                load("dup-2", "dup", "$6000.00", "2018-01-01T03:00:03Z"),
                load("dup-2", "dup", "$1.00", "2018-01-01T03:00:04Z"),
                load("dup-1", "dup-other", "$100.00", "2018-01-01T03:00:05Z"),
                load("dup-3", "dup", "$4900.00", "2018-01-01T03:00:06Z"));

        // The repeated ids produce no response, and their amounts are invisible to the limits: dup-3 still
        // lands dup at exactly $5,000 for the day. Under another customer the same id is a normal load.
        assertThat(run.responses()).containsExactly(
                accepted("dup-1", "dup"),
                declined("dup-2", "dup"),
                accepted("dup-1", "dup-other"),
                accepted("dup-3", "dup"));
        assertThat(run.summary()).isEqualTo(new ProcessingSummary(7, 3, 1, 3, 0));
    }

    @Test
    void interleavedCustomersAreEvaluatedIndependently() throws IOException {
        Run run = process(
                load("alice-1", "alice", "$5000.00", "2018-01-01T05:00:00Z"),
                load("bob-1", "bob", "$5000.00", "2018-01-01T05:00:01Z"),
                load("alice-2", "alice", "$0.01", "2018-01-01T05:00:02Z"),
                load("bob-2", "bob", "$0.01", "2018-01-01T05:00:03Z"),
                load("alice-3", "alice", "$1.00", "2018-01-02T05:00:00Z"),
                load("bob-3", "bob", "$1.00", "2018-01-02T05:00:01Z"));

        assertThat(run.responses()).containsExactly(
                accepted("alice-1", "alice"),
                accepted("bob-1", "bob"),
                declined("alice-2", "alice"),
                declined("bob-2", "bob"),
                accepted("alice-3", "alice"),
                accepted("bob-3", "bob"));
    }

    @Test
    void acceptsTheAmountShapesPayloadsCarry() throws IOException {
        Run run = process(
                load("amt-1", "amt-a", "$5000", "2018-01-01T06:00:00Z"),
                load("amt-2", "amt-b", "$1", "2018-01-01T06:00:01Z"),
                load("amt-3", "amt-b", "$1.5", "2018-01-01T06:00:02Z"),
                load("amt-4", "amt-b", "$0001.25", "2018-01-01T06:00:03Z"),
                load("amt-5", "amt-c", "$0.01", "2018-01-01T06:00:04Z"));

        // "$5000" is the whole daily allowance with no cents; "$0.01" is the smallest load that moves money.
        assertThat(run.responses()).containsExactly(
                accepted("amt-1", "amt-a"),
                accepted("amt-2", "amt-b"),
                accepted("amt-3", "amt-b"),
                accepted("amt-4", "amt-b"),
                accepted("amt-5", "amt-c"));
    }

    private Run process(String... lines) throws IOException {
        StringWriter output = new StringWriter();
        ProcessingSummary summary = processor.process(
                new BufferedReader(new StringReader(String.join("\n", lines))), output);
        return new Run(output.toString().lines().toList(), summary);
    }

    private record Run(List<String> responses, ProcessingSummary summary) {
    }

    private static String load(String id, String customerId, String amount, String time) {
        return "{\"id\":\"%s\",\"customer_id\":\"%s\",\"load_amount\":\"%s\",\"time\":\"%s\"}"
                .formatted(id, customerId, amount, time);
    }

    private static String accepted(String id, String customerId) {
        return response(id, customerId, true);
    }

    private static String declined(String id, String customerId) {
        return response(id, customerId, false);
    }

    private static String response(String id, String customerId, boolean accepted) {
        return "{\"id\":\"" + id + "\",\"customer_id\":\"" + customerId + "\",\"accepted\":" + accepted + "}";
    }
}
