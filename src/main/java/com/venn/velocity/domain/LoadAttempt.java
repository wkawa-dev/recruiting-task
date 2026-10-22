package com.venn.velocity.domain;

import java.math.BigDecimal;
import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * Every processed (non-duplicate) load attempt, accepted or declined, kept for audit and limit evaluation.
 * Constraints and indexes are defined in the Flyway migrations ({@code db/migration}).
 */
@Entity
@Table(name = "load_attempt")
public class LoadAttempt {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "load_id", nullable = false, length = 255)
    private String loadId;

    @Column(name = "customer_id", nullable = false, length = 255)
    private String customerId;

    @Column(name = "amount", nullable = false, precision = 19, scale = 2)
    private BigDecimal amount;

    @Column(name = "load_time", nullable = false)
    private Instant time;

    @Column(name = "accepted", nullable = false)
    private boolean accepted;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR) // portable varchar + check constraint, not a vendor ENUM type
    @Column(name = "decline_reason", length = 32)
    private DeclineReason declineReason;

    protected LoadAttempt() {
        // for JPA
    }

    public LoadAttempt(String loadId, String customerId, BigDecimal amount, Instant time,
                       boolean accepted, DeclineReason declineReason) {
        this.loadId = loadId;
        this.customerId = customerId;
        this.amount = amount;
        this.time = time;
        this.accepted = accepted;
        this.declineReason = declineReason;
    }

    public Long getId() {
        return id;
    }

    public String getLoadId() {
        return loadId;
    }

    public String getCustomerId() {
        return customerId;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public Instant getTime() {
        return time;
    }

    public boolean isAccepted() {
        return accepted;
    }

    public DeclineReason getDeclineReason() {
        return declineReason;
    }
}
