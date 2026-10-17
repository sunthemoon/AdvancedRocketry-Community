package io.github.sunthemoon.advancedrocketrycommunity.resourceescrow;

import static org.junit.jupiter.api.Assertions.*;

import io.github.sunthemoon.advancedrocketrycommunity.persistence.migration.ManagedSavedDataType;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketFlightPlan;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketPassengerManifest;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.persistence.RocketFlightNbtCodec;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.persistence.RocketTransferSavedData;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.model.RocketPosition;
import io.github.sunthemoon.advancedrocketrycommunity.testsupport.GovernedTransferFixture;
import io.github.sunthemoon.advancedrocketrycommunity.testsupport.MinecraftBootstrap;
import java.nio.file.Path;
import java.util.ArrayList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * The native benchmark's first coalesced F1/F2 callback failure (continuation 45): before R17 item 3 the first F2
 * publication revoked every other callback's journal-wide checked no-T. Under the per-rocket lease each callback
 * publishes its own F2 record and the others keep theirs; a rocket's own record still refuses it. No proof is bypassed.
 */
class EscrowFlightBenchmarkAdmissionTest {
    @BeforeAll static void bootstrap() { MinecraftBootstrap.initialize(); }
    @TempDir Path directory;

    @Test void eachCoalescedCallbackPublishesItsOwnF2WithoutRevokingTheOthers() throws Exception {
        var journal = new RocketTransferSavedData();
        var file = directory.resolve(ManagedSavedDataType.ROCKET_TRANSFERS.fileName());
        try {
            EscrowArmedRestartBoundaryTest.acknowledge(journal, file);
            var fixture = GovernedTransferFixture.create();
            var root = EscrowLedger.empty(fixture.record().nativeTransfer().orElseThrow().armed().ledger());
            var launches = new ArrayList<EscrowGovernedLaunch>(); var sources = new ArrayList<CompoundTag>();
            for (int i = 0; i < 8; i++) {
                var logical = GovernedTransferFixture.id(1000 + i);
                var source = fixture.source().copy();
                source.putUUID("logical", logical); source.putUUID("physical", GovernedTransferFixture.id(1100 + i));
                source.getCompound("flight").putUUID("logical_rocket_id", logical);
                var initial = RocketFlightNbtCodec.decode(source.getCompound("flight")).data().orElseThrow()
                        .withPassengers(RocketPassengerManifest.empty(1));
                source.put("flight", RocketFlightNbtCodec.encode(initial)); source.put("passengers", new ListTag());
                var old = fixture.record().sourceFlightData().plan().orElseThrow();
                var plan = new RocketFlightPlan(old.schemaVersion(), GovernedTransferFixture.id(1200 + i),
                        old.sourceBody(), old.destinationBody(), old.sourceDimension(), old.destinationDimension(),
                        old.destinationStationId(), old.requiredFuel(), old.createdAtGameTime(), old.destinationTarget(), old.propulsionDebit());
                var destination = fixture.record().destinationSnapshot().relocated(GovernedTransferFixture.id(1300 + i),
                        old.destinationDimension(), new RocketPosition(72 + i * 32, 80, 8), 160);
                var guard = journal.acknowledgedGuard(logical).orElseThrow();
                var launch = EscrowGovernedLaunch.prepare(root, source, fixture.record().sourceSnapshot(),
                        initial.withPlan(plan).startCountdown(1), destination, GovernedTransferFixture.id(1400 + i),
                        new EscrowGuard(logical, null, Math.max(1, guard.sequence()), guard.rootDigest()));
                root = launch.preflight(root, 1); launches.add(launch); sources.add(source);
            }
            var acknowledged = root.generation(1);
            assertEquals(8, acknowledged.armedFlights().size());
            for (int i = 0; i < 8; i++) {
                launches.get(i).afterArm(acknowledged, acknowledged.armedFlights().get(i), sources.get(i), journal);
            }
            for (int i = 0; i < 8; i++) {
                var launch = launches.get(i); var source = sources.get(i); var arm = acknowledged.armedFlights().get(i);
                var record = assertDoesNotThrow(() -> launch.afterArm(acknowledged, arm, source, journal),
                        "Another rocket's unacknowledged F2 record revoked this callback's checked no-T");
                journal.put(record);
                assertFalse(journal.acknowledgedCurrent());
                // L's own unacknowledged record names T, so the same callback is never checked no-T again.
                var refused = assertThrows(IllegalArgumentException.class, () -> launch.afterArm(acknowledged, arm, source, journal));
                assertEquals("F1 callback lost its acknowledged arm, exact source or checked no-T state", refused.getMessage());
            }
            assertEquals(8, journal.entries().size()); assertEquals(8, acknowledged.armedFlights().size());
            EscrowArmedRestartBoundaryTest.acknowledge(journal, file);
            assertThrows(IllegalArgumentException.class,
                    () -> launches.get(1).afterArm(acknowledged, acknowledged.armedFlights().get(1), sources.get(1), journal),
                    "An acknowledged F2 record still names T");
            assertEquals(8, journal.entries().size(), "Re-observation itself must not publish F2");
        } finally { journal.stop(); }
    }
}
