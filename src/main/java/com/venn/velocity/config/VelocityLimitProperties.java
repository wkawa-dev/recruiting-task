package com.venn.velocity.config;

import java.math.BigDecimal;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Per-customer velocity limits. Defaults match the specification; override via {@code velocity.limits.*}.
 */
@ConfigurationProperties(prefix = "velocity.limits")
public record VelocityLimitProperties(
        @DefaultValue("5000.00") BigDecimal dailyAmount,
        @DefaultValue("20000.00") BigDecimal weeklyAmount,
        @DefaultValue("3") int dailyLoadCount) {

    public VelocityLimitProperties {
        if (dailyAmount.signum() < 0 || weeklyAmount.signum() < 0 || dailyLoadCount < 0) {
            throw new IllegalArgumentException("Velocity limits must not be negative");
        }
    }
}
