package io.github.sunthemoon.advancedrocketrycommunity.rocket.server;

import static org.junit.jupiter.api.Assertions.*;

import io.github.sunthemoon.advancedrocketrycommunity.resourceescrow.EscrowGovernedDispatch;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketTransferRecord;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.persistence.RocketJournalTestAccess;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.persistence.RocketTransferSavedData;
import io.github.sunthemoon.advancedrocketrycommunity.testsupport.GovernedTransferFixture;
import io.github.sunthemoon.advancedrocketrycommunity.testsupport.MinecraftBootstrap;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.UUID;
import net.minecraft.server.MinecraftServer;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** R23 keeps both pruning consumers whole-root, including the scheduler's outstanding work. */
class RocketJournalPruningTest {
    @BeforeAll static void bootstrap() { MinecraftBootstrap.initialize(); }
    @TempDir Path directory;
    @Test void fenceRetainsRemovedTransferUntilAcknowledgementAndReservationRelease() { check(false); }
    @Test void bayRetainsRemovedTransferUntilAcknowledgementAndReservationRelease() { check(true); }

    private void check(boolean bay) {
        var data = new RocketTransferSavedData(); var record = GovernedTransferFixture.create().record();
        var live = new HashSet<UUID>(); var settled = new HashSet<UUID>();
        var fences = new RocketTransferFences(ignored -> null);
        var bays = new RocketTransferBayDispatch(live, settled, fences);
        bays.install(new RocketTransferBayRecoveryGate() {
            @Override public Claim claim(MinecraftServer server, RocketTransferSavedData journal, RocketTransferRecord value) {
                return Claim.held(State.OWNED_WAIT, "FIXTURE_WAIT");
            }
            @Override public Boundary beforeAction(MinecraftServer server, RocketTransferSavedData journal,
                                                   RocketTransferRecord value, Action action) { return Boundary.held(); }
        });
        data.put(record); acknowledge(data);
        if (bay) { bays.classify(null, data, record); }
        else { fences.classifyGoverned(record, new EscrowGovernedDispatch.Decision(
                EscrowGovernedDispatch.Route.HOLD, "GOVERNED_JOURNAL_UNACKNOWLEDGED"), live, settled); }
        java.util.function.Supplier<java.util.Set<UUID>> ids = bay ? bays::ids : fences::ids;
        Runnable prune = bay ? () -> bays.prune(data) : () -> fences.classify(null, data, live, settled);
        try (var token = data.reserve(record.logicalRocketId()).orElseThrow()) {
            data.remove(record.transferId()); prune.run();
            assertTrue(ids.get().contains(record.transferId())); assertFalse(data.actionable(record.logicalRocketId()));
            acknowledge(data); assertTrue(data.acknowledgedCurrent()); assertFalse(data.clean()); prune.run();
            assertTrue(ids.get().contains(record.transferId()), "Acknowledged absence alone cannot prune outstanding scheduler work");
        } finally {
            prune.run(); assertTrue(ids.get().isEmpty()); assertTrue(data.actionable(record.logicalRocketId())); data.abort();
        }
    }
    private void acknowledge(RocketTransferSavedData data) {
        data.save(directory.resolve(RocketTransferSavedData.DATA_NAME + ".dat").toFile());
        RocketJournalTestAccess.awaitWriter(data); assertTrue(data.acknowledgedCurrent());
    }
}
