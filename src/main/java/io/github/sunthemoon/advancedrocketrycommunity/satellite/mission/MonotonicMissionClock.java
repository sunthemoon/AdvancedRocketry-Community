package io.github.sunthemoon.advancedrocketrycommunity.satellite.mission;

/** Persistable game-time clock that never moves backward after a time rollback. */
public final class MonotonicMissionClock {
    private long logicalGameTime;
    private long lastObservedGameTime;

    private MonotonicMissionClock(long logicalGameTime, long lastObservedGameTime) {
        if (logicalGameTime < 0L || lastObservedGameTime < 0L) {
            throw new IllegalArgumentException("Mission clock values cannot be negative");
        }
        this.logicalGameTime = logicalGameTime;
        this.lastObservedGameTime = lastObservedGameTime;
    }

    public static MonotonicMissionClock create(long observedGameTime) {
        return new MonotonicMissionClock(observedGameTime, observedGameTime);
    }

    public static MonotonicMissionClock restore(long logicalGameTime, long lastObservedGameTime) {
        return new MonotonicMissionClock(logicalGameTime, lastObservedGameTime);
    }

    public long advance(long observedGameTime) {
        if (observedGameTime < 0L) {
            throw new IllegalArgumentException("Observed game time cannot be negative");
        }
        if (observedGameTime >= lastObservedGameTime) {
            logicalGameTime = saturatingAdd(logicalGameTime, observedGameTime - lastObservedGameTime);
        }
        lastObservedGameTime = observedGameTime;
        return logicalGameTime;
    }

    public long logicalGameTime() {
        return logicalGameTime;
    }

    public long lastObservedGameTime() {
        return lastObservedGameTime;
    }

    private static long saturatingAdd(long left, long right) {
        if (right > Long.MAX_VALUE - left) {
            return Long.MAX_VALUE;
        }
        return left + right;
    }
}
