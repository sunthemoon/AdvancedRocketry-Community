package io.github.sunthemoon.advancedrocketrycommunity.station.persistence;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.sunthemoon.advancedrocketrycommunity.ModIdentity;
import io.github.sunthemoon.advancedrocketrycommunity.persistence.migration.CheckedSavedDataFile;
import io.github.sunthemoon.advancedrocketrycommunity.persistence.migration.ManagedSavedDataType;
import io.github.sunthemoon.advancedrocketrycommunity.station.model.StationLimits;
import io.github.sunthemoon.advancedrocketrycommunity.station.model.StationRegistryModel;
import io.github.sunthemoon.advancedrocketrycommunity.station.model.StationState;
import io.github.sunthemoon.advancedrocketrycommunity.station.persistence.StationRegistrySavedData.CheckedExpansion;
import io.github.sunthemoon.advancedrocketrycommunity.testsupport.MinecraftBootstrap;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class StationCheckedExpansionTest {
    private static final ManagedSavedDataType TYPE = ManagedSavedDataType.STATIONS;
    @TempDir Path root;
    private Path file;
    private StationRegistrySavedData data;
    private StationState station;
    private StationState neighbor;

    @BeforeAll
    static void bootstrap() {
        MinecraftBootstrap.initialize();
    }

    @BeforeEach
    void createRegistry() {
        file = root.resolve(TYPE.fileName());
        data = new StationRegistrySavedData();
        station = commit("Grow");
        neighbor = commit("Neighbor");
        station = data.invite(station.stationId(), UUID.randomUUID());
        CheckedSavedDataFile.replace(file, TYPE, () -> data.save(new CompoundTag()));
        data.setDirty(false);
    }

    @Test
    void successReplacesTheFileBeforePublishingOnlyTheExpandedRegion() {
        assertEquals(CheckedExpansion.EXPANDED, data.checkedExpand(file, station, CheckedSavedDataFile::atomicMove));
        StationState published = data.find(station.stationId()).orElseThrow();
        assertEquals(station.withExpandedRegion(), published);
        assertEquals(StationLimits.EXPANDED_REGION_SIZE, published.region().width());
        assertEquals(station.landingPad(), published.landingPad());
        assertEquals(station.invitations(), published.invitations());
        assertEquals(neighbor, data.find(neighbor.stationId()).orElseThrow());
        // A formerly unclaimed gap column now resolves to the station without touching the neighbor.
        int gapX = station.cell().centerX() + StationLimits.REGION_SIZE / 2 + 10;
        assertEquals(published, data.findAt(gapX, station.cell().centerZ()).orElseThrow());

        CompoundTag onDisk = CheckedSavedDataFile.readPayload(file, TYPE).orElseThrow();
        assertEquals(data.save(new CompoundTag()), onDisk);
        StationRegistrySavedData restarted = StationRegistrySavedData.load(onDisk);
        assertTrue(restarted.operational());
        assertEquals(published, restarted.find(station.stationId()).orElseThrow());
        assertFalse(Files.exists(pending()));
    }

    @Test
    void repeatedOrStaleRequestsDoNotWrite() throws Exception {
        byte[] original = Files.readAllBytes(file);
        StationState observed = station;
        data.addMember(station.stationId(), UUID.randomUUID());
        assertEquals(CheckedExpansion.STALE, data.checkedExpand(file, observed, CheckedSavedDataFile::atomicMove));
        assertArrayEquals(original, Files.readAllBytes(file));

        StationState current = data.find(station.stationId()).orElseThrow();
        assertEquals(CheckedExpansion.EXPANDED, data.checkedExpand(file, current, CheckedSavedDataFile::atomicMove));
        byte[] expanded = Files.readAllBytes(file);
        StationState published = data.find(station.stationId()).orElseThrow();
        assertEquals(CheckedExpansion.ALREADY_EXPANDED,
                data.checkedExpand(file, published, CheckedSavedDataFile::atomicMove));
        assertEquals(CheckedExpansion.STALE, data.checkedExpand(file, current, CheckedSavedDataFile::atomicMove));
        assertArrayEquals(expanded, Files.readAllBytes(file));
    }

    @Test
    void failureBeforeReplacementLeavesAuthorityAndLaterSavesUnexpanded() throws Exception {
        Path wrongName = root.resolve("stations-copy.dat");
        assertEquals(CheckedExpansion.WRITE_FAILED, data.checkedExpand(wrongName, station, (from, to) -> {
            throw new AssertionError("Replacement must not be attempted");
        }));
        assertEquals(station, data.find(station.stationId()).orElseThrow());
        assertFalse(data.isDirty());
        assertFalse(data.expansionQuarantined());
        assertFalse(Files.exists(wrongName));
    }

    @Test
    void refusedAtomicReplacementRetainsTheOldFileAndAuthority() throws Exception {
        byte[] original = Files.readAllBytes(file);
        assertEquals(CheckedExpansion.WRITE_FAILED, data.checkedExpand(file, station, (from, to) -> {
            throw new AtomicMoveNotSupportedException(from.toString(), to.toString(), "injected");
        }));
        assertArrayEquals(original, Files.readAllBytes(file));
        assertEquals(station, data.find(station.stationId()).orElseThrow());
        assertFalse(data.expansionQuarantined());
        assertFalse(Files.exists(pending()));
        // An ordinary autosave cannot commit the rejected growth.
        StationRegistrySavedData reloaded = StationRegistrySavedData.load(data.save(new CompoundTag()));
        assertEquals(StationLimits.REGION_SIZE, reloaded.find(station.stationId()).orElseThrow().region().width());
        // The request can be retried once storage works again.
        assertEquals(CheckedExpansion.EXPANDED, data.checkedExpand(file, station, CheckedSavedDataFile::atomicMove));
    }

    @Test
    void replacementReportedAsFailedButVerifiedOnDiskIsPublished() {
        assertEquals(CheckedExpansion.EXPANDED, data.checkedExpand(file, station, (from, to) -> {
            CheckedSavedDataFile.atomicMove(from, to);
            throw new java.io.IOException("injected post-replacement error");
        }));
        assertEquals(station.withExpandedRegion(), data.find(station.stationId()).orElseThrow());
        assertEquals(data.save(new CompoundTag()), CheckedSavedDataFile.readPayload(file, TYPE).orElseThrow());
    }

    @Test
    void unknownReplacementOutcomeQuarantinesFurtherExpansion() throws Exception {
        assertEquals(CheckedExpansion.OUTCOME_UNKNOWN, data.checkedExpand(file, station, (from, to) -> {
            Files.write(to, new byte[]{1, 2, 3});
            throw new java.io.IOException("injected torn replacement");
        }));
        assertTrue(data.expansionQuarantined());
        assertEquals(station, data.find(station.stationId()).orElseThrow());
        assertTrue(data.isDirty(), "Ordinary saves must reassert the acknowledged authority");
        assertEquals(CheckedExpansion.UNAVAILABLE,
                data.checkedExpand(file, neighbor, CheckedSavedDataFile::atomicMove));
        assertTrue(data.operational(), "Existing station access continues from the acknowledged authority");
    }

    @Test
    void blockedRegistryCannotExpand() {
        CompoundTag future = new CompoundTag();
        future.putInt("schema_version", 99);
        StationRegistrySavedData blocked = StationRegistrySavedData.load(future);
        assertEquals(CheckedExpansion.UNAVAILABLE,
                blocked.checkedExpand(file, station, CheckedSavedDataFile::atomicMove));
    }

    @Test
    void modelPublishesOnlyTheExactExpansionOfTheObservedState() {
        StationRegistryModel model = new StationRegistryModel();
        model.restoreStation(station);
        assertThrows(IllegalArgumentException.class, () -> model.replaceExpanded(station, neighbor));
        StationState changed = station.withMember(UUID.randomUUID());
        assertThrows(IllegalStateException.class,
                () -> model.replaceExpanded(changed, changed.withExpandedRegion()));
        assertEquals(station.withExpandedRegion(), model.replaceExpanded(station, station.withExpandedRegion()));
        StationState expanded = station.withExpandedRegion();
        assertEquals(expanded, expanded.withExpandedRegion());
        assertThrows(IllegalArgumentException.class, () -> model.replaceExpanded(expanded, expanded));
    }

    private StationState commit(String name) {
        UUID stationId = UUID.randomUUID();
        data.reserve(stationId, UUID.randomUUID(), name, ModIdentity.id("earth"), 0);
        return data.commit(stationId);
    }

    private Path pending() {
        return root.resolve(TYPE.fileName() + ".arce-pending");
    }
}
