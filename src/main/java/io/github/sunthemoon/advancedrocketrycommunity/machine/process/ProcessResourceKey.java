package io.github.sunthemoon.advancedrocketrycommunity.machine.process;

import java.util.Objects;
import java.util.regex.Pattern;

/** Stable resource identity resolved by the server-side recipe adapter. */
public record ProcessResourceKey(ProcessResourceKind kind, String channel, String resourceId)
        implements Comparable<ProcessResourceKey> {
    public static final int MAX_CHANNEL_CHARS = 64;
    public static final int MAX_RESOURCE_ID_CHARS = 128;

    private static final Pattern CHANNEL_PATTERN = Pattern.compile("[a-z0-9_.-]+");
    private static final Pattern RESOURCE_ID_PATTERN = Pattern.compile("[a-z0-9_.-]+:[a-z0-9/._-]+");

    public ProcessResourceKey {
        Objects.requireNonNull(kind, "kind");
        channel = requireBoundedMatch("channel", channel, MAX_CHANNEL_CHARS, CHANNEL_PATTERN);
        resourceId = requireBoundedMatch(
                "resourceId",
                resourceId,
                MAX_RESOURCE_ID_CHARS,
                RESOURCE_ID_PATTERN
        );
    }

    @Override
    public int compareTo(ProcessResourceKey other) {
        int kindOrder = kind.compareTo(other.kind);
        if (kindOrder != 0) {
            return kindOrder;
        }
        int channelOrder = channel.compareTo(other.channel);
        return channelOrder != 0 ? channelOrder : resourceId.compareTo(other.resourceId);
    }

    private static String requireBoundedMatch(String field, String value, int maxChars, Pattern pattern) {
        Objects.requireNonNull(value, field);
        if (value.isEmpty() || value.length() > maxChars || !pattern.matcher(value).matches()) {
            throw new IllegalArgumentException(field + " is not a bounded resource-style identifier");
        }
        return value;
    }
}
