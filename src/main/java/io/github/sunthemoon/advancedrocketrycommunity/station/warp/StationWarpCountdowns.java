package io.github.sunthemoon.advancedrocketrycommunity.station.warp;

import io.github.sunthemoon.advancedrocketrycommunity.station.model.StationLimits;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * ADR-044 §4 countdowns, held in memory only: at most one per station and 64 in total. Nothing is
 * persisted before the commit, so a stop or crash during a countdown is simply no warp.
 */
public final class StationWarpCountdowns {
    /** Announced when the countdown starts (ADR-044 §4: 10, 5, 3, 2 and 1 seconds). */
    public static final int START_SECONDS = (int) (StationLimits.WARP_COUNTDOWN_TICKS / 20L);
    /** Later whole seconds before the commit at which online members are told the time left. */
    public static final List<Integer> ANNOUNCED_SECONDS = List.of(5, 3, 2, 1);

    private final Map<UUID, Countdown> byStation = new LinkedHashMap<>();

    public Start start(WarpQuote quote, long nowTick) {
        Objects.requireNonNull(quote, "quote");
        if (byStation.containsKey(quote.stationId())) {
            return Start.ALREADY_RUNNING;
        }
        if (byStation.size() >= StationLimits.MAX_WARP_COUNTDOWNS) {
            return Start.CAPACITY_REACHED;
        }
        byStation.put(quote.stationId(), new Countdown(quote, nowTick, nowTick + StationLimits.WARP_COUNTDOWN_TICKS));
        return Start.STARTED;
    }

    public Optional<Countdown> get(UUID stationId) {
        return Optional.ofNullable(byStation.get(Objects.requireNonNull(stationId, "stationId")));
    }

    public Optional<Countdown> cancel(UUID stationId) {
        return Optional.ofNullable(byStation.remove(Objects.requireNonNull(stationId, "stationId")));
    }

    /** Countdowns that announce a remaining whole second at {@code nowTick}. */
    public List<Announcement> announcements(long nowTick) {
        List<Announcement> result = new ArrayList<>();
        for (Countdown countdown : byStation.values()) {
            long left = countdown.dueTick() - nowTick;
            if (left > 0 && left % 20L == 0L && ANNOUNCED_SECONDS.contains((int) (left / 20L))) {
                result.add(new Announcement(countdown, (int) (left / 20L)));
            }
        }
        return result;
    }

    /** The earliest started countdown that is due, if any; the caller commits at most one per tick. */
    public Optional<Countdown> nextDue(long nowTick) {
        return byStation.values().stream()
                .filter(countdown -> countdown.dueTick() <= nowTick)
                .findFirst();
    }

    public Collection<Countdown> all() {
        return List.copyOf(byStation.values());
    }

    public int size() {
        return byStation.size();
    }

    public void clear() {
        byStation.clear();
    }

    public record Countdown(WarpQuote quote, long startedTick, long dueTick) {
        public long ticksLeft(long nowTick) {
            return Math.max(0L, dueTick - nowTick);
        }
    }

    public record Announcement(Countdown countdown, int secondsLeft) {
    }

    public enum Start {
        STARTED,
        ALREADY_RUNNING,
        CAPACITY_REACHED
    }
}
