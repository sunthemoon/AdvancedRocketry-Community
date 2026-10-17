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
}
