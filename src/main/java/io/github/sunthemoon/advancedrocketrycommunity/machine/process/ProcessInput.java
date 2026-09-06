package io.github.sunthemoon.advancedrocketrycommunity.machine.process;

import java.util.List;
import java.util.Objects;

/** One bounded ingredient with deterministic, server-resolved alternatives. */
public record ProcessInput(
        ProcessResourceKind kind,
        String channel,
        List<String> alternatives,
        long amount
) {
    public static final int MAX_VARIANTS = 32;

    public ProcessInput {
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(alternatives, "alternatives");
        if (alternatives.isEmpty() || alternatives.size() > MAX_VARIANTS) {
            throw new IllegalArgumentException("ingredient alternatives must contain 1..32 entries");
        }
        if (amount < 1) {
            throw new IllegalArgumentException("input amount must be positive");
        }
        alternatives = List.copyOf(alternatives);
        if (alternatives.stream().distinct().count() != alternatives.size()) {
            throw new IllegalArgumentException("ingredient alternatives cannot contain duplicates");
        }
        for (String resourceId : alternatives) {
            new ProcessResourceKey(kind, channel, resourceId);
        }
        channel = new ProcessResourceKey(kind, channel, alternatives.get(0)).channel();
    }

    public ProcessResourceKey key(String resourceId) {
        return new ProcessResourceKey(kind, channel, resourceId);
    }
}
