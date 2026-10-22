package com.venn.velocity.load;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Instant;

import com.venn.velocity.domain.CustomerRepository;
import com.venn.velocity.domain.DeclineReason;
import com.venn.velocity.domain.LoadAttempt;
import com.venn.velocity.domain.LoadAttemptRepository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(properties = "velocity.cli.enabled=false")
class FundLoadServiceIntegrationTest {

    @Autowired
    private FundLoadService service;

    @Autowired
    private LoadAttemptRepository repository;

    @Autowired
    private CustomerRepository customers;

    @BeforeEach
    void cleanDatabase() {
        repository.deleteAll();
        customers.deleteAll();
    }

    @Test
    void declinesSecondLoadOf3000InOneDay() {
        assertThat(accepted("1", "c1", "3000.00", "2000-01-03T08:00:00Z")).isTrue();
        assertThat(accepted("2", "c1", "3000.00", "2000-01-03T09:00:00Z")).isFalse();
    }

    @Test
    void declinesFourthLoadOf400InOneDay() {
        assertThat(accepted("1", "c1", "400.00", "2000-01-03T08:00:00Z")).isTrue();
        assertThat(accepted("2", "c1", "400.00", "2000-01-03T09:00:00Z")).isTrue();
        assertThat(accepted("3", "c1", "400.00", "2000-01-03T10:00:00Z")).isTrue();
        assertThat(accepted("4", "c1", "400.00", "2000-01-03T11:00:00Z")).isFalse();
    }

    @Test
    void declinedLoadsDoNotCountTowardsLimits() {
        assertThat(accepted("1", "c1", "4000.00", "2000-01-03T08:00:00Z")).isTrue();
        assertThat(accepted("2", "c1", "2000.00", "2000-01-03T09:00:00Z")).isFalse();
        assertThat(accepted("3", "c1", "1000.00", "2000-01-03T10:00:00Z")).isTrue();
    }

    @Test
    void dailyLimitsResetAtMidnightUtc() {
        assertThat(accepted("1", "c1", "5000.00", "2000-01-03T23:59:59Z")).isTrue();
        assertThat(accepted("2", "c1", "5000.00", "2000-01-04T00:00:00Z")).isTrue();
    }

    @Test
    void weeklyLimitResetsOnMonday() {
        // Mon 2000-01-03 .. Thu 2000-01-06: 4 x 5000 = 20000
        for (int day = 3; day <= 6; day++) {
            assertThat(accepted("w" + day, "c1", "5000.00", "2000-01-0" + day + "T10:00:00Z")).isTrue();
        }
        assertThat(accepted("fri", "c1", "0.01", "2000-01-07T10:00:00Z")).isFalse();
        assertThat(accepted("sun", "c1", "1.00", "2000-01-09T23:59:59Z")).isFalse();
        assertThat(accepted("mon", "c1", "5000.00", "2000-01-10T00:00:00Z")).isTrue();
    }

    @Test
    void limitsAreTrackedPerCustomer() {
        assertThat(accepted("1", "c1", "5000.00", "2000-01-03T08:00:00Z")).isTrue();
        assertThat(accepted("2", "c2", "5000.00", "2000-01-03T08:00:00Z")).isTrue();
    }

    @Test
    void ignoresDuplicateLoadIdForSameCustomer() {
        assertThat(service.process(load("1", "c1", "100.00", "2000-01-03T08:00:00Z"))).isPresent();
        assertThat(service.process(load("1", "c1", "100.00", "2000-01-03T09:00:00Z"))).isEmpty();
        assertThat(repository.count()).isEqualTo(1);
    }

    @Test
    void ignoresDuplicateEvenWhenFirstAttemptWasDeclined() {
        assertThat(accepted("1", "c1", "6000.00", "2000-01-03T08:00:00Z")).isFalse();
        assertThat(service.process(load("1", "c1", "100.00", "2000-01-03T09:00:00Z"))).isEmpty();
    }

    @Test
    void sameLoadIdForDifferentCustomersIsNotADuplicate() {
        assertThat(service.process(load("1", "c1", "100.00", "2000-01-03T08:00:00Z"))).isPresent();
        assertThat(service.process(load("1", "c2", "100.00", "2000-01-03T08:00:00Z"))).isPresent();
    }

    @Test
    void persistsDeclineReasonForAudit() {
        accepted("1", "c1", "6000.00", "2000-01-03T08:00:00Z");

        LoadAttempt attempt = repository.findAll().get(0);
        assertThat(attempt.isAccepted()).isFalse();
        assertThat(attempt.getDeclineReason()).isEqualTo(DeclineReason.DAILY_AMOUNT_EXCEEDED);
    }

    private boolean accepted(String id, String customer, String amount, String time) {
        return service.process(load(id, customer, amount, time)).orElseThrow().accepted();
    }

    private static FundLoad load(String id, String customer, String amount, String time) {
        return new FundLoad(id, customer, new BigDecimal(amount), Instant.parse(time));
    }
}
