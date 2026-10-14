package io.github.sunthemoon.advancedrocketrycommunity.progression.classic.firstevent;

import io.github.sunthemoon.advancedrocketrycommunity.ModIdentity;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Fixed sorted UUIDs and events for the W44 A0 tests; no world state. */
public final class FirstEventFixtures {
    public static final String CHECKSUM = "0123456789abcdef".repeat(4);

    private FirstEventFixtures() {
    }

    public static UUID id(int value) {
        return new UUID(0x5744_0000_0000_0000L, value);
    }

    public static List<UUID> participants(int count, int offset) {
        List<UUID> result = new ArrayList<>(count);
        for (int index = 0; index < count; index++) { result.add(id(offset + index)); }
        return result;
    }

    public static FirstEvent.StationWarp warp(int event, List<UUID> participants) {
        return FirstEvent.StationWarp.create(id(event), id(9_001), id(9_002), ModIdentity.id("earth"),
                ModIdentity.id("moon"), 100_000, 400L, participants);
    }

    public static FirstEvent.MoonLanding moon(int event, List<UUID> participants) {
        return FirstEvent.MoonLanding.create(id(event), id(8_001), id(8_002), id(8_003), id(8_004), CHECKSUM,
                12, 65, -4, 2_000L, participants);
    }
}
