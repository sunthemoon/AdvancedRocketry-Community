package io.github.sunthemoon.advancedrocketrycommunity.satellite.persistence;

import static io.github.sunthemoon.advancedrocketrycommunity.satellite.persistence.RegistryWriterHarness.*;
import static org.junit.jupiter.api.Assertions.*;

import io.github.sunthemoon.advancedrocketrycommunity.celestial.persistence.CelestialSavedData;
import io.github.sunthemoon.advancedrocketrycommunity.gametest.DiscoveryProgressFixture;
import io.github.sunthemoon.advancedrocketrycommunity.persistence.migration.CheckedSavedDataFile;
import io.github.sunthemoon.advancedrocketrycommunity.testsupport.MinecraftBootstrap;
import io.github.sunthemoon.advancedrocketrycommunity.testsupport.TestDataStorage;
import io.github.sunthemoon.advancedrocketrycommunity.testsupport.UnstartedServer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.NoSuchFileException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;
import net.minecraft.gametest.framework.GameTestServer;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.DimensionDataStorage;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

/** W194: a fixture handoff must relinquish the previous writer's ownership of the shared staging file. */
@Timeout(120)
class DiscoveryProgressFixtureWriterTest {
    @TempDir Path directory;
    @BeforeAll static void bootstrap() { MinecraftBootstrap.initialize(); }

    @Test void installingDiscoveryAwaitsTheOriginalButLeavesItOpenForRestoration() throws Exception {
        var level = level();
        var original = registry(level);
        var satellite = launch(original);
        assertTrue(original.requestWrite(target(), ATOMIC));
        assertTrue(original.writerInFlight(), "An unpublished capture still owns its staging file");
        DiscoveryProgressFixture.beforeDiscovery(level);
        var temporary = registry(level);
        try {
            assertNotSame(original, temporary);
            assertFalse(original.writerBusy(), "Installation left the original writer in flight");
            assertTrue(original.proves(original.watch(satellite)));
            assertTrue(reload(target()).satellite(satellite).isPresent());
        } finally {
            // Also make the red run's cleanup safe: finish the old writer before restoring it.
            original.awaitForTests(level.getServer());
            DiscoveryProgressFixture.afterDiscovery(level);
            original.awaitForTests(level.getServer());
            temporary.close();
            original.close();
        }
    }

    @Test void restoringDiscoveryDrainsAndClosesTheTemporaryWriterBeforeSavingTheOriginal() throws Exception {
        var level = level();
        var original = registry(level);
        var originalSatellite = launch(original);
        original.flush(target());
        DiscoveryProgressFixture.beforeDiscovery(level);
        var temporary = registry(level);
        launch(temporary);
        assertTrue(temporary.requestWrite(target(), ATOMIC));
        // A mutation not in the active capture must also be drained before the fixture is discarded.
        var laterSatellite = launch(temporary);
        var later = temporary.watch(laterSatellite);
        try {
            DiscoveryProgressFixture.afterDiscovery(level);
            assertSame(original, SatelliteMissionSavedData.get(level.getServer()));
            assertFalse(temporary.writerBusy(), "Restoration left a competing writer in flight");
            assertTrue(temporary.proves(later), "The final fixture mutation was not drained");
            assertFalse(temporary.requestWrite(target(), ATOMIC), "The retired writer can still replace the file");
            original.awaitForTests(level.getServer());
            var restored = reload(target());
            assertTrue(restored.satellite(originalSatellite).isPresent());
            assertTrue(restored.satellite(laterSatellite).isEmpty());
            // Restoration must not close the original: another ordinary save still works.
            var next = launch(original);
            original.flush(target());
            assertTrue(reload(target()).satellite(next).isPresent());
        } finally {
            temporary.close();
            original.close();
        }
    }

    @Test void competingRootsCanMoveThePendingFileButAFailedCaptureDoesNotAcknowledgeAndCanRetry() {
        var server = live();
        var first = RegistryWriterHarness.registry(server);
        var second = RegistryWriterHarness.registry(server);
        var baseline = launch(first);
        write(server, first, target(), ATOMIC);
        var acknowledged = first.acknowledgement().orElseThrow();
        var changed = launch(first);
        var watch = first.watch(changed);
        var entered = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        var missing = new AtomicReference<NoSuchFileException>();
        var held = ATOMIC.withCommitter((pending, file) -> {
            entered.countDown();
            await(release);
            try { CheckedSavedDataFile.atomicMove(pending, file); }
            catch (NoSuchFileException failure) { missing.set(failure); throw failure; }
        });
        try {
            assertTrue(first.requestWrite(target(), held));
            pump(server, first, () -> entered.getCount() == 0);
            var competitor = launch(second);
            write(server, second, target(), ATOMIC);
            assertTrue(reload(target()).satellite(competitor).isPresent());
            assertFalse(Files.exists(target().resolveSibling(REGISTRY.fileName() + ".arce-pending")));
            release.countDown();
            pump(server, first, () -> !first.writerBusy());
            assertNotNull(missing.get(), "The competing writer did not consume the first writer's staging name");
            assertEquals(SatelliteRegistryProof.Outcome.FAILED, first.writerForTests().lastOutcome().orElseThrow());
            assertEquals(acknowledged, first.acknowledgement().orElseThrow());
            assertEquals(acknowledged.epoch(), first.saveEpoch());
            assertTrue(first.isDirty());
            assertTrue(first.flushPending());
            assertFalse(first.proves(watch));
            write(server, first, target(), ATOMIC);
            assertTrue(first.proves(watch));
            assertFalse(first.isDirty());
            var recovered = reload(target());
            assertTrue(recovered.satellite(baseline).isPresent());
            assertTrue(recovered.satellite(changed).isPresent());
            assertTrue(recovered.satellite(competitor).isEmpty(), "Competing roots overwrite whole registries");
        } finally {
            release.countDown();
            first.close();
            second.close();
        }
    }

    private Path target() { return directory.resolve(REGISTRY.fileName()); }

    private static SatelliteMissionSavedData registry(ServerLevel level) {
        var data = SatelliteMissionSavedData.get(level.getServer());
        // No bay deployment exists in this storage-only fixture; avoid unrelated Forge capability bootstrap.
        data.deploymentFence((satellite, removal) -> false);
        return data;
    }

    private RegistryLevel level() throws Exception {
        var server = UnstartedServer.allocate(GameTestServer.class);
        UnstartedServer.set(MinecraftServer.class, server, "serverThread", Thread.currentThread());
        UnstartedServer.set(MinecraftServer.class, server, "running", true);
        var level = UnstartedServer.allocate(RegistryLevel.class);
        level.storage = TestDataStorage.over(Files.createDirectories(directory));
        level.storage.set(SatelliteMissionSavedData.DATA_NAME, SatelliteMissionSavedData.create(0));
        level.storage.set(CelestialSavedData.DATA_NAME, CelestialSavedData.create());
        UnstartedServer.set(ServerLevel.class, level, "server", server);
        UnstartedServer.set(MinecraftServer.class, server, "levels", Map.of(Level.OVERWORLD, level));
        return level;
    }

    private static final class RegistryLevel extends ServerLevel {
        DimensionDataStorage storage;
        RegistryLevel() { super(null, null, null, null, null, null, null, false, 0, List.of(), false, null); }
        @Override public DimensionDataStorage getDataStorage() { return storage; }
        @Override public long getGameTime() { return 0; }
    }
}
