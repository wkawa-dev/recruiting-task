package com.venn.velocity.cli;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.Writer;
import java.util.Optional;

import com.venn.velocity.load.FundLoad;
import com.venn.velocity.load.FundLoadService;
import com.venn.velocity.load.InvalidLoadRequestException;
import com.venn.velocity.load.LoadRequest;
import com.venn.velocity.load.LoadResponse;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

/**
 * Streams newline-delimited JSON load attempts and writes one JSON response per decided attempt.
 * <p>
 * A bad record never halts the stream: malformed, invalid and database-rejected records are logged at WARN and
 * skipped. Infrastructure failures (I/O, database unavailable) propagate, since continuing would silently drop
 * decisions.
 * <p>
 * Raw payloads carry customer and financial data, so they are only logged at DEBUG; WARN entries give the line
 * number and a value-free reason.
 */
@Component
public class FundLoadProcessor {

    private static final Logger log = LoggerFactory.getLogger(FundLoadProcessor.class);

    private final FundLoadService service;
    private final JsonMapper jsonMapper;

    public FundLoadProcessor(FundLoadService service, JsonMapper jsonMapper) {
        this.service = service;
        this.jsonMapper = jsonMapper;
    }

    /** Processes the whole input, flushing after every response so consumers of a pipe see decisions promptly. */
    public ProcessingSummary process(BufferedReader input, Writer output) throws IOException {
        long lineNumber = 0;
        long accepted = 0;
        long declined = 0;
        long duplicates = 0;
        long invalid = 0;

        String line;
        while ((line = input.readLine()) != null) {
            lineNumber++;
            if (line.isBlank()) {
                continue;
            }
            Optional<LoadResponse> result;
            try {
                FundLoad load = FundLoad.from(jsonMapper.readValue(line, LoadRequest.class));
                result = service.process(load);
            } catch (JacksonException e) {
                invalid++;
                log.warn("Skipping invalid input at line {}: malformed JSON", lineNumber);
                log.debug("Invalid payload at line {}: {}", lineNumber, line, e);
                continue;
            } catch (InvalidLoadRequestException e) {
                invalid++;
                log.warn("Skipping invalid input at line {}: {}", lineNumber, e.getMessage());
                log.debug("Invalid payload at line {}: {}", lineNumber, line);
                continue;
            } catch (DataIntegrityViolationException e) {
                // The database message quotes the offending values, so the details go to DEBUG only.
                invalid++;
                log.warn("Skipping invalid input at line {}: rejected by the database", lineNumber);
                log.debug("Rejected payload at line {}: {}", lineNumber, line, e);
                continue;
            }

            if (result.isEmpty()) {
                duplicates++;
                continue;
            }
            LoadResponse response = result.get();
            if (response.accepted()) {
                accepted++;
            } else {
                declined++;
            }
            output.write(jsonMapper.writeValueAsString(response));
            output.write('\n');
            output.flush();
        }

        ProcessingSummary summary = new ProcessingSummary(lineNumber, accepted, declined, duplicates, invalid);
        log.info("Processing complete: {}", summary);
        return summary;
    }
}
