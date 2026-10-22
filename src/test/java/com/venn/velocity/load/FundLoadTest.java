package com.venn.velocity.load;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.Instant;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

class FundLoadTest {

    @Test
    void parsesValidRequest() {
        FundLoad load = FundLoad.from(new LoadRequest("1234", "528", "$123.45", "2018-01-01T00:00:00Z"));

        assertThat(load.id()).isEqualTo("1234");
        assertThat(load.customerId()).isEqualTo("528");
        assertThat(load.amount()).isEqualByComparingTo("123.45");
        assertThat(load.time()).isEqualTo(Instant.parse("2018-01-01T00:00:00Z"));
    }

    @ParameterizedTest
    @CsvSource({"$0.01, 0.01", "$5, 5.00", "$5.1, 5.10", "$4999.99, 4999.99"})
    void normalisesAmountScale(String raw, String expected) {
        assertThat(FundLoad.parseAmount(raw)).isEqualTo(new BigDecimal(expected));
    }

    @Test
    void acceptsLargestAmountThatFitsTheAmountColumn() {
        assertThat(FundLoad.parseAmount("$99999999999999999.99")).isEqualTo(new BigDecimal("99999999999999999.99"));
    }

    @Test
    void ignoresLeadingZerosWhenCountingDigits() {
        assertThat(FundLoad.parseAmount("$000000000000000000012.50")).isEqualTo(new BigDecimal("12.50"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"123.45", "$-1.00", "$1.234", "$abc", "$", "$1,000.00", "USD10",
            "$100000000000000000", "$100000000000000000000"})
    void rejectsMalformedAmount(String raw) {
        assertThatThrownBy(() -> FundLoad.parseAmount(raw))
                .isInstanceOf(InvalidLoadRequestException.class)
                .hasMessageContaining("load_amount");
    }

    @ParameterizedTest
    @ValueSource(strings = {"$0", "$0.00", "$000.0"})
    void rejectsZeroAmount(String raw) {
        assertThatThrownBy(() -> FundLoad.parseAmount(raw))
                .isInstanceOf(InvalidLoadRequestException.class)
                .hasMessageContaining("greater than zero");
    }

    @Test
    void rejectsMalformedTime() {
        assertThatThrownBy(() -> FundLoad.from(new LoadRequest("1", "1", "$1.00", "2018-01-01 00:00")))
                .isInstanceOf(InvalidLoadRequestException.class)
                .hasMessageContaining("time");
    }

    @Test
    void acceptsTimesAtTheEdgesOfTheSupportedRange() {
        assertThat(FundLoad.parseTime("0001-01-01T00:00:00Z")).isEqualTo(FundLoad.MIN_TIME);
        assertThat(FundLoad.parseTime("9999-12-31T23:59:59Z")).isEqualTo(Instant.parse("9999-12-31T23:59:59Z"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"+10000-01-01T00:00:00Z", "-0001-12-31T23:59:59Z",
            "+1000000000-12-31T23:59:59Z", "-1000000000-01-01T00:00:00Z"})
    void rejectsTimesOutsideTheSupportedRange(String raw) {
        assertThatThrownBy(() -> FundLoad.parseTime(raw))
                .isInstanceOf(InvalidLoadRequestException.class)
                .hasMessageContaining("year");
    }

    @Test
    void truncatesTimeToStoredPrecisionSoLoadStaysInItsDay() {
        assertThat(FundLoad.parseTime("2000-01-01T23:59:59.999999999Z"))
                .isEqualTo(Instant.parse("2000-01-01T23:59:59.999999Z"));
    }

    @Test
    void acceptsIdsUpToColumnLengthAndRejectsLonger() {
        String maxId = "x".repeat(255);
        String tooLong = maxId + "x";

        assertThat(FundLoad.from(new LoadRequest(maxId, maxId, "$1.00", "2018-01-01T00:00:00Z")).id())
                .isEqualTo(maxId);
        assertThatThrownBy(() -> FundLoad.from(new LoadRequest(tooLong, "1", "$1.00", "2018-01-01T00:00:00Z")))
                .isInstanceOf(InvalidLoadRequestException.class)
                .hasMessageContaining("'id' exceeds");
        assertThatThrownBy(() -> FundLoad.from(new LoadRequest("1", tooLong, "$1.00", "2018-01-01T00:00:00Z")))
                .isInstanceOf(InvalidLoadRequestException.class)
                .hasMessageContaining("'customer_id' exceeds");
    }

    @Test
    void rejectsMissingFields() {
        assertThatThrownBy(() -> FundLoad.from(new LoadRequest(null, "1", "$1.00", "2018-01-01T00:00:00Z")))
                .hasMessageContaining("'id'");
        assertThatThrownBy(() -> FundLoad.from(new LoadRequest("1", " ", "$1.00", "2018-01-01T00:00:00Z")))
                .hasMessageContaining("'customer_id'");
        assertThatThrownBy(() -> FundLoad.from(new LoadRequest("1", "1", null, "2018-01-01T00:00:00Z")))
                .hasMessageContaining("'load_amount'");
        assertThatThrownBy(() -> FundLoad.from(new LoadRequest("1", "1", "$1.00", "")))
                .hasMessageContaining("'time'");
    }
}
