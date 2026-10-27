package io.github.sunthemoon.advancedrocketrycommunity.satellite.persistence;

import static org.junit.jupiter.api.Assertions.*;

import io.github.sunthemoon.advancedrocketrycommunity.persistence.migration.CheckedSavedDataFile;
import io.github.sunthemoon.advancedrocketrycommunity.persistence.migration.CheckedSavedDataPolicy;
import io.github.sunthemoon.advancedrocketrycommunity.persistence.migration.ManagedSavedDataType;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.service.SatelliteManager;
import io.github.sunthemoon.advancedrocketrycommunity.testsupport.UnstartedServer;
import java.io.IOException;
import java.nio.file.Path;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.dedicated.DedicatedServer;

/** W129b: a live-like registry on a real but unstarted server, real ticks and a committer the test pauses on IO. */
final class RegistryWriterHarness {
    static final ManagedSavedDataType REGISTRY = ManagedSavedDataType.SATELLITE_MISSIONS;
    static final CheckedSavedDataPolicy ATOMIC = CheckedSavedDataPolicy.atomic(REGISTRY);

    private RegistryWriterHarness() { }

    enum Ends { COMMIT, FAIL, UNKNOWN }

    /** The IO thread enters the committer and waits for the test; the attempt then ends as {@code ends}. */
    static final class Paused {
        final CountDownLatch entered = new CountDownLatch(1), release = new CountDownLatch(1);
        final CheckedSavedDataPolicy policy;

        Paused(Ends ends) {
            policy = new CheckedSavedDataPolicy(REGISTRY, (staged, file) -> {
                entered.countDown();
                await(release);
                if (ends == Ends.FAIL) { throw new IOException("W129b paused attempt failed before replacement"); }
                CheckedSavedDataFile.atomicMove(staged, file);
                if (ends == Ends.UNKNOWN) { throw new IOException("W129b paused attempt ambiguous after replacement"); }
            }, ends == Ends.UNKNOWN ? (file, type) -> { throw new IllegalStateException("W129b unreadable readback"); }
                    : CheckedSavedDataFile::readPayload);
        }

        /** Ticks the server until the IO thread waits in the committer; a start may wait for the 10-tick cadence. */
        void awaitEntered(MinecraftServer server, SatelliteMissionSavedData data) {
            pump(server, data, () -> entered.getCount() == 0);
        }
    }

    static DedicatedServer live() {
        DedicatedServer server = UnstartedServer.owned();
        UnstartedServer.set(MinecraftServer.class, server, "running", true);
        return server;
    }

    static SatelliteMissionSavedData registry(MinecraftServer server) {
        var data = SatelliteMissionSavedData.create(0);
        data.bindServerForTests(server);
        return data;
    }

    static void at(MinecraftServer server, int tick) { UnstartedServer.set(MinecraftServer.class, server, "tickCount", tick); }

    static void flags(MinecraftServer server, boolean running, boolean stopped) {
        UnstartedServer.set(MinecraftServer.class, server, "running", running);
        UnstartedServer.set(MinecraftServer.class, server, "stopped", stopped);
    }

    /** Real ticks, one per millisecond, each polling at tick START, until the condition holds. */
    static void pump(MinecraftServer server, SatelliteMissionSavedData data, BooleanSupplier done) {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(20);
        while (!done.getAsBoolean()) {
            assertTrue(System.nanoTime() < deadline, "The registry writer did not finish");
            at(server, server.getTickCount() + 1);
            data.pollWriter();
            try { Thread.sleep(1); } catch (InterruptedException stop) { Thread.currentThread().interrupt(); return; }
        }
    }

    /** Request with {@code policy} and pump until the writer is idle again. */
    static void write(MinecraftServer server, SatelliteMissionSavedData data, Path file, CheckedSavedDataPolicy policy) {
        assertTrue(data.requestWrite(file, policy), "The request was refused");
        pump(server, data, () -> !data.writerBusy());
    }

    static UUID launch(SatelliteMissionSavedData data) { return SatelliteRegistryProofTest.launch(data); }

    static long startEpoch(SatelliteMissionSavedData data, UUID satellite) {
        return data.mission(SatelliteManager.initialMissionId(satellite)).orElseThrow().startEpoch();
    }

    static SatelliteMissionSavedData reload(Path file) {
        return SatelliteMissionSavedData.load(CheckedSavedDataFile.readPayload(file, REGISTRY).orElseThrow());
    }

    static void await(CountDownLatch latch) {
        boolean interrupted = false;
        while (true) {
            try { latch.await(); break; } catch (InterruptedException again) { interrupted = true; }
        }
        if (interrupted) { Thread.currentThread().interrupt(); }
    }
}
