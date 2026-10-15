package io.github.sunthemoon.advancedrocketrycommunity.progression.classic.firstevent;

import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketFlightLimits;
import java.util.Arrays;
import java.util.Optional;

/**
 * The two world-first kinds of ADR-066 section 6.2 (W44 sections 1.1 and 2.1). The serialized name is the event
 * {@code kind}, the progression receipt key and the {@code ClassicEvent} name; the source names the root that owns
 * the outbox.
 */
public enum FirstEventKind {
    /** The Moon landing outbox lives in the transfer journal; the passenger bound wins (W44 section 2.1). */
    FIRST_MOON_LANDING("first_moon_landing", "rocket_transfers", RocketFlightLimits.MAX_PASSENGERS, 4 * 1_024),
    /** The station warp outbox lives in the station registry; ADR-066 bounds it at 4,096 participants and 128 KiB. */
    FIRST_STATION_WARP("first_station_warp", "stations", 4_096, 128 * 1_024);

    /** Outbox and event depth bound (ADR-066). */
    public static final int MAX_DEPTH = 16;
    /** Outbox and event node bound (ADR-066). Participants are one flat array, so n does not add nodes. */
    public static final int MAX_NODES = 8_192;

    private final String serializedName;
    private final String source;
    private final int maxParticipants;
    private final int maxOutboxBytes;

    FirstEventKind(String serializedName, String source, int maxParticipants, int maxOutboxBytes) {
        this.serializedName = serializedName;
        this.source = source;
        this.maxParticipants = maxParticipants;
        this.maxOutboxBytes = maxOutboxBytes;
    }

    public String serializedName() { return serializedName; }

    /** The receipt's {@code source} value. */
    public String source() { return source; }

    public int maxParticipants() { return maxParticipants; }

    public int maxOutboxBytes() { return maxOutboxBytes; }

    public static Optional<FirstEventKind> byName(String name) {
        return Arrays.stream(values()).filter(kind -> kind.serializedName.equals(name)).findFirst();
    }
}
