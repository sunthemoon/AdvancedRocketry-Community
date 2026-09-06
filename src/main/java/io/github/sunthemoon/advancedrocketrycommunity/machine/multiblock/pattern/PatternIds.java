package io.github.sunthemoon.advancedrocketrycommunity.machine.multiblock.pattern;

import java.util.Objects;
import java.util.regex.Pattern;

final class PatternIds {
    static final int MAX_ID_CHARS = 128;
    private static final Pattern RESOURCE_ID = Pattern.compile("[a-z0-9_.-]+:[a-z0-9/._-]+");
    private static final Pattern CHANNEL = Pattern.compile("[a-z0-9_.-]+");

    private PatternIds() {
    }

    static String requireResourceId(String field, String value) {
        return require(field, value, RESOURCE_ID);
    }

    static String requireChannel(String field, String value) {
        return require(field, value, CHANNEL);
    }

    private static String require(String field, String value, Pattern pattern) {
        Objects.requireNonNull(value, field);
        if (value.isEmpty() || value.length() > MAX_ID_CHARS || !pattern.matcher(value).matches()) {
            throw new IllegalArgumentException(field + " is not a bounded identifier");
        }
        return value;
    }
}
