package com.venn.velocity.load;

import java.util.Optional;

import com.venn.velocity.domain.Customer;
import com.venn.velocity.domain.CustomerActivity;
import com.venn.velocity.domain.DeclineReason;
import com.venn.velocity.domain.CustomerRepository;
import com.venn.velocity.domain.LoadAttempt;
import com.venn.velocity.domain.LoadAttemptRepository;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Decides and records load attempts; safe to call concurrently from many threads.
 * <p>
 * The check-then-write sequence (duplicate check, read totals, insert) is only correct if no other load for the
 * same customer interleaves with it. Each decision therefore runs in one transaction that first takes a
 * pessimistic row lock on the customer ({@code SELECT ... FOR UPDATE}). Loads for the same customer are
 * serialised; loads for different customers proceed in parallel.
 */
@Service
public class FundLoadService {

    private static final Logger log = LoggerFactory.getLogger(FundLoadService.class);

    private final LoadAttemptRepository loadAttempts;
    private final CustomerRepository customers;
    private final VelocityLimitPolicy policy;
    private final TransactionTemplate transaction;

    public FundLoadService(LoadAttemptRepository loadAttempts, CustomerRepository customers,
                           VelocityLimitPolicy policy, TransactionTemplate transaction) {
        this.loadAttempts = loadAttempts;
        this.customers = customers;
        this.policy = policy;
        this.transaction = transaction;
    }

    /**
     * @return the decision, or empty if this load id was already seen for the customer (no response is given)
     */
    public Optional<LoadResponse> process(FundLoad load) {
        ensureCustomerExists(load.customerId());
        return transaction.execute(status -> decideAndRecord(load));
    }

    private Optional<LoadResponse> decideAndRecord(FundLoad load) {
        customers.lockById(load.customerId())
                .orElseThrow(() -> new IllegalStateException("Customer row missing: " + load.customerId()));

        if (loadAttempts.existsByCustomerIdAndLoadId(load.customerId(), load.id())) {
            log.info("Ignoring duplicate load id={} customer={}", load.id(), load.customerId());
            return Optional.empty();
        }

        LoadDecision decision = policy.evaluate(activityBefore(load), load.amount());

        DeclineReason reason = decision.declineReason().orElse(null);
        loadAttempts.save(new LoadAttempt(load.id(), load.customerId(), load.amount(), load.time(),
                decision.accepted(), reason));

        // Amounts stay out of INFO logs; the full record is in the database, keyed by customer and load id.
        if (decision.accepted()) {
            log.debug("Accepted load id={} customer={} amount={}", load.id(), load.customerId(), load.amount());
        } else {
            log.info("Declined load id={} customer={} reason={}", load.id(), load.customerId(), reason);
        }
        return Optional.of(new LoadResponse(load.id(), load.customerId(), decision.accepted()));
    }

    /**
     * Creates the customer's lock row in its own short transaction. Two first-time loads may race to insert it;
     * the loser's primary-key violation just means the row now exists.
     */
    private void ensureCustomerExists(String customerId) {
        if (customers.existsById(customerId)) {
            return;
        }
        try {
            customers.saveAndFlush(new Customer(customerId));
        } catch (DataIntegrityViolationException e) {
            log.debug("Customer {} was created concurrently", customerId);
        }
    }

    private CustomerActivity activityBefore(FundLoad load) {
        TimeWindow day = TimeWindow.dayOf(load.time());
        TimeWindow week = TimeWindow.weekOf(load.time());
        return loadAttempts.acceptedActivity(load.customerId(), day.from(), day.to(), week.from(), week.to());
    }
}
