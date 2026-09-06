package io.github.sunthemoon.advancedrocketrycommunity.machine.multiblock.pattern;

import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/** Already-loaded block observation used by the pure matcher set. */
public record PatternBlock(
        String blockId,
        Set<String> tags,
        boolean air,
        boolean controller,
        Optional<String> portChannel
) {
    public static final int MAX_TAGS = 64;

    public PatternBlock {
        blockId = PatternIds.requireResourceId("blockId", blockId);
        tags = Set.copyOf(Objects.requireNonNull(tags, "tags"));
        if (tags.size() > MAX_TAGS) {
            throw new IllegalArgumentException("observed block exceeds the tag limit");
        }
        tags.forEach(tag -> PatternIds.requireResourceId("tag", tag));
        portChannel = Objects.requireNonNull(portChannel, "portChannel")
                .map(channel -> PatternIds.requireChannel("portChannel", channel));
    }
}
