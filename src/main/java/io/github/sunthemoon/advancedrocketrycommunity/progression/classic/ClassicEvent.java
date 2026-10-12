package io.github.sunthemoon.advancedrocketrycommunity.progression.classic;

import java.util.Arrays;
import java.util.Optional;

/** ADR-066 §6.2 server events without a vanilla trigger; each is fired only from its real completion point. */
public enum ClassicEvent {
    /** A new binding generation of a classic multiblock reached FORMED. */
    FORMED_MULTIBLOCK("formed_multiblock"),
    /** The server accepted a rocket seat right-click on vanilla TNT. */
    SEAT_ON_TNT("seat_on_tnt"),
    /** A committed rocket descent completed in the Moon Level with the player aboard. */
    MOON_LANDING("moon_landing"),
    /** A checked station warp committed while the player was present in the station with VISIT access. */
    STATION_WARP("station_warp");

    private final String serializedName;

    ClassicEvent(String serializedName) {
        this.serializedName = serializedName;
    }

    public String serializedName() {
        return serializedName;
    }

    public static Optional<ClassicEvent> byName(String name) {
        return Arrays.stream(values()).filter(event -> event.serializedName.equals(name)).findFirst();
    }
}
