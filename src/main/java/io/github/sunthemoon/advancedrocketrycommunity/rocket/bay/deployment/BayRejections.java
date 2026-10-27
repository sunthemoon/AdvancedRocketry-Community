package io.github.sunthemoon.advancedrocketrycommunity.rocket.bay.deployment;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * The runtime rejection map (W128's one-page design): the last D0 or D1 refusal of each satellite, which nothing durable
 * records. At most 4,096 entries; the oldest is evicted. RAM only, cleared when the session ends. Owner-thread only.
 */
public final class BayRejections {
    public static final int LIMIT = 4_096;
    public record Rejection(String reason, UUID rocket, long tick) {
        public Rejection {
            Objects.requireNonNull(reason, "reason");
            Objects.requireNonNull(rocket, "rocket");
        }
    }

    private final LinkedHashMap<UUID, Rejection> entries = new LinkedHashMap<>() {
        @Override protected boolean removeEldestEntry(Map.Entry<UUID, Rejection> eldest) { return size() > LIMIT; }
    };

    /** A newer refusal of the same satellite replaces the older one and becomes the newest entry. */
    public void put(UUID satellite, String reason, UUID rocket, long tick) {
        Objects.requireNonNull(satellite, "satellite");
        entries.remove(satellite);
        entries.put(satellite, new Rejection(reason, rocket, tick));
    }

    public Optional<Rejection> get(UUID satellite) { return Optional.ofNullable(entries.get(satellite)); }
    public void remove(UUID satellite) { entries.remove(satellite); }
    public int size() { return entries.size(); }
    public void clear() { entries.clear(); }
}
