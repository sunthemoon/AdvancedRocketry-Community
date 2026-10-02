package io.github.sunthemoon.advancedrocketrycommunity.endgame.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class EndgameModelTest {
    @Test
    void idOrderIsTheCanonicalStringOrderNotTheSignedUuidOrder() {
        UUID high = UUID.fromString("80000000-0000-0000-0000-000000000000");
        UUID low = UUID.fromString("10000000-0000-0000-0000-000000000000");
        assertTrue(high.compareTo(low) < 0, "UUID.compareTo compares the signed most significant half");
        assertTrue(EndgameIdOrder.compare(high, low) > 0, "ADR-054 section 7 orders the lowercase strings");
        List<UUID> ids = new ArrayList<>(List.of(high, low, UUID.fromString("0fffffff-ffff-ffff-ffff-ffffffffffff")));
        ids.sort(EndgameIdOrder.ORDER);
        assertEquals(List.of("0fffffff-ffff-ffff-ffff-ffffffffffff", "10000000-0000-0000-0000-000000000000",
                "80000000-0000-0000-0000-000000000000"), ids.stream().map(UUID::toString).toList());
    }

    /** The allocation-free order (C13) is exactly the order of the canonical lowercase strings. */
    @Test
    void theIdOrderIsTheCanonicalStringOrderForAnyTwoIds() {
        java.util.Random random = new java.util.Random(54L);
        long[] edges = {0L, 1L, -1L, Long.MIN_VALUE, Long.MAX_VALUE, 0x0fffffffffffffffL, 0x8000000000000001L};
        List<UUID> ids = new ArrayList<>();
        for (long high : edges) {
            for (long low : edges) {
                ids.add(new UUID(high, low));
            }
        }
        for (int i = 0; i < 2_000; i++) {
            ids.add(new UUID(random.nextLong(), random.nextLong()));
        }
        for (int i = 0; i < 20_000; i++) {
            UUID first = ids.get(random.nextInt(ids.size()));
            UUID second = i % 7 == 0 ? new UUID(first.getMostSignificantBits(), random.nextLong())
                    : ids.get(random.nextInt(ids.size()));
            assertEquals(Integer.signum(first.toString().compareTo(second.toString())),
                    Integer.signum(EndgameIdOrder.compare(first, second)), first + " " + second);
        }
    }

    @Test
    void theAccountedMaximumFitsBelowTheGrowthThresholdWithEveryCapReached() {
        assertEquals(2_816, EndgameLimits.MAX_PINNED_TOMBSTONES);
        assertEquals(2_932_736L, EndgameLimits.ACCOUNTED_MAXIMUM_BYTES);
        assertTrue(EndgameLimits.ACCOUNTED_MAXIMUM_BYTES < EndgameLimits.GROWTH_ADMISSION_BYTES);
        assertTrue(EndgameLimits.GROWTH_ADMISSION_BYTES < EndgameLimits.MAX_ROOT_BYTES);
    }

    @Test
    void settingsDefaultToTheContractAndRejectValuesBeyondTheirMaxima() {
        EndgameSettings defaults = EndgameSettings.DEFAULTS;
        for (EndgameSystem system : EndgameSystem.values()) {
            assertTrue(defaults.enabled(system), system.id());
        }
        assertFalse(defaults.laserPhysicalMining(), "physical mining is off by default (ADR-055)");
        assertEquals(10, defaults.intentIntervalTicks());
        assertEquals(2, defaults.selectionIntervalTicks());
        assertThrows(IllegalArgumentException.class, () -> new EndgameSettings(true, false, true, true, true, true,
                9, 2, 2048, 64, 256));
        assertThrows(IllegalArgumentException.class, () -> new EndgameSettings(true, false, true, true, true, true,
                10, 1, 2048, 64, 256));
        assertThrows(IllegalArgumentException.class, () -> new EndgameSettings(true, false, true, true, true, true,
                10, 2, 2049, 64, 256));
        assertThrows(IllegalArgumentException.class, () -> new EndgameSettings(true, false, true, true, true, true,
                10, 2, 2048, 65, 256));
        assertThrows(IllegalArgumentException.class, () -> new EndgameSettings(true, false, true, true, true, true,
                10, 2, 2048, 64, 257));
        EndgameSettings railgunOff = new EndgameSettings(true, false, false, true, true, true, 10, 2, 2048, 64, 256);
        assertFalse(railgunOff.enabled(EndgameSystem.RAILGUN));
        assertTrue(railgunOff.enabled(EndgameSystem.LASER_DRILL));
    }

    @Test
    void systemIdsAndCodeTranslationKeysAreStableAndUnique() {
        assertEquals(List.of("laser_drill", "railgun", "black_hole_generator", "gravity_field", "space_elevator"),
                java.util.Arrays.stream(EndgameSystem.values()).map(EndgameSystem::id).toList());
        assertEquals(EndgameSystem.GRAVITY_FIELD, EndgameSystem.byId("gravity_field").orElseThrow());
        assertTrue(EndgameSystem.byId("gravity").isEmpty());
        Set<String> keys = new HashSet<>();
        for (EndgameCode code : EndgameCode.values()) {
            assertTrue(keys.add(code.translationKey()), code.name());
            assertTrue(code.translationKey().startsWith("advancedrocketrycommunity.endgame.code."));
            assertEquals(code, EndgameCode.byName(code.name()).orElseThrow());
        }
        assertEquals("advancedrocketrycommunity.endgame.code.target_protected",
                EndgameCode.TARGET_PROTECTED.translationKey());
    }
}
