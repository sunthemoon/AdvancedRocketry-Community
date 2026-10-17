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
}
