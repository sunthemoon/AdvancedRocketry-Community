package io.github.sunthemoon.advancedrocketrycommunity.satellite.persistence;

import static io.github.sunthemoon.advancedrocketrycommunity.satellite.persistence.RegistryWriterHarness.*;
import static org.junit.jupiter.api.Assertions.*;

import io.github.sunthemoon.advancedrocketrycommunity.persistence.CheckedRootWriter;
import io.github.sunthemoon.advancedrocketrycommunity.persistence.migration.CheckedSavedDataFile;
import io.github.sunthemoon.advancedrocketrycommunity.persistence.migration.CheckedSavedDataPolicy;
import io.github.sunthemoon.advancedrocketrycommunity.testsupport.MinecraftBootstrap;
import java.io.IOException;
import java.nio.file.Path;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

/**
 * W129c: an owner-thread await on a GameTest or release-test server runs the writer's cadence on a millisecond clock.
 * The server's later ticks must still advance that cadence one tick each. A write that fails at the end of a long
 * await starts its {@value CheckedRootWriter#BACKOFF_TICKS}-tick backoff then, and a later request starts after that
 * many server ticks, not after the tick count has first caught up with the await's milliseconds.
 */
@Timeout(60)
final class SatelliteRegistryAwaitClockTest {
    private static final long SLOW_MILLIS = 400;

    @TempDir Path directory;
    @BeforeAll static void bootstrap() { MinecraftBootstrap.initialize(); }

    @Test void aBackoffThatStartsAtTheEndOfAnAwaitEndsAfterItsServerTicks() {
        var server = live(); var data = registry(server);
        Path file = directory.resolve(REGISTRY.fileName());
        var slowFailure = new CheckedSavedDataPolicy(REGISTRY, (staged, target) -> {
            try { Thread.sleep(SLOW_MILLIS); } catch (InterruptedException stop) { Thread.currentThread().interrupt(); }
            throw new IOException("W129c slow attempt failed before replacement");
        }, CheckedSavedDataFile::readPayload);
        System.setProperty(SatelliteRegistryTestAwait.PROPERTY, "true");
        try {
            at(server, 1_000);
            launch(data);
            assertTrue(data.requestWrite(file, slowFailure), "The first request was refused");
            data.awaitForTests(server); // the failure is published, and its backoff begins, at the end of the await
            assertFalse(data.writerBusy());
            assertTrue(data.requestWrite(file, ATOMIC), "The request after the await was refused");
            int ticks = 0;
            for (; ticks < CheckedRootWriter.BACKOFF_TICKS * 3 / 4; ticks++) { tick(server, data); }
            assertTrue(data.writerBusy() && !data.writerInFlight(), "The request started inside the failure backoff");
            for (; ticks <= CheckedRootWriter.BACKOFF_TICKS; ticks++) { tick(server, data); }
            assertFalse(data.writerBusy() && !data.writerInFlight(),
                    "Server ticks after an await did not advance the backoff; the request is still waiting to start");
            pump(server, data, () -> !data.writerBusy());
            assertEquals(data.mutationSequence(), data.acknowledgement().orElseThrow().sequence());
        } finally {
            System.clearProperty(SatelliteRegistryTestAwait.PROPERTY);
            data.close();
        }
    }

    private static void tick(net.minecraft.server.MinecraftServer server, SatelliteMissionSavedData data) {
        at(server, server.getTickCount() + 1);
        data.pollWriter();
    }
}
