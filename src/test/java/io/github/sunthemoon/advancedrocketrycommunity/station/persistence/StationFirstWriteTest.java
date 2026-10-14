package io.github.sunthemoon.advancedrocketrycommunity.station.persistence;

import static org.junit.jupiter.api.Assertions.*;

import io.github.sunthemoon.advancedrocketrycommunity.persistence.migration.CheckedSavedDataFile;
import io.github.sunthemoon.advancedrocketrycommunity.persistence.migration.ManagedRootPresence;
import io.github.sunthemoon.advancedrocketrycommunity.persistence.migration.ManagedRootSupplier;
import io.github.sunthemoon.advancedrocketrycommunity.persistence.migration.ManagedSavedDataType;
import io.github.sunthemoon.advancedrocketrycommunity.persistence.migration.MigrationDiagnosticId;
import io.github.sunthemoon.advancedrocketrycommunity.persistence.migration.WorldDataMigrationService;
import io.github.sunthemoon.advancedrocketrycommunity.station.model.StationState;
import io.github.sunthemoon.advancedrocketrycommunity.testsupport.MinecraftBootstrap;
import io.github.sunthemoon.advancedrocketrycommunity.testsupport.TestDataStorage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class StationFirstWriteTest {
    @TempDir Path root;
    @BeforeAll static void bootstrap() { MinecraftBootstrap.initialize(); }

    @Test void failedInitialOrdinarySaveKeepsTheGuardForTheNextOrdinarySave() throws Exception {
        var data = fresh();
        seed(data);
        failInitialSave(data);
        Files.createDirectory(target().getParent());
        Files.writeString(target(), "foreign station registry");
        data.setDirty();
        data.save(target().toFile());
        assertEquals("foreign station registry", Files.readString(target()));
        assertEquals(ManagedRootPresence.UNREADABLE, data.presence());
        assertFalse(data.operational());
        assertFalse(data.isDirty());
    }

    @Test void failedInitialOrdinarySaveKeepsTheGuardForTheNextCheckedSave() throws Exception {
        var data = fresh();
        StationState station = seed(data);
        failInitialSave(data);
        Files.createDirectory(target().getParent());
        Files.writeString(target(), "foreign station registry");
        var moves = new AtomicInteger();
        assertEquals(StationRegistrySavedData.CheckedUpdate.WRITE_FAILED, data.checkedExpand(target(), station,
                (staged, file) -> { moves.incrementAndGet(); CheckedSavedDataFile.atomicMove(staged, file); }));
        assertEquals(0, moves.get());
        assertEquals("foreign station registry", Files.readString(target()));
        assertEquals(ManagedRootPresence.UNREADABLE, data.presence());
        assertFalse(data.isDirty());
        assertFalse(Files.exists(target().resolveSibling(target().getFileName() + ".arce-pending")));
    }

    @Test void failedInitialOrdinarySaveCanRetryIfTheTargetRemainsAbsent() throws Exception {
        var data = fresh();
        seed(data);
        failInitialSave(data);
        Files.createDirectory(target().getParent());
        data.setDirty();
        data.save(target().toFile());
        assertTrue(data.operational());
        assertEquals(data.save(new CompoundTag()), CheckedSavedDataFile.readPayload(target(),
                ManagedSavedDataType.STATIONS).orElseThrow());
        data.setDirty();
        data.save(target().toFile());
        assertTrue(data.operational(), "the successful first save establishes session ownership");
    }

    @Test void successfulOrdinarySavePreservesTheLiveClockWithoutRaisingTheCheckedFloor() throws Exception {
        var data = fresh();
        seed(data);
        Files.createDirectory(target().getParent());
        data.advanceOrbitalClock();
        data.advanceOrbitalClock();
        data.save(target().toFile());
        assertFalse(data.isDirty());
        assertEquals(2L, StationRegistrySavedData.load(CheckedSavedDataFile.readPayload(target(),
                ManagedSavedDataType.STATIONS).orElseThrow()).orbitalTick());
        assertEquals(0L, data.orbitalClockFloor(), "ordinary save is not a checked clock barrier");
    }

    /** W44K: the first save fails after every byte is staged, long after an in-place write would have truncated. */
    @Test void aFirstSaveFailingAfterStagingLeavesNoTornTargetAndTheNextSaveOwnsTheRegistry() throws Exception {
        Files.createDirectory(target().getParent());
        var data = fresh();
        StationState first = seed(data);
        var moves = new AtomicInteger();
        data.save(target().toFile(), (staged, file) -> {
            moves.incrementAndGet();
            assertEquals(data.save(new CompoundTag()), read(staged), "the candidate is completely staged");
            assertFalse(Files.exists(file));
            throw new IOException("injected ENOSPC after the candidate was staged");
        });
        assertEquals(1, moves.get(), "the first save of a proven-absent root is staged");
        assertFalse(Files.exists(target()), "a failed first save leaves no torn target");
        assertFalse(Files.exists(pending()));
        assertEquals(ManagedRootPresence.PROVEN_ABSENT, data.presence());
        assertTrue(data.operational());
        assertFalse(data.isDirty(), "preserve vanilla's ordinary-save dirty handling");

        // IO recovers. The next mutation's save re-proves absence and writes the registry.
        StationState second = seed(data);
        data.save(target().toFile());
        assertEquals(data.save(new CompoundTag()), read(target()));
        assertEquals(ManagedRootPresence.PROVEN_ABSENT, data.presence());
        assertTrue(data.operational());
        StationState third = seed(data);
        data.save(target().toFile(), (staged, file) -> fail("a save after the known commit writes in place"));
        assertEquals(data.save(new CompoundTag()), read(target()));
        assertTrue(data.operational());
        assertFalse(Files.exists(pending()));

        assertRestartsCleanly(first, second, third);
    }

    /** W44K: a first move reported as failed after it happened is still a known commit, through its readback. */
    @Test void aFirstSaveFailingAfterItsMoveIsOwnedThroughItsReadback() throws Exception {
        Files.createDirectory(target().getParent());
        var data = fresh();
        StationState first = seed(data);
        data.save(target().toFile(), (staged, file) -> {
            CheckedSavedDataFile.atomicMove(staged, file);
            throw new IOException("injected failure after the move");
        });
        assertEquals(data.save(new CompoundTag()), read(target()));
        StationState second = seed(data);
        data.save(target().toFile(), (staged, file) -> fail("the confirmed first move established ownership"));
        assertEquals(data.save(new CompoundTag()), read(target()));
        assertEquals(ManagedRootPresence.PROVEN_ABSENT, data.presence());
        assertTrue(data.operational());
        assertRestartsCleanly(first, second);
    }

    /** The pre-start validator accepts the file, and the supplier loads every station of the session. */
    private void assertRestartsCleanly(StationState... stations) {
        assertEquals(MigrationDiagnosticId.DATA_CURRENT, new WorldDataMigrationService().migrate(root).diagnosticId());
        StationRegistrySavedData restarted = ManagedRootSupplier.obtain(TestDataStorage.over(target().getParent()),
                target(), ManagedSavedDataType.STATIONS, StationRegistrySavedData::load,
                StationRegistrySavedData::created, CheckedSavedDataFile::presence);
        assertEquals(ManagedRootPresence.LOADED, restarted.presence());
        assertTrue(restarted.operational());
        for (StationState station : stations) {
            assertEquals(Optional.of(station), restarted.find(station.stationId()));
        }
    }

    private static CompoundTag read(Path file) {
        return CheckedSavedDataFile.readPayload(file, ManagedSavedDataType.STATIONS).orElseThrow();
    }

    private void failInitialSave(StationRegistrySavedData data) {
        data.save(target().toFile()); // The parent does not exist, so compressed IO demonstrably fails.
        assertFalse(Files.exists(target()));
        assertFalse(data.isDirty(), "preserve vanilla's ordinary-save dirty handling");
        assertEquals(ManagedRootPresence.PROVEN_ABSENT, data.presence());
    }

    private static StationRegistrySavedData fresh() {
        return StationRegistrySavedData.created(ManagedRootPresence.PROVEN_ABSENT);
    }

    private static StationState seed(StationRegistrySavedData data) {
        UUID id = UUID.randomUUID();
        data.reserve(id, UUID.randomUUID(), "First write", new ResourceLocation("advancedrocketrycommunity:earth"), 0);
        return data.commit(id);
    }

    private Path target() { return root.resolve("data").resolve(ManagedSavedDataType.STATIONS.fileName()); }

    private Path pending() { return target().resolveSibling(target().getFileName() + ".arce-pending"); }
}
