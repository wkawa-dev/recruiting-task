package com.venn.velocity.load;

import java.math.BigDecimal;

import com.venn.velocity.config.VelocityLimitProperties;
import com.venn.velocity.domain.CustomerActivity;
import com.venn.velocity.domain.DeclineReason;

import org.springframework.stereotype.Component;

/**
 * Pure decision logic: given what a customer has already loaded, may they load {@code amount} more?
 */
@Component
public class VelocityLimitPolicy {

    private final VelocityLimitProperties limits;

    public VelocityLimitPolicy(VelocityLimitProperties limits) {
        this.limits = limits;
    }

    public LoadDecision evaluate(CustomerActivity activity, BigDecimal amount) {
        if (activity.dailyCount() + 1 > limits.dailyLoadCount()) {
            return LoadDecision.decline(DeclineReason.DAILY_LOAD_COUNT_EXCEEDED);
        }
        if (activity.dailyAmount().add(amount).compareTo(limits.dailyAmount()) > 0) {
            return LoadDecision.decline(DeclineReason.DAILY_AMOUNT_EXCEEDED);
        }
        if (activity.weeklyAmount().add(amount).compareTo(limits.weeklyAmount()) > 0) {
            return LoadDecision.decline(DeclineReason.WEEKLY_AMOUNT_EXCEEDED);
        }
        return LoadDecision.accept();
    }
}
