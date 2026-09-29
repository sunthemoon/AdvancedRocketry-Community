package io.github.sunthemoon.advancedrocketrycommunity.station.model;

import static org.junit.jupiter.api.Assertions.*;

import io.github.sunthemoon.advancedrocketrycommunity.ModIdentity;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

final class StationRegionBoundaryTest {
    @Test
    void bothSizesKeepInclusiveIndexedEdgesAtNegativeAndMaximumCells() {
        for (int x : new int[]{-1_000_000, -2, -1, 0, 1, 2, 1_000_000}) {
            for (int z : new int[]{-1_000_000, -1, 0, 1, 1_000_000}) {
                for (int width : new int[]{512, 768}) {
                    StationGridCell cell = new StationGridCell(x, z);
                    StationState station = state(1, cell, width);
                    StationRegistryModel registry = new StationRegistryModel();
                    registry.restoreStation(station);
                    StationRegion region = station.region();
                    assertEquals(width, region.width());
                    assertEquals(cell.landingPad(), station.landingPad());
                    for (int px : new int[]{region.minimumX(), cell.centerX(), region.maximumX()}) {
                        for (int pz : new int[]{region.minimumZ(), cell.centerZ(), region.maximumZ()}) {
                            assertEquals(station, registry.findAt(px, pz).orElseThrow());
                        }
                    }
                    assertTrue(registry.findAt(region.minimumX() - 1, cell.centerZ()).isEmpty());
                    assertTrue(registry.findAt(region.maximumX() + 1, cell.centerZ()).isEmpty());
                    assertTrue(registry.findAt(cell.centerX(), region.minimumZ() - 1).isEmpty());
                    assertTrue(registry.findAt(cell.centerX(), region.maximumZ() + 1).isEmpty());
                    assertTrue(registry.findAt(Integer.MIN_VALUE, Integer.MAX_VALUE).isEmpty());
                }
            }
        }
    }

    @Test
    void adjacentExpandedCellsLeaveGapAndRestoreRejectsDuplicateIdentityOrCell() {
        for (int x = -3; x <= 3; x++) {
            StationRegistryModel registry = new StationRegistryModel();
            var first = state(1, new StationGridCell(x, -1), 768);
            var second = state(2, new StationGridCell(x + 1, -1), 768);
            registry.restoreStation(first);
            registry.restoreStation(second);
            assertFalse(first.region().overlaps(second.region()));
            assertEquals(256, second.region().minimumX() - first.region().maximumX() - 1);
            for (int column = first.region().maximumX() + 1; column < second.region().minimumX(); column++) {
                assertTrue(registry.findAt(column, first.cell().centerZ()).isEmpty());
            }
            assertThrows(IllegalArgumentException.class, () -> registry.restoreStation(first));
            assertThrows(IllegalArgumentException.class, () -> registry.restoreStation(state(3, first.cell(), 512)));
            assertThrows(IllegalArgumentException.class, () -> registry.restoreReservation(new StationReservation(
                    new UUID(150, 4), new UUID(151, 4), "Conflict", first.cell(), ModIdentity.id("moon"), 1)));
        }
    }

    @Test
    void invalidWidthOffsetPadAndCoordinateCannotBeStored() {
        var cell = new StationGridCell(0, 0);
        for (int width : new int[]{0, 511, 513, 767, 769, 1024, Integer.MAX_VALUE}) {
            assertThrows(IllegalArgumentException.class, () -> cell.region(width));
        }
        assertThrows(IllegalArgumentException.class, () -> new StationRegion(-384, -256, 383, 255));
        assertThrows(IllegalArgumentException.class, () -> new StationGridCell(1_000_001, 0));
        assertThrows(IllegalArgumentException.class, () -> new StationGridCell(Integer.MIN_VALUE, 0));
        assertThrows(IllegalArgumentException.class, () -> new StationRegion(Integer.MIN_VALUE, 0, Integer.MAX_VALUE, 767));
        assertThrows(IllegalArgumentException.class, () -> new StationState(2, new UUID(150, 1), new UUID(151, 1),
                "Offset", cell, new StationRegion(-383, -384, 384, 383), cell.landingPad(),
                ModIdentity.id("earth"), 0, StationEnvironmentProfile.BASIC_SPACE, List.of(), List.of()));
        assertThrows(IllegalArgumentException.class, () -> new StationState(2, new UUID(150, 1), new UUID(151, 1),
                "Pad", cell, cell.region(768), new StationPosition(0, 129, 0),
                ModIdentity.id("earth"), 0, StationEnvironmentProfile.BASIC_SPACE, List.of(), List.of()));
    }

    @Test
    void failedCommitAtCapacityRetainsReservationUntilAnExistingStationIsDeleted() {
        StationRegistryModel registry = new StationRegistryModel();
        for (int index = 0; index < StationLimits.MAX_STATIONS - 1; index++) {
            registry.restoreStation(state(index, new StationGridCell(index, 0), 512));
        }
        UUID first = new UUID(152, 1);
        UUID second = new UUID(152, 2);
        registry.reserve(first, new UUID(153, 1), "First", ModIdentity.id("earth"), 0);
        var pending = registry.reserve(second, new UUID(153, 2), "Second", ModIdentity.id("earth"), 0);
        registry.commit(first);
        assertThrows(IllegalStateException.class, () -> registry.commit(second));
        assertEquals(StationLimits.MAX_STATIONS, registry.stations().size());
        assertEquals(List.of(pending), registry.reservations());
        registry.delete(new UUID(150, 0));
        assertEquals(second, registry.commit(second).stationId());
        assertEquals(StationLimits.MAX_STATIONS, registry.stations().size());
    }

    @Test
    void fullCommittedRegistryCanRestoreItsSeparatelyBoundedReservations() {
        StationRegistryModel registry = new StationRegistryModel();
        for (int index = 0; index < StationLimits.MAX_STATIONS; index++) {
            registry.restoreStation(state(index, new StationGridCell(index, 0), 512));
        }
        for (int index = 0; index < StationLimits.MAX_RESERVATIONS; index++) {
            registry.restoreReservation(new StationReservation(new UUID(154, index), new UUID(155, index),
                    "Pending", new StationGridCell(index, 1), ModIdentity.id("moon"), 1));
        }
        assertEquals(4096, registry.stations().size());
        assertEquals(64, registry.reservations().size());
        assertThrows(IllegalArgumentException.class, () -> registry.restoreStation(state(4096, new StationGridCell(4096, 0), 512)));
        assertThrows(IllegalArgumentException.class, () -> registry.restoreReservation(new StationReservation(
                new UUID(154, 65), new UUID(155, 65), "Overflow", new StationGridCell(65, 1), ModIdentity.id("moon"), 1)));
    }

    @Test
    void deletingExpandedClaimReusesCellWithInitialSize() {
        StationRegistryModel registry = new StationRegistryModel();
        var old = state(1, new StationGridCell(0, 0), 768);
        registry.restoreStation(old);
        registry.delete(old.stationId());
        var next = registry.reserve(new UUID(156, 1), new UUID(156, 2), "New", ModIdentity.id("earth"), 1);
        var created = registry.commit(next.stationId());
        assertEquals(old.cell(), created.cell());
        assertEquals(512, created.region().width());
        assertTrue(registry.findAt(383, 383).isEmpty());
    }

    private static StationState state(int identity, StationGridCell cell, int width) {
        return new StationState(StationLimits.STATE_SCHEMA_VERSION, new UUID(150, identity), new UUID(151, identity),
                "Boundary", cell, cell.region(width), cell.landingPad(), ModIdentity.id("earth"), 0,
                StationEnvironmentProfile.BASIC_SPACE, List.of(), List.of());
    }
}
