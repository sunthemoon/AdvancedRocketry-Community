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

/** Diagnostic for the native benchmark's first coalesced F1/F2 callback failure; no proof is bypassed. */
class EscrowFlightBenchmarkAdmissionTest {
    @BeforeAll static void bootstrap() { MinecraftBootstrap.initialize(); }
    @TempDir Path directory;

    @Test void firstF2PublicationInvalidatesTheOtherSevenCoalescedCallbacksUntilJournalAcknowledgement() throws Exception {
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
            var first = launches.get(0).afterArm(acknowledged, acknowledged.armedFlights().get(0), sources.get(0), journal);
            journal.put(first);
            assertFalse(journal.acknowledgedCurrent());
            for (int i = 1; i < 8; i++) {
                var launch = launches.get(i); var source = sources.get(i); var arm = acknowledged.armedFlights().get(i);
                var refused = assertThrows(IllegalArgumentException.class, () -> launch.afterArm(acknowledged, arm, source, journal));
                assertEquals("F1 callback lost its acknowledged arm, exact source or checked no-T state", refused.getMessage());
            }
            assertEquals(1, journal.entries().size()); assertEquals(8, acknowledged.armedFlights().size());
            EscrowArmedRestartBoundaryTest.acknowledge(journal, file);
            assertDoesNotThrow(() -> launches.get(1).afterArm(acknowledged, acknowledged.armedFlights().get(1), sources.get(1), journal));
            assertEquals(1, journal.entries().size(), "Re-observation itself must not publish F2");
        } finally { journal.stop(); }
    }
}
