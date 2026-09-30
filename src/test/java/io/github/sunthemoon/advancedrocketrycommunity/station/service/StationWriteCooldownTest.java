package io.github.sunthemoon.advancedrocketrycommunity.station.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.sunthemoon.advancedrocketrycommunity.station.model.StationLimits;
import java.util.UUID;
import org.junit.jupiter.api.Test;

final class StationWriteCooldownTest {
    @Test
    void oneWritePerStationPerWindow() {
        StationWriteCooldown cooldown = new StationWriteCooldown(StationGravityService.WRITE_COOLDOWN_TICKS);
        UUID station = UUID.randomUUID();
        UUID other = UUID.randomUUID();
        assertTrue(cooldown.ready(station, 1_000));
        cooldown.record(station, 1_000);
        assertFalse(cooldown.ready(station, 1_000));
        assertFalse(cooldown.ready(station, 1_099));
        assertTrue(cooldown.ready(station, 1_100));
        assertTrue(cooldown.ready(other, 1_000), "Cooldown is per station");
        assertTrue(cooldown.ready(station, 999), "A tick counter reset never blocks forever");
        cooldown.clear();
        assertTrue(cooldown.ready(station, 1_001));
        assertThrows(IllegalArgumentException.class, () -> new StationWriteCooldown(0));
    }

    @Test
    void expiredEntriesArePurgedAndTheMapStaysBounded() {
        StationWriteCooldown cooldown = new StationWriteCooldown(100);
        for (int index = 0; index < 50; index++) {
            cooldown.record(UUID.randomUUID(), index);
        }
        cooldown.record(UUID.randomUUID(), 500);
        assertEquals(1, cooldown.size(), "Expired entries are dropped on the next write");
        for (int index = 0; index < StationLimits.MAX_STATIONS + 10; index++) {
            cooldown.record(UUID.randomUUID(), 1_000);
        }
        assertEquals(StationLimits.MAX_STATIONS, cooldown.size());
    }
}
