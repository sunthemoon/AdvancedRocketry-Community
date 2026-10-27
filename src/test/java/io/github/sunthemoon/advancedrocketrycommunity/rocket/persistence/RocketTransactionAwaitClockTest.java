package io.github.sunthemoon.advancedrocketrycommunity.rocket.persistence;

import static io.github.sunthemoon.advancedrocketrycommunity.rocket.persistence.RocketTransactionShutdownTest.*;
import static org.junit.jupiter.api.Assertions.*;

import io.github.sunthemoon.advancedrocketrycommunity.persistence.CheckedRootWriter;
import io.github.sunthemoon.advancedrocketrycommunity.persistence.migration.CheckedSavedDataFile;
import io.github.sunthemoon.advancedrocketrycommunity.persistence.migration.CheckedSavedDataPolicy;
import io.github.sunthemoon.advancedrocketrycommunity.persistence.migration.ManagedSavedDataType;
import io.github.sunthemoon.advancedrocketrycommunity.testsupport.MinecraftBootstrap;
import io.github.sunthemoon.advancedrocketrycommunity.testsupport.UnstartedServer;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.dedicated.DedicatedPlayerList;
import net.minecraft.server.players.PlayerList;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

/** R120: the bound adapter must translate a release-only await clock back to advancing server ticks. */
@Timeout(60)
class RocketTransactionAwaitClockTest {
    private static final ManagedSavedDataType TYPE = ManagedSavedDataType.ROCKET_TRANSACTIONS;
    private static final CheckedSavedDataPolicy ATOMIC = CheckedSavedDataPolicy.atomic(TYPE);
    @TempDir Path directory;
    @BeforeAll static void bootstrap() { MinecraftBootstrap.initialize(); }

    @Test void successThenAnotherWriteKeepsTheTenServerTickCadence() throws Exception {
        var server = server(); var data = journal(server);
        try {
            insert(data, 1);
            data.requestWrite(file(), ATOMIC.withCommitter((staged, target) -> {
                pause(); CheckedSavedDataFile.atomicMove(staged, target);
            }));
            data.awaitWriter();
            assertTrue(data.acknowledgedCurrent());
            insert(data, 2);
            data.requestWrite(file(), ATOMIC);
            assertTrue(data.writerInFlight());
            completed(data); data.pollWriter(); // Do not advance the actual server tick during IO.
            insert(data, 3); data.requestWrite(file(), ATOMIC);
            assertTrue(data.writerBusy() && !data.writerInFlight());
            ticks(server, data, 9);
            assertFalse(data.writerInFlight(), "A write started before the ten-tick cadence");
            ticks(server, data, 1);
            assertTrue(data.writerInFlight(), "The await clock stalled the following server-tick cadence");
            completed(data); data.pollWriter();
            assertTrue(data.acknowledgedCurrent());
        } finally { data.abort(); System.clearProperty(RocketTransactionWriterCuts.PROPERTY); }
    }

    @Test void aLateAwaitFailureBacksOffForServerTicksRatherThanAwaitMilliseconds() throws Exception {
        var server = server(); var data = journal(server);
        try {
            insert(data, 1);
            data.requestWrite(file(), ATOMIC.withCommitter((staged, target) -> {
                pause(); throw new IOException("R120 delayed failure before replacement");
            }));
            data.awaitWriter();
            assertFalse(data.acknowledgedCurrent());
            assertBackoff(server, data);
        } finally { data.abort(); System.clearProperty(RocketTransactionWriterCuts.PROPERTY); }
    }

    @Test void anInterruptedAwaitAlsoAccountsForItsElapsedClock() throws Exception {
        var server = server(); var data = journal(server);
        var release = new CountDownLatch(1);
        Thread owner = Thread.currentThread();
        Thread interrupter = new Thread(() -> {
            try { Thread.sleep(400); owner.interrupt(); }
            catch (InterruptedException stop) { Thread.currentThread().interrupt(); }
        }, "R120-interrupt-await");
        try {
            insert(data, 1);
            data.requestWrite(file(), ATOMIC.withCommitter((staged, target) -> {
                try { release.await(); } catch (InterruptedException stop) { throw new IOException(stop); }
                CheckedSavedDataFile.atomicMove(staged, target);
            }));
            interrupter.start();
            assertThrows(IllegalStateException.class, data::awaitWriter);
            assertTrue(Thread.interrupted(), "The await did not preserve interruption");
            interrupter.join(); release.countDown(); completed(data); data.pollWriter();
            // Start and finish at the adapter clock, then verify that another request gets its next ten ticks.
            insert(data, 2); data.requestWrite(file(), ATOMIC); completed(data); data.pollWriter();
            insert(data, 3); data.requestWrite(file(), ATOMIC);
            ticks(server, data, 9); assertFalse(data.writerInFlight());
            ticks(server, data, 1);
            assertTrue(data.writerInFlight(), "Exceptional await exit left a frozen clock");
        } finally {
            Thread.interrupted(); release.countDown(); interrupter.join(); data.abort();
            System.clearProperty(RocketTransactionWriterCuts.PROPERTY);
        }
    }

    private void assertBackoff(MinecraftServer server, RocketTransactionSavedData data) throws Exception {
        data.requestWrite(file(), ATOMIC);
        ticks(server, data, CheckedRootWriter.BACKOFF_TICKS * 3 / 4);
        assertTrue(data.writerBusy() && !data.writerInFlight(), "Retry shortened the failure backoff");
        ticks(server, data, CheckedRootWriter.BACKOFF_TICKS / 4 + 1);
        assertTrue(data.writerInFlight(), "Await milliseconds extended the 200-tick failure backoff");
        completed(data); data.pollWriter(); assertTrue(data.acknowledgedCurrent());
    }

    private Path file() { return directory.resolve(TYPE.fileName()); }
    private static MinecraftServer server() {
        var server = live();
        var players = UnstartedServer.allocate(DedicatedPlayerList.class);
        UnstartedServer.set(PlayerList.class, players, "players", List.of());
        UnstartedServer.set(MinecraftServer.class, server, "playerList", players);
        RocketTransactionWriterBenchmark.at(server, 1_000);
        System.setProperty(RocketTransactionWriterCuts.PROPERTY, "true");
        return server;
    }
    private static void ticks(MinecraftServer server, RocketTransactionSavedData data, int count) {
        for (int i = 0; i < count; i++) {
            RocketTransactionWriterBenchmark.at(server, server.getTickCount() + 1); data.pollWriter();
        }
    }
    private static void completed(RocketTransactionSavedData data) throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
        while (!data.writerCompletionReady()) {
            assertTrue(System.nanoTime() < deadline, "The writer did not complete"); Thread.sleep(1);
        }
    }
    private static void pause() throws IOException {
        try { Thread.sleep(400); } catch (InterruptedException stop) { Thread.currentThread().interrupt(); throw new IOException(stop); }
    }
}
