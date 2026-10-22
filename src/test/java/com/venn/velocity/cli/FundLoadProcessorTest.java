package com.venn.velocity.cli;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.StringReader;
import java.io.StringWriter;
import java.util.Optional;

import com.venn.velocity.load.FundLoadService;
import com.venn.velocity.load.LoadResponse;

import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;

import tools.jackson.databind.json.JsonMapper;

class FundLoadProcessorTest {

    private final FundLoadService service = mock(FundLoadService.class);
    private final FundLoadProcessor processor = new FundLoadProcessor(service, JsonMapper.builder().build());

    @Test
    void recordRejectedByDatabaseIsSkippedAsInvalidAndLaterRecordsAreStillDecided() throws IOException {
        when(service.process(argThat(load -> load != null && load.id().equals("1"))))
                .thenThrow(new DataIntegrityViolationException("Value too long"));
        when(service.process(argThat(load -> load != null && load.id().equals("2"))))
                .thenReturn(Optional.of(new LoadResponse("2", "1", true)));
        String input = """
                {"id":"1","customer_id":"1","load_amount":"$1.00","time":"2000-01-01T00:00:00Z"}
                {"id":"2","customer_id":"1","load_amount":"$1.00","time":"2000-01-01T01:00:00Z"}
                """;
        StringWriter output = new StringWriter();

        ProcessingSummary summary = processor.process(new BufferedReader(new StringReader(input)), output);

        assertThat(output.toString().lines()).containsExactly("{\"id\":\"2\",\"customer_id\":\"1\",\"accepted\":true}");
        assertThat(summary).isEqualTo(new ProcessingSummary(2, 1, 0, 0, 1));
    }
}
