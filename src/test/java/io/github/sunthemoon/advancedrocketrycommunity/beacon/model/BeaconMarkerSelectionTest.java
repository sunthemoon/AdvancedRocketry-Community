package io.github.sunthemoon.advancedrocketrycommunity.beacon.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * The finder's visibility filter (ADR-065 section 7): only the viewer's own records, last known active, on the viewer's
 * body and Level; the nearest eight by distance and then canonical UUID.
 */
class BeaconMarkerSelectionTest {
    private static final String EARTH = "advancedrocketrycommunity:earth";
    private static final String MOON = "advancedrocketrycommunity:moon";
    private static final String OVERWORLD = "minecraft:overworld";
    private static final String SPACE = "advancedrocketrycommunity:space";
    private static final UUID VIEWER = UUID.fromString("00000000-0000-0000-0000-00000000000a");
    private static final BeaconMarkerSelection.Viewer AT_ORIGIN =
            new BeaconMarkerSelection.Viewer(VIEWER, EARTH, OVERWORLD, 0, 64, 0);

    private static BeaconRecord beacon(UUID id, UUID owner, String body, String level, int x, boolean active) {
        return new BeaconRecord(id, owner, body, level, x, 64, 0, 3, active, "n" + x);
    }

    private static BeaconRecord beacon(UUID owner, String body, String level, int x, boolean active) {
        return beacon(UUID.randomUUID(), owner, body, level, x, active);
    }

    @Test
    void anotherOwnersBeaconNeverPassesWhereverItStands() {
        UUID stranger = UUID.randomUUID();
        List<BeaconRecord> candidates = List.of(
                beacon(stranger, EARTH, OVERWORLD, 1, true),
                beacon(stranger, EARTH, OVERWORLD, 0, true),
                beacon(VIEWER, EARTH, OVERWORLD, 50, true));
        List<BeaconRecord> selected = BeaconMarkerSelection.select(candidates, AT_ORIGIN);
        assertEquals(1, selected.size());
        assertEquals(VIEWER, selected.get(0).owner());
        assertTrue(BeaconMarkerSelection.select(candidates,
                new BeaconMarkerSelection.Viewer(UUID.randomUUID(), EARTH, OVERWORLD, 0, 64, 0)).isEmpty());
    }

    @Test
    void inactiveOtherBodyAndOtherLevelRecordsAreFiltered() {
        BeaconRecord visible = beacon(VIEWER, EARTH, OVERWORLD, 9, true);
        List<BeaconRecord> candidates = List.of(
                beacon(VIEWER, EARTH, OVERWORLD, 1, false),
                beacon(VIEWER, MOON, OVERWORLD, 2, true),
                beacon(VIEWER, EARTH, SPACE, 3, true),
                visible);
        assertEquals(List.of(visible), BeaconMarkerSelection.select(candidates, AT_ORIGIN));
        assertEquals(List.of(), BeaconMarkerSelection.select(candidates,
                new BeaconMarkerSelection.Viewer(VIEWER, MOON, SPACE, 0, 64, 0)));
    }

    @Test
    void theNearestEightComeFirstAndTiesFollowTheCanonicalUuid() {
        List<BeaconRecord> candidates = new ArrayList<>();
        for (int i = 1; i <= 12; i++) {
            candidates.add(beacon(VIEWER, EARTH, OVERWORLD, i * 10, true));
        }
        UUID low = UUID.fromString("00000000-0000-0000-0000-000000000001");
        UUID high = UUID.fromString("ffffffff-0000-0000-0000-000000000001");
        BeaconRecord tieHigh = beacon(high, VIEWER, EARTH, OVERWORLD, -5, true);
        BeaconRecord tieLow = beacon(low, VIEWER, EARTH, OVERWORLD, 5, true);
        candidates.add(tieHigh);
        candidates.add(tieLow);
        Collections.shuffle(candidates, new Random(7));
        List<BeaconRecord> selected = BeaconMarkerSelection.select(candidates, AT_ORIGIN);
        assertEquals(BeaconLimits.MAX_MARKERS, selected.size());
        assertEquals(tieLow, selected.get(0));
        assertEquals(tieHigh, selected.get(1));
        for (int i = 2; i < selected.size(); i++) {
            assertEquals((i - 1) * 10, selected.get(i).x());
        }
        List<BeaconRecord> reversed = new ArrayList<>(candidates);
        Collections.reverse(reversed);
        assertEquals(selected, BeaconMarkerSelection.select(reversed, AT_ORIGIN));
    }

    @Test
    void distanceIsExactAtTheWorldCorners() {
        BeaconRecord far = new BeaconRecord(UUID.randomUUID(), VIEWER, EARTH, OVERWORLD, 30_000_000, 2_031, 30_000_000,
                0, true, "");
        BeaconRecord near = new BeaconRecord(UUID.randomUUID(), VIEWER, EARTH, OVERWORLD, -30_000_000, -2_032,
                -30_000_000, 0, true, "");
        BeaconMarkerSelection.Viewer corner = new BeaconMarkerSelection.Viewer(VIEWER, EARTH, OVERWORLD, -30_000_000,
                -2_032, -30_000_000);
        assertEquals(List.of(near, far), BeaconMarkerSelection.select(List.of(far, near), corner));
        assertTrue(far.distanceSquared(-30_000_000, -2_032, -30_000_000) > 0);
    }

    @Test
    void recordsRefuseOutOfBoundsData() {
        UUID id = UUID.randomUUID();
        assertThrows(IllegalArgumentException.class, () -> new BeaconRecord(id, VIEWER, EARTH, OVERWORLD,
                30_000_001, 64, 0, 0, true, ""));
        assertThrows(IllegalArgumentException.class, () -> new BeaconRecord(id, VIEWER, EARTH, OVERWORLD,
                0, 2_032, 0, 0, true, ""));
        assertThrows(IllegalArgumentException.class, () -> new BeaconRecord(id, VIEWER, EARTH, OVERWORLD,
                0, 64, 0, -1, true, ""));
        assertThrows(IllegalArgumentException.class, () -> new BeaconRecord(id, VIEWER, "earth", OVERWORLD,
                0, 64, 0, 0, true, ""));
        assertThrows(IllegalArgumentException.class, () -> new BeaconRecord(id, VIEWER, EARTH, "Minecraft:Overworld",
                0, 64, 0, 0, true, ""));
        assertThrows(IllegalArgumentException.class, () -> new BeaconRecord(id, VIEWER, "a:" + "b".repeat(127),
                OVERWORLD, 0, 64, 0, 0, true, ""));
        assertThrows(IllegalArgumentException.class, () -> new BeaconRecord(id, VIEWER, EARTH, OVERWORLD,
                0, 64, 0, 0, true, "x".repeat(33)));
        assertTrue(BeaconLimits.validId("minecraft:overworld") && BeaconLimits.validId("a.b-c_d:e/f.g"));
        assertFalse(BeaconLimits.validId("a/b:c") || BeaconLimits.validId(":a") || BeaconLimits.validId("a:")
                || BeaconLimits.validId("a:b:c"));
    }

    @Test
    void namesAreBoundedPrintableAndWellFormed() {
        assertTrue(BeaconLimits.validName(""));
        assertTrue(BeaconLimits.validName("中".repeat(32)));
        assertTrue(BeaconLimits.validName("🚀".repeat(16)));
        assertFalse(BeaconLimits.validName("中".repeat(33)));
        assertFalse(BeaconLimits.validName("tab\there"));
        assertFalse(BeaconLimits.validName("§cred"));
        assertFalse(BeaconLimits.validName("lone\uD83D"));
        assertFalse(BeaconLimits.validName("\uDE80lone"));
        assertFalse(BeaconLimits.validName(null));
    }
}
