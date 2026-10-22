package com.venn.velocity.load;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;

import com.venn.velocity.config.VelocityLimitProperties;
import com.venn.velocity.domain.CustomerActivity;
import com.venn.velocity.domain.DeclineReason;

import org.junit.jupiter.api.Test;

class VelocityLimitPolicyTest {

    private final VelocityLimitPolicy policy = new VelocityLimitPolicy(
            new VelocityLimitProperties(new BigDecimal("5000.00"), new BigDecimal("20000.00"), 3));

    @Test
    void acceptsWithinAllLimits() {
        assertThat(policy.evaluate(activity("0", 0, "0"), amount("100.00")).accepted()).isTrue();
    }

    @Test
    void acceptsExactlyAtLimits() {
        assertThat(policy.evaluate(activity("4000.00", 2, "19000.00"), amount("1000.00")).accepted()).isTrue();
    }

    @Test
    void declinesFourthLoadOfTheDay() {
        assertThat(policy.evaluate(activity("1200.00", 3, "1200.00"), amount("400.00")))
                .isEqualTo(LoadDecision.decline(DeclineReason.DAILY_LOAD_COUNT_EXCEEDED));
    }

    @Test
    void declinesWhenDailyAmountWouldBeExceeded() {
        assertThat(policy.evaluate(activity("3000.00", 1, "3000.00"), amount("3000.00")))
                .isEqualTo(LoadDecision.decline(DeclineReason.DAILY_AMOUNT_EXCEEDED));
    }

    @Test
    void declinesSingleLoadAboveDailyLimit() {
        assertThat(policy.evaluate(activity("0", 0, "0"), amount("5000.01")))
                .isEqualTo(LoadDecision.decline(DeclineReason.DAILY_AMOUNT_EXCEEDED));
    }

    @Test
    void declinesWhenWeeklyAmountWouldBeExceeded() {
        assertThat(policy.evaluate(activity("0", 0, "19000.00"), amount("1000.01")))
                .isEqualTo(LoadDecision.decline(DeclineReason.WEEKLY_AMOUNT_EXCEEDED));
    }

    private static CustomerActivity activity(String daily, long count, String weekly) {
        return new CustomerActivity(new BigDecimal(daily), count, new BigDecimal(weekly));
    }

    private static BigDecimal amount(String value) {
        return new BigDecimal(value);
    }
}
