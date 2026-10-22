package com.venn.velocity.cli;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.StringReader;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import com.venn.velocity.domain.LoadAttemptRepository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

@SpringBootTest(properties = "velocity.cli.enabled=false")
@ExtendWith(OutputCaptureExtension.class)
class FundLoadProcessorIntegrationTest {

    @Autowired
    private FundLoadProcessor processor;

    @Autowired
    private LoadAttemptRepository repository;

    @BeforeEach
    void cleanDatabase() {
        repository.deleteAll();
    }

    @Test
    void producesExpectedOutputForProvidedInput() throws IOException {
        StringWriter output = new StringWriter();
        try (BufferedReader input = Files.newBufferedReader(Path.of("input.txt"), StandardCharsets.UTF_8)) {
            processor.process(input, output);
        }

        List<String> expected = Files.readAllLines(Path.of("output.txt"), StandardCharsets.UTF_8);
        assertThat(output.toString().lines().toList()).containsExactlyElementsOf(expected);
    }

    @Test
    void skipsInvalidLinesAndKeepsProcessing() throws IOException {
        String input = String.join("\n",
                "{\"id\":\"1\",\"customer_id\":\"1\",\"load_amount\":\"$100.00\",\"time\":\"2000-01-01T00:00:00Z\"}",
                "not json",
                "{\"id\":\"2\",\"customer_id\":\"1\",\"load_amount\":\"100\",\"time\":\"2000-01-01T00:00:00Z\"}",
                "",
                "{\"id\":\"3\",\"customer_id\":\"1\",\"load_amount\":\"$6000.00\",\"time\":\"2000-01-01T01:00:00Z\"}",
                "{\"id\":\"1\",\"customer_id\":\"1\",\"load_amount\":\"$1.00\",\"time\":\"2000-01-01T02:00:00Z\"}");
        StringWriter output = new StringWriter();

        ProcessingSummary summary = processor.process(new BufferedReader(new StringReader(input)), output);

        assertThat(output.toString().lines()).containsExactly(
                "{\"id\":\"1\",\"customer_id\":\"1\",\"accepted\":true}",
                "{\"id\":\"3\",\"customer_id\":\"1\",\"accepted\":false}");
        assertThat(summary).isEqualTo(new ProcessingSummary(6, 1, 1, 1, 2));
    }

    @Test
    void skipsValuesThatDoNotFitTheSchemaAndKeepsProcessing() throws IOException {
        String longId = "9".repeat(256);
        String input = String.join("\n",
                line("1", "1", "$100000000000000000000", "2000-01-01T00:00:00Z"),
                line(longId, "1", "$1.00", "2000-01-01T00:00:00Z"),
                line("2", longId, "$1.00", "2000-01-01T00:00:00Z"),
                line("3", "1", "$1.00", "+1000000000-12-31T23:59:59Z"),
                line("4", "1", "$1.00", "-1000000000-01-01T00:00:00Z"),
                line("5", "1", "$1.00", "2000-01-01T01:00:00Z"));
        StringWriter output = new StringWriter();

        ProcessingSummary summary = processor.process(new BufferedReader(new StringReader(input)), output);

        assertThat(output.toString().lines()).containsExactly("{\"id\":\"5\",\"customer_id\":\"1\",\"accepted\":true}");
        assertThat(summary).isEqualTo(new ProcessingSummary(6, 1, 0, 0, 5));
    }

    @Test
    void storesValuesAtTheEdgesOfValidation() throws IOException {
        String maxId = "9".repeat(255);
        String input = String.join("\n",
                line(maxId, maxId, "$99999999999999999.99", "2000-01-01T00:00:00Z"),
                line("1", "2", "$1.00", "0001-01-01T00:00:00Z"),
                line("2", "3", "$1.00", "9999-12-31T23:59:59.999999999Z"));
        StringWriter output = new StringWriter();

        ProcessingSummary summary = processor.process(new BufferedReader(new StringReader(input)), output);

        assertThat(summary).isEqualTo(new ProcessingSummary(3, 2, 1, 0, 0));
        assertThat(repository.count()).isEqualTo(3);
    }

    @Test
    void invalidInputIsLoggedWithoutItsPayload(CapturedOutput logs) throws IOException {
        String input = String.join("\n",
                "{\"id\":\"1\",\"customer_id\":\"SENSITIVE-1\",\"load_amount\":\"$12x\",\"time\":\"2000-01-01T00:00:00Z\"}",
                "{\"id\":\"2\",\"customer_id\":\"SENSITIVE-2\" oops}");

        processor.process(new BufferedReader(new StringReader(input)), new StringWriter());

        assertThat(logs.getErr())
                .contains("Skipping invalid input at line 1: Invalid load_amount")
                .contains("Skipping invalid input at line 2: malformed JSON")
                .doesNotContain("SENSITIVE");
    }

    private static String line(String id, String customerId, String amount, String time) {
        return "{\"id\":\"%s\",\"customer_id\":\"%s\",\"load_amount\":\"%s\",\"time\":\"%s\"}"
                .formatted(id, customerId, amount, time);
    }
}
