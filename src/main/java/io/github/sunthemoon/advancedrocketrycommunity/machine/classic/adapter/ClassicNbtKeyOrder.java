package io.github.sunthemoon.advancedrocketrycommunity.machine.classic.adapter;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Objects;

/** Hash-v1 key order only; callers must preflight their own bounded native input before sorting. */
final class ClassicNbtKeyOrder {
    private ClassicNbtKeyOrder() { }

    static int compare(String left, String right) {
        Objects.requireNonNull(left, "left");
        Objects.requireNonNull(right, "right");
        int primary = Arrays.compareUnsigned(
                left.getBytes(StandardCharsets.UTF_8),
                right.getBytes(StandardCharsets.UTF_8)
        );
        if (primary != 0) {
            return primary;
        }
        // Ordering bytes may replace malformed surrogates. Tie on the original units, never reconstructed keys.
        int length = Math.min(left.length(), right.length());
        for (int index = 0; index < length; index++) {
            int secondary = Character.compare(left.charAt(index), right.charAt(index));
            if (secondary != 0) {
                return secondary;
            }
        }
        return Integer.compare(left.length(), right.length());
    }
}
