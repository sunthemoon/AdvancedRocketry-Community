package io.github.sunthemoon.arceintenttest;

import java.util.Locale;

/** Each trigger emits exactly one ordinary, well-formed intent. */
public enum IntentCase {
    FLIGHT_STALE("rocket_flight", 10),
    FLIGHT_RANGE("rocket_flight", 10),
    FLIGHT_NON_OWNER("rocket_flight", 10),
    FLIGHT_LATE("rocket_flight", 10),
    FLIGHT_FORBIDDEN_SURFACE("rocket_flight", 10),
    FLIGHT_PRIVATE_STATION("rocket_flight", 10),
    CLASSIC_STALE("classic_controls", 3),
    CLASSIC_RANGE("classic_controls", 3),
    CLASSIC_NON_OWNER("classic_controls", 3),
    CLASSIC_LATE("classic_controls", 3),
    MOTION_NO_JETPACK("suit_motion", 2),
    MOTION_NO_HOVER("suit_motion", 2),
    MOTION_DISABLED("suit_motion", 2),
    MOTION_SPECTATOR("suit_motion", 2),
    MOTION_CONFIG_DISABLED("suit_motion", 2);

    private final String channel;
    private final int protocol;

    IntentCase(String channel, int protocol) {
        this.channel = "advancedrocketrycommunity:" + channel;
        this.protocol = protocol;
    }

    public String channel() { return channel; }
    public int protocol() { return protocol; }
    public boolean flight() { return name().startsWith("FLIGHT_"); }
    public boolean classic() { return name().startsWith("CLASSIC_"); }
    public String id() { return name().toLowerCase(Locale.ROOT); }
    public static IntentCase parse(String id) { return valueOf(id.toUpperCase(Locale.ROOT)); }
}
