package io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.persistence;

import net.minecraft.gametest.framework.GameTestServer;
import net.minecraft.gametest.framework.GameTestHelper;

/** Serializes deliberately injected presence-matrix fixture cuts, never a gameplay write boundary. */
public final class RocketJournalGameTestSupport {
    private RocketJournalGameTestSupport() { }
    public static io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketTransferRecoveryReport recoverFixture(
            GameTestHelper helper,
            java.util.function.Supplier<io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketTransferRecoveryReport> recover) {
        acknowledgeFixture(helper);
        var report = recover.get();
        if (report.status() == io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketTransferRecoveryReport.Status.RETRY_LATER) {
            acknowledgeFixture(helper);
            report = recover.get();
        }
        return report;
    }
    public static void acknowledgeFixture(GameTestHelper helper) {
        var server = helper.getLevel().getServer();
        if (!(server instanceof GameTestServer) || !server.isSameThread()) {
            throw new IllegalStateException("Journal fixture drain requires a GameTestServer");
        }
        var journal = RocketTransferSavedData.get(server);
        journal.flushChecked(server); journal.drainForTests(); journal.pollWriter(server.getTickCount());
        helper.assertTrue(journal.acknowledgedCurrent(), "Injected fixture generation was not acknowledged");
    }
}
