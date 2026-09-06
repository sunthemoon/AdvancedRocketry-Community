package io.github.sunthemoon.advancedrocketrycommunity.machine.port;

public record ProcessPortRange(int first, int count) {
    public static final int MAX_INDEX_EXCLUSIVE = 64;

    public ProcessPortRange {
        if (first < 0 || count < 1 || Math.addExact(first, count) > MAX_INDEX_EXCLUSIVE) {
            throw new IllegalArgumentException("port range must be non-empty and end at or before 64");
        }
    }

    public int endExclusive() {
        return first + count;
    }
}
