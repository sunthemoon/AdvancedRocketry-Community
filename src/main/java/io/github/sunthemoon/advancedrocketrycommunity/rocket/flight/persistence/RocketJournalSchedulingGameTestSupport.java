package io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.persistence;

import java.util.UUID;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.GameTestServer;

/** Detached scheduling fixtures; these never replace or poll the world's journal. */
public final class RocketJournalSchedulingGameTestSupport {
    private RocketJournalSchedulingGameTestSupport() { }

    public static boolean queue(GameTestHelper helper, RocketTransferSavedData journal, UUID logical, Runnable effect) {
        if (!(helper.getLevel().getServer() instanceof GameTestServer)) {
            throw new IllegalStateException("Not a GameTest fixture");
        }
        return journal.queueEffect(logical, effect);
    }
}
