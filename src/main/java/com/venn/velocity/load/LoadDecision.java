package com.venn.velocity.load;

import java.util.Optional;

import com.venn.velocity.domain.DeclineReason;

public record LoadDecision(Optional<DeclineReason> declineReason) {

    private static final LoadDecision ACCEPTED = new LoadDecision(Optional.empty());

    public static LoadDecision accept() {
        return ACCEPTED;
    }

    public static LoadDecision decline(DeclineReason reason) {
        return new LoadDecision(Optional.of(reason));
    }

    public boolean accepted() {
        return declineReason.isEmpty();
    }
}
