package io.github.sunthemoon.advancedrocketrycommunity.satellite.service;

import static org.junit.jupiter.api.Assertions.*;

import io.github.sunthemoon.advancedrocketrycommunity.ModIdentity;
import io.github.sunthemoon.advancedrocketrycommunity.persistence.migration.ManagedSavedDataType;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.content.SatelliteIdentity;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.mission.SatelliteOperationCode;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.model.SatelliteDefinition;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.persistence.SatelliteMissionSavedData;
import io.github.sunthemoon.advancedrocketrycommunity.testsupport.MinecraftBootstrap;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** ADR-074 item 7: the bounded pending-action table, its admission, locks, coverage, holds and discard. */
class TerminalActionTableTest {
    /** Created after the bootstrap: touching Level earlier would initialise the registries unbootstrapped. */
    private static ResourceKey<Level> OVERWORLD, OTHER;
    @TempDir Path directory;
    @BeforeAll static void bootstrap() {
        MinecraftBootstrap.initialize();
        OVERWORLD = Level.OVERWORLD;
        OTHER = ResourceKey.create(Registries.DIMENSION, ModIdentity.id("w129c_other"));
    }

    @Test void oneEntryPerTerminalOnePerSatelliteAndAtMost256RefuseBeforeAnyMutation() {
        var table = new TerminalActionTable();
        var data = SatelliteMissionSavedData.create(0);
        var first = identity();
        var here = key(OVERWORLD, 0);
        assertNull(table.admission(here, first.satelliteId()));
        var entry = table.launch(here, first, watch(data, first), SatelliteOperationCode.SUCCESS, UUID.randomUUID());
        assertEquals(SatelliteOperationCode.REGISTRY_PENDING, table.admission(here, UUID.randomUUID()),
                "A second action at the same terminal is refused");
        assertEquals(SatelliteOperationCode.REGISTRY_BUSY, table.admission(key(OTHER, 0), first.satelliteId()),
                "The same satellite at another terminal is refused");
        assertEquals(here, new TerminalActionTable.Key(OVERWORLD, new BlockPos.MutableBlockPos(0, 64, 0)));
        for (int index = 1; index < TerminalActionTable.MAX_ENTRIES; index++) {
            var other = identity();
            table.data(key(OVERWORLD, index), other, data.token(), SatelliteOperationCode.SUCCESS, UUID.randomUUID());
        }
        assertEquals(TerminalActionTable.MAX_ENTRIES, table.size());
        assertEquals(SatelliteOperationCode.REGISTRY_BUSY, table.admission(key(OTHER, 999), UUID.randomUUID()),
                "A full table answers REGISTRY_BUSY");
        assertThrows(IllegalStateException.class, () -> table.data(key(OTHER, 999), identity(), data.token(),
                SatelliteOperationCode.SUCCESS, UUID.randomUUID()), "An unadmitted entry cannot be added");
        table.completed(entry);
        assertNull(table.admission(key(OTHER, 0), first.satelliteId()), "Completion releases the satellite index");
        assertNull(table.admission(here, UUID.randomUUID()));
    }

    @Test void onlyLaunchAndDecommissionLockAndCoverageAloneReleasesNothing() throws Exception {
        var table = new TerminalActionTable();
        var data = SatelliteMissionSavedData.create(0);
        var launch = identity(); var decommission = identity(); var dataAction = identity();
        var launchEntry = table.launch(key(OVERWORLD, 1), launch, watch(data, launch), SatelliteOperationCode.SUCCESS, UUID.randomUUID());
        var removal = table.decommission(key(OVERWORLD, 2), decommission, data.token(), SatelliteOperationCode.SUCCESS, UUID.randomUUID());
        var action = table.data(key(OVERWORLD, 3), dataAction, data.token(), SatelliteOperationCode.PENDING_DISCOVERY, UUID.randomUUID());
        assertTrue(table.locked(key(OVERWORLD, 1))); assertTrue(table.locked(key(OVERWORLD, 2)));
        assertFalse(table.locked(key(OVERWORLD, 3)), "A data action locks no item");
        assertFalse(launchEntry.covered(data)); assertFalse(removal.covered(data)); assertFalse(action.covered(data));
        data.flush(directory.resolve(ManagedSavedDataType.SATELLITE_MISSIONS.fileName()));
        assertTrue(launchEntry.covered(data)); assertTrue(removal.covered(data)); assertTrue(action.covered(data));
        assertEquals(3, table.size(), "Coverage is not completion");
        assertTrue(table.hold(launchEntry)); assertFalse(table.hold(launchEntry), "One diagnostic per hold");
        assertTrue(launchEntry.held()); assertTrue(table.locked(key(OVERWORLD, 1)), "A hold keeps the lock");
        assertEquals(SatelliteOperationCode.PENDING_DISCOVERY, action.result(), "The stored RAM result is kept");
    }

    @Test void rotationReachesEveryEntryAndDiscardDropsThemAll() {
        var table = new TerminalActionTable();
        var data = SatelliteMissionSavedData.create(0);
        for (int index = 0; index < 40; index++) {
            table.data(key(OVERWORLD, index), identity(), data.token(), SatelliteOperationCode.SUCCESS, UUID.randomUUID());
        }
        var firsts = new HashSet<TerminalActionTable.Entry>();
        for (int round = 0; round < 40; round++) { firsts.add(table.rotation().get(0)); }
        assertEquals(40, firsts.size(), "Every entry leads one rotation");
        table.discard();
        assertEquals(0, table.size()); assertTrue(table.rotation().isEmpty());
        assertFalse(table.locked(key(OVERWORLD, 0)));
    }

    private static TerminalActionTable.Key key(ResourceKey<Level> level, int index) {
        return new TerminalActionTable.Key(level, new BlockPos(index, 64, 0));
    }

    private static SatelliteIdentity identity() {
        return new SatelliteIdentity(UUID.randomUUID(), UUID.randomUUID(), ModIdentity.id("data_satellite"));
    }

    private static io.github.sunthemoon.advancedrocketrycommunity.satellite.persistence.SatelliteRegistryProof.Watch watch(
            SatelliteMissionSavedData data, SatelliteIdentity identity) {
        var definition = new SatelliteDefinition(1, ModIdentity.id("data_satellite"), 200, 120, 100,
                List.of(ModIdentity.id("earth")));
        assertTrue(data.launch(identity.satelliteId(), SatelliteManager.initialMissionId(identity.satelliteId()),
                identity.ownerId(), definition, ModIdentity.id("earth"), 0, false).success());
        return data.watch(identity.satelliteId());
    }
}
