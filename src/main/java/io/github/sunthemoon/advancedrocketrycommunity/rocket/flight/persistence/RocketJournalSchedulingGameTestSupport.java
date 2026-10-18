package io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.persistence;

import java.util.UUID;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.GameTestServer;

/** GameTest-only scheduling fixtures, including a bounded root for product-entry capacity checks. */
public final class RocketJournalSchedulingGameTestSupport {
    private RocketJournalSchedulingGameTestSupport() { }

    /** Replace only a settled GameTest root, preserving its payload, to exercise product-entry capacity refusal. */
    public static RocketTransferSavedData boundedFixture(GameTestHelper helper, int limit) {
        var server = helper.getLevel().getServer();
        if (!(server instanceof GameTestServer) || !server.isSameThread()) { throw new IllegalStateException("Not a GameTest fixture"); }
        var previous = RocketTransferSavedData.get(server);
        helper.assertTrue(previous.clean(), "Fixture replacement requires a settled root");
        return replace(server, previous, limit);
    }

    /**
     * W91-R3: puts a 128-slot copy back after {@link #boundedFixture} on every path, also after a failed or timed-out
     * test whose root is not settled; its records ride the copy and its continuations are discarded, as at a stop.
     * Nothing happens unless {@code fixture} is still the world root.
     */
    public static void restoreFullBound(net.minecraft.server.MinecraftServer server, RocketTransferSavedData fixture) {
        if (!(server instanceof GameTestServer) || !server.isSameThread()) { throw new IllegalStateException("Not a GameTest fixture"); }
        if (fixture != null && RocketTransferSavedData.existing(server) == fixture) {
            replace(server, fixture, RocketJournalSchedule.LIMIT);
        }
    }

    private static RocketTransferSavedData replace(net.minecraft.server.MinecraftServer server,
                                                   RocketTransferSavedData previous, int limit) {
        var replacement = RocketTransferSavedData.load(previous.save(new net.minecraft.nbt.CompoundTag()), limit);
        previous.stop();
        server.overworld().getDataStorage().set(RocketTransferSavedData.DATA_NAME, replacement);
        return RocketTransferSavedData.get(server);
    }

    public static boolean queue(GameTestHelper helper, RocketTransferSavedData journal, UUID logical, Runnable effect) {
        if (!(helper.getLevel().getServer() instanceof GameTestServer)) {
            throw new IllegalStateException("Not a GameTest fixture");
        }
        return journal.queueEffect(logical, effect);
    }

    /** A real checked writer paused immediately before replacement, on an isolated fixture file. */
    public static HeldWrite hold(GameTestHelper helper, RocketTransferSavedData journal, java.nio.file.Path file) {
        if (!(helper.getLevel().getServer() instanceof GameTestServer)) {
            throw new IllegalStateException("Not a GameTest fixture");
        }
        if (journal == RocketTransferSavedData.get(helper.getLevel().getServer())) {
            throw new IllegalArgumentException("Held writer requires a detached journal");
        }
        var held = new HeldWrite();
        helper.assertTrue(journal.requestWrite(file, new io.github.sunthemoon.advancedrocketrycommunity.persistence
                .migration.CheckedSavedDataPolicy(io.github.sunthemoon.advancedrocketrycommunity.persistence
                .migration.ManagedSavedDataType.ROCKET_TRANSFERS, (staged, target) -> {
                    held.entered.countDown();
                    try {
                        if (!held.release.await(30, java.util.concurrent.TimeUnit.SECONDS)) {
                            throw new java.io.IOException("GameTest held writer was not released");
                        }
                    } catch (InterruptedException failure) {
                        Thread.currentThread().interrupt(); throw new java.io.IOException(failure);
                    }
                    io.github.sunthemoon.advancedrocketrycommunity.persistence.migration.CheckedSavedDataFile
                            .atomicMove(staged, target);
                })), "Held fixture write was refused");
        return held;
    }

    public static final class HeldWrite implements AutoCloseable {
        private final java.util.concurrent.CountDownLatch entered = new java.util.concurrent.CountDownLatch(1);
        private final java.util.concurrent.CountDownLatch release = new java.util.concurrent.CountDownLatch(1);
        private HeldWrite() { }
        public boolean entered() { return entered.getCount() == 0; }
        @Override public void close() { release.countDown(); }
    }

    /** A detached real writer that retains the same failed snapshot until the fixture permits its retry. */
    public static FailedWrite fail(GameTestHelper helper, RocketTransferSavedData journal,
                                   java.nio.file.Path file, boolean unknown) {
        return fail(helper, journal, file, unknown, false);
    }

    public static FailedWrite fail(GameTestHelper helper, RocketTransferSavedData journal,
                                   java.nio.file.Path file, boolean unknown, boolean moveBeforeUnknown) {
        var server = helper.getLevel().getServer();
        if (!(server instanceof GameTestServer) || !server.isSameThread()
                || journal == RocketTransferSavedData.get(server)) {
            throw new IllegalArgumentException("Fault injection requires an owner-thread detached GameTest journal");
        }
        var fault = new FailedWrite();
        helper.assertTrue(journal.requestWrite(file, new io.github.sunthemoon.advancedrocketrycommunity.persistence
                .migration.CheckedSavedDataPolicy(io.github.sunthemoon.advancedrocketrycommunity.persistence
                .migration.ManagedSavedDataType.ROCKET_TRANSFERS, (staged, target) -> {
                    fault.gate.entered.countDown();
                    try {
                        if (!fault.gate.release.await(30, java.util.concurrent.TimeUnit.SECONDS)) {
                            throw new java.io.IOException("Fault fixture gate was not released");
                        }
                    } catch (InterruptedException failure) {
                        Thread.currentThread().interrupt(); throw new java.io.IOException(failure);
                    }
                    if (fault.failing) {
                        if (unknown && moveBeforeUnknown) {
                            io.github.sunthemoon.advancedrocketrycommunity.persistence.migration.CheckedSavedDataFile.atomicMove(staged, target);
                        }
                        if (unknown) { throw new AssertionError("Injected scheduling UNKNOWN"); }
                        throw new java.io.IOException("Injected scheduling NOT_COMMITTED");
                    }
                    io.github.sunthemoon.advancedrocketrycommunity.persistence.migration.CheckedSavedDataFile.atomicMove(staged, target);
                })), "Fault fixture write was refused");
        return fault;
    }

    public static final class FailedWrite implements AutoCloseable {
        private final HeldWrite gate = new HeldWrite();
        private volatile boolean failing = true;
        private FailedWrite() { }
        public boolean entered() { return gate.entered(); }
        public void releaseFailure() { gate.close(); }
        public void recover() { failing = false; gate.close(); }
        @Override public void close() { recover(); }
    }
}
