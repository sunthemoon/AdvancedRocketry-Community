package io.github.sunthemoon.advancedrocketrycommunity.satellite.command;

/** Pure, bounded formatting of a completed vanilla/satellite tick-ring snapshot. */
final class RawTickTimings {
    static final int WINDOW = 100;

    private RawTickTimings() { }

    /**
     * Tick IDs start at 1: tickServer increments the counter before recording slot tick % 100.
     * At END of tick C, populated slots represent max(1, C - 99)..C, oldest first. In particular,
     * slot 0 first becomes populated at tick 100, not tick 0. Never infer population from duration zero.
     * The command's current tick can be C (between ticks) or C + 1 (inside the next tick).
     */
    static String format(int currentTick, int completedTick, long[] vanilla, long[] post) {
        if (completedTick < 0 || currentTick < completedTick
                || vanilla.length != WINDOW || post.length != WINDOW) {
            throw new IllegalArgumentException("Invalid completed tick snapshot");
        }
        int count = Math.min(completedTick, WINDOW);
        StringBuilder line = new StringBuilder("ticks_raw tick=").append(currentTick)
                .append(" entries=").append(count).append(" data=");
        for (int offset = 0; offset < count; offset++) {
            int tick = completedTick - count + 1 + offset;
            int index = tick % WINDOW;
            if (vanilla[index] < 0 || post[index] < 0) {
                throw new IllegalArgumentException("Negative tick duration");
            }
            if (offset != 0) { line.append(','); }
            line.append(tick).append(':').append(Math.addExact(vanilla[index], post[index]));
        }
        return line.toString();
    }
}
