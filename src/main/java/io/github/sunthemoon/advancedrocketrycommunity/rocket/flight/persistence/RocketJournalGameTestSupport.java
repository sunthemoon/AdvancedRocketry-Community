package io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.persistence;

import net.minecraft.gametest.framework.GameTestServer;
import net.minecraft.gametest.framework.GameTestHelper;

/** Serializes deliberately injected presence-matrix fixture cuts, never a gameplay write boundary. */
public final class RocketJournalGameTestSupport {
    private RocketJournalGameTestSupport() { }
    public static void assertPendingLaunch(GameTestHelper helper,
            io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketFlightRequestResult result,
            io.github.sunthemoon.advancedrocketrycommunity.rocket.entity.RocketEntity rocket) {
        helper.assertTrue(helper.getLevel().getServer() instanceof GameTestServer, "Not a GameTest fixture");
        helper.assertTrue(result.code() == io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketFlightRequestCode.JOURNAL_PENDING
                && !result.success(), "Launch did not await its exact journal acknowledgement: " + result.code());
        helper.assertTrue(rocket.flightData().orElseThrow().state() == io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketFlightState.FUELED
                || rocket.flightData().orElseThrow().state() == io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketFlightState.LANDED,
                "Launch started flight before its exact acknowledgement");
        helper.assertTrue(!RocketTransferSavedData.get(helper.getLevel().getServer()).acknowledgedCurrent(),
                "Launch synchronously acknowledged its newly captured generation");
    }
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
    /**
     * W44 A1: one synchronous writer generation of a detached journal bound to its own fixture file, then its
     * continuations; never the world's journal.
     */
    public static void confirmDetached(GameTestHelper helper, RocketTransferSavedData journal, java.nio.file.Path file) {
        helper.assertTrue(helper.getLevel().getServer() instanceof GameTestServer, "Not a GameTest fixture");
        journal.saveChecked(file, io.github.sunthemoon.advancedrocketrycommunity.persistence.migration.CheckedSavedDataPolicy
                .atomic(io.github.sunthemoon.advancedrocketrycommunity.persistence.migration.ManagedSavedDataType.ROCKET_TRANSFERS));
        journal.pollWriter(helper.getLevel().getServer().getTickCount());
        helper.assertTrue(journal.acknowledgedCurrent(), "Detached fixture generation was not acknowledged");
    }

    /**
     * W44 (design section 10): an explicit non-electing state for a shared-world landing test. When the world's Moon
     * slot is still EMPTY, a fixture event with no participants takes it, so the landing under test is not the first.
     */
    public static void occupyFirstMoonLanding(GameTestHelper helper) {
        var server = helper.getLevel().getServer();
        if (!(server instanceof GameTestServer) || !server.isSameThread()) {
            throw new IllegalStateException("The shared Moon slot fixture requires a GameTestServer");
        }
        var journal = RocketTransferSavedData.get(server);
        if (journal.firstMoonLandingRam().isEmpty()) {
            helper.assertTrue(journal.electFirstMoonLanding(io.github.sunthemoon.advancedrocketrycommunity.progression
                    .classic.firstevent.FirstEvent.MoonLanding.create(java.util.UUID.randomUUID(),
                            java.util.UUID.randomUUID(), java.util.UUID.randomUUID(), java.util.UUID.randomUUID(),
                            java.util.UUID.randomUUID(), "0".repeat(64), 0, 0, 0, 0L, java.util.List.of())),
                    "The shared Moon slot fixture was refused");
        }
        acknowledgeFixture(helper);
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
