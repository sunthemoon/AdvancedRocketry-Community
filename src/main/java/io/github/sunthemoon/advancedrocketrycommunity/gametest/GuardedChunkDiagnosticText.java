package io.github.sunthemoon.advancedrocketrycommunity.gametest;

import java.util.Objects;

/** Bounded single-line ASCII encoding for the opt-in native diagnostic. */
final class GuardedChunkDiagnosticText {
    static final int MAX_BYTES = 256;
    private static final String HEX = "0123456789abcdef";

    record Encoded(String text, boolean truncated) { }

    static Encoded encode(String input) {
        Objects.requireNonNull(input, "input");
        StringBuilder output = new StringBuilder(MAX_BYTES);
        for (int index = 0; index < input.length(); index++) {
            char value = input.charAt(index);
            int width = value == '"' || value == '\\' ? 2 : value >= 32 && value <= 126 ? 1 : 6;
            if (output.length() + width > MAX_BYTES) {
                return new Encoded(output.toString(), true);
            }
            if (width == 1) {
                output.append(value);
            } else if (width == 2) {
                output.append('\\').append(value);
            } else {
                output.append("\\u").append(HEX.charAt((value >>> 12) & 15))
                        .append(HEX.charAt((value >>> 8) & 15)).append(HEX.charAt((value >>> 4) & 15))
                        .append(HEX.charAt(value & 15));
            }
        }
        return new Encoded(output.toString(), false);
    }

    private GuardedChunkDiagnosticText() { }
}
