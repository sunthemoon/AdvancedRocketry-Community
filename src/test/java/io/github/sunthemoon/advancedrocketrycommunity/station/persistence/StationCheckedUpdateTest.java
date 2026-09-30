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
import io.github.sunthemoon.advancedrocketrycommunity.station.persistence.StationRegistrySavedData.CheckedUpdate;
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

final class StationCheckedUpdateTest {
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
        assertEquals(CheckedUpdate.COMMITTED, data.checkedExpand(file, station, (from, to) -> {
            // The live registry must still be the observed state while the candidate is committed.
            assertEquals(station, data.find(station.stationId()).orElseThrow());
            assertEquals(station, data.findAt(station.cell().centerX(), station.cell().centerZ()).orElseThrow());
            CheckedSavedDataFile.atomicMove(from, to);
            assertEquals(station, data.find(station.stationId()).orElseThrow());
        }));
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
        assertEquals(CheckedUpdate.STALE, data.checkedExpand(file, observed, CheckedSavedDataFile::atomicMove));
        assertArrayEquals(original, Files.readAllBytes(file));

        StationState current = data.find(station.stationId()).orElseThrow();
        assertEquals(CheckedUpdate.COMMITTED, data.checkedExpand(file, current, CheckedSavedDataFile::atomicMove));
        byte[] expanded = Files.readAllBytes(file);
        StationState published = data.find(station.stationId()).orElseThrow();
        assertEquals(CheckedUpdate.UNCHANGED,
                data.checkedExpand(file, published, CheckedSavedDataFile::atomicMove));
        assertEquals(CheckedUpdate.STALE, data.checkedExpand(file, current, CheckedSavedDataFile::atomicMove));
        assertArrayEquals(expanded, Files.readAllBytes(file));
    }

    @Test
    void failureBeforeReplacementLeavesAuthorityAndLaterSavesUnexpanded() throws Exception {
        Path wrongName = root.resolve("stations-copy.dat");
        assertEquals(CheckedUpdate.WRITE_FAILED, data.checkedExpand(wrongName, station, (from, to) -> {
            throw new AssertionError("Replacement must not be attempted");
        }));
        assertEquals(station, data.find(station.stationId()).orElseThrow());
        assertFalse(data.isDirty());
        assertFalse(data.updatesQuarantined());
        assertFalse(Files.exists(wrongName));
    }

    @Test
    void refusedAtomicReplacementRetainsTheOldFileAndAuthority() throws Exception {
        byte[] original = Files.readAllBytes(file);
        assertEquals(CheckedUpdate.WRITE_FAILED, data.checkedExpand(file, station, (from, to) -> {
            throw new AtomicMoveNotSupportedException(from.toString(), to.toString(), "injected");
        }));
        assertArrayEquals(original, Files.readAllBytes(file));
        assertEquals(station, data.find(station.stationId()).orElseThrow());
        assertFalse(data.updatesQuarantined());
        assertTrue(data.isDirty(), "Ordinary saves must reassert the acknowledged authority");
        assertFalse(Files.exists(pending()));
        // An ordinary autosave cannot commit the rejected growth.
        StationRegistrySavedData reloaded = StationRegistrySavedData.load(data.save(new CompoundTag()));
        assertEquals(StationLimits.REGION_SIZE, reloaded.find(station.stationId()).orElseThrow().region().width());
        // The request can be retried once storage works again.
        assertEquals(CheckedUpdate.COMMITTED, data.checkedExpand(file, station, CheckedSavedDataFile::atomicMove));
    }

    @Test
    void failedReplacementThatLeavesNoFileKeepsAuthorityAndSchedulesRewrite() {
        assertEquals(CheckedUpdate.WRITE_FAILED, data.checkedExpand(file, station, (from, to) -> {
            Files.delete(to);
            throw new java.io.IOException("injected missing target");
        }));
        assertEquals(station, data.find(station.stationId()).orElseThrow());
        assertFalse(data.updatesQuarantined());
        assertTrue(data.isDirty(), "The acknowledged authority must be written again");
        assertFalse(Files.exists(file));
    }

    @Test
    void replacementReportedAsFailedButVerifiedOnDiskIsPublished() {
        assertEquals(CheckedUpdate.COMMITTED, data.checkedExpand(file, station, (from, to) -> {
            CheckedSavedDataFile.atomicMove(from, to);
            throw new java.io.IOException("injected post-replacement error");
        }));
        assertEquals(station.withExpandedRegion(), data.find(station.stationId()).orElseThrow());
        assertEquals(data.save(new CompoundTag()), CheckedSavedDataFile.readPayload(file, TYPE).orElseThrow());
    }

    @Test
    void unknownReplacementOutcomeQuarantinesFurtherExpansion() throws Exception {
        assertEquals(CheckedUpdate.OUTCOME_UNKNOWN, data.checkedExpand(file, station, (from, to) -> {
            Files.write(to, new byte[]{1, 2, 3});
            throw new java.io.IOException("injected torn replacement");
        }));
        assertTrue(data.updatesQuarantined());
        assertEquals(station, data.find(station.stationId()).orElseThrow());
        assertTrue(data.isDirty(), "Ordinary saves must reassert the acknowledged authority");
        assertEquals(CheckedUpdate.UNAVAILABLE,
                data.checkedExpand(file, neighbor, CheckedSavedDataFile::atomicMove));
        assertTrue(data.operational(), "Existing station access continues from the acknowledged authority");
    }

    /** WARP review R2 (M22): warp cores are not credited once checked updates are quarantined. */
    @Test
    void aQuarantinedRegistryRefusesWarpEnergy() throws Exception {
        assertTrue(data.acceptsWarpEnergy() && data.updatesAvailable());
        assertEquals(CheckedUpdate.OUTCOME_UNKNOWN, data.checkedExpand(file, station, (from, to) -> {
            Files.write(to, new byte[]{4, 5, 6});
            throw new java.io.IOException("injected torn replacement");
        }));
        assertTrue(data.operational() && data.updatesQuarantined());
        assertFalse(data.acceptsWarpEnergy(), "A quarantined registry accepted warp energy");
        assertFalse(data.updatesAvailable(), "A quarantined registry reported checked updates as available");
    }

    /** ADR-047 / final review B3: a quarantined registry still resolves the station sky context. */
    @Test
    void aQuarantinedRegistryStillResolvesTheSkyContext() throws Exception {
        int x = station.cell().centerX();
        int z = station.cell().centerZ();
        assertEquals(java.util.Optional.of(station.orbitBody()),
                io.github.sunthemoon.advancedrocketrycommunity.station.orbit.StationSkyContextService.contextFor(
                        data, true, x, z));
        assertEquals(CheckedUpdate.OUTCOME_UNKNOWN, data.checkedExpand(file, station, (from, to) -> {
            Files.write(to, new byte[]{7, 8, 9});
            throw new java.io.IOException("injected torn replacement");
        }));
        assertTrue(data.updatesQuarantined());
        assertEquals(java.util.Optional.of(station.orbitBody()),
                io.github.sunthemoon.advancedrocketrycommunity.station.orbit.StationSkyContextService.contextFor(
                        data, true, x, z), "A quarantined registry must still resolve reads");
        assertTrue(io.github.sunthemoon.advancedrocketrycommunity.station.orbit.StationSkyContextService.contextFor(
                data, false, x, z).isEmpty(), "Outside Space there is no context");
    }

    @Test
    void gravityUsesTheSameCheckedCommitAndKeepsEverythingElse() throws Exception {
        assertEquals(CheckedUpdate.COMMITTED, data.checkedSetGravity(file, station, 400, (from, to) -> {
            assertEquals(station, data.find(station.stationId()).orElseThrow());
            CheckedSavedDataFile.atomicMove(from, to);
        }));
        StationState published = data.find(station.stationId()).orElseThrow();
        assertEquals(400, published.environment().gravityMilli());
        assertEquals(station.environment().solarAngleMilliDegrees(), published.environment().solarAngleMilliDegrees());
        assertTrue(published.sameAuthorityAs(station) && published.region().equals(station.region()));
        assertEquals(published, StationRegistrySavedData.load(CheckedSavedDataFile.readPayload(file, TYPE)
                .orElseThrow()).find(station.stationId()).orElseThrow());
        assertEquals(CheckedUpdate.UNCHANGED, data.checkedSetGravity(file, published, 400,
                CheckedSavedDataFile::atomicMove));
        assertEquals(CheckedUpdate.STALE, data.checkedSetGravity(file, station, 0, CheckedSavedDataFile::atomicMove));

        byte[] committed = Files.readAllBytes(file);
        assertEquals(CheckedUpdate.WRITE_FAILED, data.checkedSetGravity(file, published, 1000, (from, to) -> {
            throw new AtomicMoveNotSupportedException(from.toString(), to.toString(), "injected");
        }));
        assertArrayEquals(committed, Files.readAllBytes(file));
        assertEquals(400, data.find(station.stationId()).orElseThrow().environment().gravityMilli());
        assertThrows(IllegalArgumentException.class, () -> station.withGravityMilli(10_001));
    }

    @Test
    void blockedRegistryCannotExpand() {
        CompoundTag future = new CompoundTag();
        future.putInt("schema_version", 99);
        StationRegistrySavedData blocked = StationRegistrySavedData.load(future);
        assertEquals(CheckedUpdate.UNAVAILABLE,
                blocked.checkedExpand(file, station, CheckedSavedDataFile::atomicMove));
    }

    @Test
    void modelPublishesOnlyRegionOrEnvironmentUpdatesOfTheObservedState() {
        StationRegistryModel model = new StationRegistryModel();
        model.restoreStation(station);
        assertThrows(IllegalArgumentException.class, () -> model.replaceChecked(station, neighbor));
        StationState changed = station.withMember(UUID.randomUUID());
        assertThrows(IllegalStateException.class,
                () -> model.replaceChecked(changed, changed.withExpandedRegion()));
        assertEquals(station.withExpandedRegion(), model.replaceChecked(station, station.withExpandedRegion()));
        StationState expanded = station.withExpandedRegion();
        assertEquals(expanded, expanded.withExpandedRegion());
        assertThrows(IllegalArgumentException.class, () -> model.replaceChecked(expanded, expanded));
        // Checked updates cannot carry ownership, team or identity changes.
        assertThrows(IllegalArgumentException.class,
                () -> model.replaceChecked(expanded, expanded.transferOwnership(UUID.randomUUID())));
        assertThrows(IllegalArgumentException.class,
                () -> model.replaceChecked(expanded, expanded.withMember(UUID.randomUUID()).withGravityMilli(1)));
        assertEquals(expanded.withGravityMilli(250), model.replaceChecked(expanded, expanded.withGravityMilli(250)));
    }

    @Test
    void onlyGrowthOrAGravityOnlyChangeIsACheckedUpdate() throws Exception {
        StationState expanded = station.withExpandedRegion();
        assertTrue(expanded.isCheckedUpdateOf(station));
        assertTrue(station.withGravityMilli(300).isCheckedUpdateOf(station));
        assertFalse(station.isCheckedUpdateOf(expanded), "Shrinking back to 512 is not allowed");
        assertFalse(expanded.withGravityMilli(300).isCheckedUpdateOf(station), "Growth must not also change gravity");
        assertFalse(environment(station, 0, false, 270_000).isCheckedUpdateOf(station), "Vacuum is not settable");
        assertFalse(environment(station, 0, true, 90_000).isCheckedUpdateOf(station), "Sun angle is not settable");
        assertFalse(station.isCheckedUpdateOf(station));
        StationRegistryModel model = new StationRegistryModel();
        model.restoreStation(expanded);
        assertThrows(IllegalArgumentException.class, () -> model.replaceChecked(expanded, station));
        assertThrows(IllegalArgumentException.class,
                () -> model.replaceChecked(expanded, environment(expanded, 9_999, false, 0)));
        // A disallowed transition reaching the checked path is refused before any write.
        byte[] before = Files.readAllBytes(file);
        StationRegistrySavedData growing = data;
        assertThrows(IllegalArgumentException.class, () -> growing.checkedReplace(file, station,
                environment(station, 0, false, 270_000), (from, to) -> {
                    throw new AssertionError("Replacement must not be attempted");
                }));
        assertArrayEquals(before, Files.readAllBytes(file));
        assertEquals(station, data.find(station.stationId()).orElseThrow());
        // Gravity outside the stored bound is refused before any write.
        assertThrows(IllegalArgumentException.class, () -> data.checkedSetGravity(file, station, 10_001,
                CheckedSavedDataFile::atomicMove));
    }

    private static StationState environment(StationState state, int gravityMilli, boolean vacuum, int angle) {
        return new StationState(state.schemaVersion(), state.stationId(), state.ownerId(), state.name(), state.cell(),
                state.region(), state.landingPad(), state.orbitBody(), state.createdAtGameTime(),
                new io.github.sunthemoon.advancedrocketrycommunity.station.model.StationEnvironmentProfile(
                        gravityMilli, vacuum, angle), state.members(), state.invitations());
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
