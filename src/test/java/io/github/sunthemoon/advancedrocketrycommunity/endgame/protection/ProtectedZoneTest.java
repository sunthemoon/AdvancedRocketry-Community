package io.github.sunthemoon.advancedrocketrycommunity.endgame.protection;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

/** ADR-054 section 6 zone bounds, names and allow lists. */
class ProtectedZoneTest {
    private static final ResourceLocation OVERWORLD = ResourceLocation.tryBuild("minecraft", "overworld");
    private static final ResourceLocation NETHER = ResourceLocation.tryBuild("minecraft", "the_nether");

    @Test
    void namesAreOneToThirtyTwoLowercaseCharacters() {
        assertTrue(ProtectedZone.validName("spawn"));
        assertTrue(ProtectedZone.validName("base_2-north"));
        assertTrue(ProtectedZone.validName("a".repeat(32)));
        assertFalse(ProtectedZone.validName(""));
        assertFalse(ProtectedZone.validName("a".repeat(33)));
        assertFalse(ProtectedZone.validName("Spawn"));
        assertFalse(ProtectedZone.validName("my zone"));
        assertFalse(ProtectedZone.validName(null));
    }

    @Test
    void spansUpToFourThousandNinetySixBlocksPerAxis() {
        ProtectedZone widest = ProtectedZone.of("w", OVERWORLD, -2048, -2048, 2047, 2047, List.of());
        assertEquals(-2048, widest.minX());
        assertThrows(IllegalArgumentException.class,
                () -> ProtectedZone.of("w", OVERWORLD, -2048, 0, 2048, 0, List.of()));
        assertThrows(IllegalArgumentException.class,
                () -> ProtectedZone.of("w", OVERWORLD, 0, -2048, 0, 2048, List.of()));
        ProtectedZone swapped = ProtectedZone.of("s", OVERWORLD, 10, 20, -10, -20, List.of());
        assertEquals(List.of(-10, -20, 10, 20), List.of(swapped.minX(), swapped.minZ(), swapped.maxX(), swapped.maxZ()));
        assertThrows(IllegalArgumentException.class, () -> new ProtectedZone("i", OVERWORLD, 1, 0, 0, 0, List.of()));
    }

    @Test
    void allowListsHoldAtMostSixteenUniquePlayers() {
        List<UUID> sixteen = new ArrayList<>();
        for (int i = 0; i < 16; i++) {
            sixteen.add(new UUID(0L, i));
        }
        ProtectedZone zone = ProtectedZone.of("z", OVERWORLD, 0, 0, 0, 0, sixteen);
        assertTrue(zone.allows(new UUID(0L, 15)));
        assertFalse(zone.allows(new UUID(0L, 16)));
        List<UUID> seventeen = new ArrayList<>(sixteen);
        seventeen.add(new UUID(0L, 16));
        assertThrows(IllegalArgumentException.class, () -> ProtectedZone.of("z", OVERWORLD, 0, 0, 0, 0, seventeen));
        assertThrows(IllegalArgumentException.class,
                () -> ProtectedZone.of("z", OVERWORLD, 0, 0, 0, 0, List.of(new UUID(0L, 1), new UUID(0L, 1))));
    }

    @Test
    void intersectionIsInclusivePerLevelAndIgnoresHeight() {
        ProtectedZone zone = ProtectedZone.of("z", OVERWORLD, 0, 0, 9, 9, List.of());
        assertTrue(zone.intersects(OVERWORLD, 9, 9, 20, 20), "touching corners intersect");
        assertTrue(zone.intersects(OVERWORLD, -5, 3, 0, 4), "a box ending on the edge intersects");
        assertFalse(zone.intersects(OVERWORLD, 10, 0, 20, 9));
        assertFalse(zone.intersects(OVERWORLD, -10, -10, -1, -1));
        assertFalse(zone.intersects(NETHER, 0, 0, 9, 9), "zones are per Level");
        assertTrue(zone.intersects(OVERWORLD, 2, 2, 3, 3), "a box inside intersects");
    }
}
