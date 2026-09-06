package io.github.sunthemoon.advancedrocketrycommunity.machine.port;

public final class ProcessPortRevision {
    private long value;
    private final Runnable changed;

    public ProcessPortRevision(long initialValue, Runnable changed) {
        if (initialValue < 0) {
            throw new IllegalArgumentException("port revision cannot be negative");
        }
        this.value = initialValue;
        this.changed = java.util.Objects.requireNonNull(changed, "changed");
    }

    public long value() {
        return value;
    }

    public void recordMutation() {
        value = Math.addExact(value, 1);
        changed.run();
    }
}
