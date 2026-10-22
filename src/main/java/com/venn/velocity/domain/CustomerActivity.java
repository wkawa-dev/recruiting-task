package com.venn.velocity.domain;

import java.math.BigDecimal;

/**
 * A customer's previously accepted loads within the day and week of a new attempt.
 */
public record CustomerActivity(BigDecimal dailyAmount, long dailyCount, BigDecimal weeklyAmount) {
}
