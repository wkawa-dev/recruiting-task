package com.venn.velocity.load;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.function.IntFunction;

import com.venn.velocity.domain.CustomerRepository;
import com.venn.velocity.domain.LoadAttempt;
import com.venn.velocity.domain.LoadAttemptRepository;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.RepeatedTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * Fires many loads for one customer at the same instant; limits must hold exactly as if they were sequential.
 */
@SpringBootTest(properties = "velocity.cli.enabled=false")
class FundLoadServiceConcurrencyTest {

    private static final int THREADS = 16;
    private static final String TIME = "2000-01-03T10:00:00Z";

    @Autowired
    private FundLoadService service;

    @Autowired
    private LoadAttemptRepository loadAttempts;

    @Autowired
    private CustomerRepository customers;

    private final ExecutorService executor = Executors.newFixedThreadPool(THREADS);

    @BeforeEach
    void cleanDatabase() {
        loadAttempts.deleteAll();
        customers.deleteAll();
    }

    @AfterEach
    void shutdown() {
        executor.shutdownNow();
    }

    @RepeatedTest(5)
    void dailyLoadCountHoldsUnderConcurrentLoads() throws Exception {
        List<Optional<LoadResponse>> responses = runConcurrently(i -> load("load-" + i, "100.00"));

        assertThat(responses).allMatch(Optional::isPresent);
        assertThat(acceptedCount(responses)).isEqualTo(3);
        assertThat(loadAttempts.findAll().stream().filter(LoadAttempt::isAccepted).map(LoadAttempt::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add)).isEqualByComparingTo("300.00");
    }

    @RepeatedTest(5)
    void dailyAmountHoldsUnderConcurrentLoads() throws Exception {
        List<Optional<LoadResponse>> responses = runConcurrently(i -> load("load-" + i, "3000.00"));

        assertThat(acceptedCount(responses)).isEqualTo(1);
    }

    @RepeatedTest(5)
    void concurrentDuplicatesProduceExactlyOneResponse() throws Exception {
        List<Optional<LoadResponse>> responses = runConcurrently(i -> load("same-id", "100.00"));

        assertThat(responses.stream().filter(Optional::isPresent)).hasSize(1);
        assertThat(loadAttempts.count()).isEqualTo(1);
    }

    private List<Optional<LoadResponse>> runConcurrently(IntFunction<FundLoad> loads) throws Exception {
        CountDownLatch start = new CountDownLatch(1);
        List<Future<Optional<LoadResponse>>> futures = new ArrayList<>();
        for (int i = 0; i < THREADS; i++) {
            FundLoad load = loads.apply(i);
            Callable<Optional<LoadResponse>> task = () -> {
                start.await();
                return service.process(load);
            };
            futures.add(executor.submit(task));
        }
        start.countDown();
        List<Optional<LoadResponse>> responses = new ArrayList<>();
        for (Future<Optional<LoadResponse>> future : futures) {
            responses.add(future.get());
        }
        return responses;
    }

    private static long acceptedCount(List<Optional<LoadResponse>> responses) {
        return responses.stream().flatMap(Optional::stream).filter(LoadResponse::accepted).count();
    }

    private static FundLoad load(String id, String amount) {
        return new FundLoad(id, "c1", new BigDecimal(amount), Instant.parse(TIME));
    }
}
