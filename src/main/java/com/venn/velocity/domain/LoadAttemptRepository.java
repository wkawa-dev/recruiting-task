package com.venn.velocity.domain;

import java.time.Instant;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface LoadAttemptRepository extends JpaRepository<LoadAttempt, Long> {

    boolean existsByCustomerIdAndLoadId(String customerId, String loadId);

    /**
     * Accepted-load totals for the week {@code [weekFrom, weekTo)} and the day {@code [dayFrom, dayTo)} within it,
     * in one index range scan over {@code (customer_id, load_time)}.
     */
    @Query("""
            select new com.venn.velocity.domain.CustomerActivity(
                coalesce(sum(case when a.time >= :dayFrom and a.time < :dayTo then a.amount end), 0),
                count(case when a.time >= :dayFrom and a.time < :dayTo then 1 end),
                coalesce(sum(a.amount), 0))
            from LoadAttempt a
            where a.customerId = :customerId and a.accepted = true
              and a.time >= :weekFrom and a.time < :weekTo
            """)
    CustomerActivity acceptedActivity(@Param("customerId") String customerId,
                                      @Param("dayFrom") Instant dayFrom, @Param("dayTo") Instant dayTo,
                                      @Param("weekFrom") Instant weekFrom, @Param("weekTo") Instant weekTo);
}
