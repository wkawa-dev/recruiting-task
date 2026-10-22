package com.venn.velocity.cli;

/**
 * Counts for one processing run.
 *
 * @param lines      input lines read, including blank ones
 * @param accepted   loads accepted
 * @param declined   loads declined
 * @param duplicates loads ignored because the load id was already seen for the customer
 * @param invalid    records skipped because they were malformed, failed validation or were rejected by the database
 */
public record ProcessingSummary(long lines, long accepted, long declined, long duplicates, long invalid) {
}
