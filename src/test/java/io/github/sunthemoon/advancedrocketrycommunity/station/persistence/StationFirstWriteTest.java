package io.github.sunthemoon.advancedrocketrycommunity.station.persistence;

import static org.junit.jupiter.api.Assertions.*;

import io.github.sunthemoon.advancedrocketrycommunity.persistence.migration.CheckedSavedDataFile;
import io.github.sunthemoon.advancedrocketrycommunity.persistence.migration.ManagedRootPresence;
import io.github.sunthemoon.advancedrocketrycommunity.persistence.migration.ManagedSavedDataType;
import io.github.sunthemoon.advancedrocketrycommunity.station.model.StationState;
import io.github.sunthemoon.advancedrocketrycommunity.testsupport.MinecraftBootstrap;
import java.nio.file.Files;
import java.nio.file.Path;
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
}
