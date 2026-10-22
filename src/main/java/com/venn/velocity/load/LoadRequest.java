package com.venn.velocity.load;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Raw load attempt exactly as it arrives on the wire. Validated into a {@link FundLoad} before processing.
 */
public record LoadRequest(
        @JsonProperty("id") String id,
        @JsonProperty("customer_id") String customerId,
        @JsonProperty("load_amount") String loadAmount,
        @JsonProperty("time") String time) {
}
