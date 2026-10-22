package com.venn.velocity.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Instant;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(properties = "velocity.cli.enabled=false")
class LoadAttemptRepositoryTest {

    // Week of Mon 2000-01-03; the day under test is Wed 2000-01-05.
    private static final Instant WEEK_FROM = Instant.parse("2000-01-03T00:00:00Z");
    private static final Instant WEEK_TO = Instant.parse("2000-01-10T00:00:00Z");
    private static final Instant DAY_FROM = Instant.parse("2000-01-05T00:00:00Z");
    private static final Instant DAY_TO = Instant.parse("2000-01-06T00:00:00Z");

    @Autowired
    private LoadAttemptRepository loadAttempts;

    @Autowired
    private CustomerRepository customers;

    private int sequence;

    @BeforeEach
    void setUp() {
        loadAttempts.deleteAll();
        customers.deleteAll();
        customers.save(new Customer("c1"));
        customers.save(new Customer("c2"));
    }

    @Test
    void returnsZerosWhenThereIsNoActivity() {
        assertThat(activity("c1")).isEqualTo(new CustomerActivity(new BigDecimal("0"), 0, new BigDecimal("0")));
    }

    @Test
    void aggregatesAcceptedLoadsOfThisCustomerIntoDayAndWeekTotals() {
        save("c1", "100.00", "2000-01-05T00:00:00Z", true);   // day start, inclusive
        save("c1", "200.00", "2000-01-05T23:59:59Z", true);   // in day
        save("c1", "400.00", "2000-01-04T12:00:00Z", true);   // earlier in week
        save("c1", "800.00", "2000-01-09T23:59:59Z", true);   // Sunday, end of week
        save("c1", "1600.00", "2000-01-05T12:00:00Z", false); // declined: ignored
        save("c1", "3200.00", "2000-01-02T23:59:59Z", true);  // previous week: ignored
        save("c1", "6400.00", "2000-01-10T00:00:00Z", true);  // next week, exclusive: ignored
        save("c2", "9999.00", "2000-01-05T12:00:00Z", true);  // other customer: ignored

        CustomerActivity activity = activity("c1");

        assertThat(activity.dailyAmount()).isEqualByComparingTo("300.00");
        assertThat(activity.dailyCount()).isEqualTo(2);
        assertThat(activity.weeklyAmount()).isEqualByComparingTo("1500.00");
    }

    private CustomerActivity activity(String customerId) {
        return loadAttempts.acceptedActivity(customerId, DAY_FROM, DAY_TO, WEEK_FROM, WEEK_TO);
    }

    private void save(String customerId, String amount, String time, boolean accepted) {
        loadAttempts.save(new LoadAttempt("load-" + sequence++, customerId, new BigDecimal(amount),
                Instant.parse(time), accepted, accepted ? null : DeclineReason.DAILY_AMOUNT_EXCEEDED));
    }
}
