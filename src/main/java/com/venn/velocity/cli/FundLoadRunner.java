package com.venn.velocity.cli;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Command-line entry point.
 * <pre>
 *   --input=&lt;file&gt;   load attempts, one JSON object per line (default: input.txt)
 *   --output=&lt;file&gt;  where to write responses (default: standard output)
 * </pre>
 */
@Component
@ConditionalOnProperty(name = "velocity.cli.enabled", havingValue = "true", matchIfMissing = true)
public class FundLoadRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(FundLoadRunner.class);
    static final String DEFAULT_INPUT = "input.txt";

    private final FundLoadProcessor processor;

    public FundLoadRunner(FundLoadProcessor processor) {
        this.processor = processor;
    }

    @Override
    public void run(ApplicationArguments args) throws IOException {
        Path input = Path.of(singleOption(args, "input", DEFAULT_INPUT));
        String outputOption = singleOption(args, "output", null);

        if (!Files.isReadable(input)) {
            throw new IllegalArgumentException("Input file not found or not readable: " + input.toAbsolutePath());
        }
        log.info("Processing load attempts from {} to {}", input.toAbsolutePath(),
                outputOption == null ? "stdout" : Path.of(outputOption).toAbsolutePath());

        try (BufferedReader reader = Files.newBufferedReader(input, StandardCharsets.UTF_8)) {
            if (outputOption == null) {
                // Do not close System.out.
                processor.process(reader, new OutputStreamWriter(System.out, StandardCharsets.UTF_8));
            } else {
                try (Writer writer = Files.newBufferedWriter(Path.of(outputOption), StandardCharsets.UTF_8)) {
                    processor.process(reader, writer);
                }
            }
        }
    }

    private static String singleOption(ApplicationArguments args, String name, String defaultValue) {
        List<String> values = args.getOptionValues(name);
        if (values == null || values.isEmpty()) {
            return defaultValue;
        }
        if (values.size() > 1) {
            throw new IllegalArgumentException("Option --" + name + " given more than once");
        }
        return values.get(0);
    }
}
