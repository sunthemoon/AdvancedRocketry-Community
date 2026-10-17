package io.github.sunthemoon.advancedrocketrycommunity.resourceescrow;

import static org.junit.jupiter.api.Assertions.*;

import io.github.sunthemoon.advancedrocketrycommunity.persistence.migration.ManagedSavedDataType;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketFlightPlan;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketPassengerManifest;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.persistence.RocketFlightNbtCodec;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.persistence.RocketTransferSavedData;
import io.github.sunthemoon.advancedrocketrycommunity.testsupport.GovernedTransferFixture;
import io.github.sunthemoon.advancedrocketrycommunity.testsupport.MinecraftBootstrap;
import java.nio.file.Path;
import java.util.ArrayList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Diagnostic for C52: coalesced F1s with the same selected pad are not eight completable flights. */
class EscrowFlightBenchmarkPadAdmissionTest {
    @BeforeAll static void bootstrap() { MinecraftBootstrap.initialize(); }
    @TempDir Path directory;

    @Test void aSharedPreF2DestinationArmsEightButRefusesSevenAtJournalPublication() throws Exception {
        var journal = new RocketTransferSavedData();
        try {
            EscrowArmedRestartBoundaryTest.acknowledge(journal,
                    directory.resolve(ManagedSavedDataType.ROCKET_TRANSFERS.fileName()));
            var fixture = GovernedTransferFixture.create();
            var root = EscrowLedger.empty(fixture.record().nativeTransfer().orElseThrow().armed().ledger());
            var launches = new ArrayList<EscrowGovernedLaunch>(); var sources = new ArrayList<CompoundTag>();
            // The surface selector sees only journal.entries(), which is empty until an F1 callback publishes F2.
            var destination = fixture.record().destinationSnapshot();
            for (int i = 0; i < 8; i++) {
                var logical = GovernedTransferFixture.id(2000 + i);
                var source = fixture.source().copy();
                source.putUUID("logical", logical); source.putUUID("physical", GovernedTransferFixture.id(2100 + i));
                source.getCompound("flight").putUUID("logical_rocket_id", logical);
                var initial = RocketFlightNbtCodec.decode(source.getCompound("flight")).data().orElseThrow()
                        .withPassengers(RocketPassengerManifest.empty(1));
                source.put("flight", RocketFlightNbtCodec.encode(initial)); source.put("passengers", new ListTag());
                var old = fixture.record().sourceFlightData().plan().orElseThrow();
                var plan = new RocketFlightPlan(old.schemaVersion(), GovernedTransferFixture.id(2200 + i),
                        old.sourceBody(), old.destinationBody(), old.sourceDimension(), old.destinationDimension(),
                        old.destinationStationId(), old.requiredFuel(), old.createdAtGameTime(), old.destinationTarget(), old.propulsionDebit());
                var guard = journal.logicalGuard(logical).orElseThrow();
                var launch = EscrowGovernedLaunch.prepare(root, source, fixture.record().sourceSnapshot(),
                        initial.withPlan(plan).startCountdown(1), destination, GovernedTransferFixture.id(2300 + i),
                        new EscrowGuard(logical, null, Math.max(1, guard.sequence()), guard.rootDigest()));
                root = launch.preflight(root, 1); launches.add(launch); sources.add(source);
                assertTrue(journal.entries().isEmpty());
            }
            var acknowledged = root.generation(1);
            assertEquals(8, acknowledged.armedFlights().size());
            for (int i = 0; i < 8; i++) {
                var launch = launches.get(i); var arm = acknowledged.armedFlights().get(i); var source = sources.get(i);
                assertDoesNotThrow(() -> launch.afterArm(acknowledged, arm, source, journal));
            }
            journal.put(launches.get(0).afterArm(acknowledged, acknowledged.armedFlights().get(0), sources.get(0), journal));
            for (int i = 1; i < 8; i++) {
                var launch = launches.get(i); var arm = acknowledged.armedFlights().get(i); var source = sources.get(i);
                assertTrue(EscrowFlightJournal.noTransfer(journal, arm.logical(), arm.transfer(), arm.serial()).getAsBoolean(),
                        "Another rocket's F2 must not revoke this rocket's checked no-T lease");
                var refusal = assertThrows(IllegalArgumentException.class,
                        () -> launch.afterArm(acknowledged, arm, source, journal));
                assertEquals("Transfer destination overlaps an active reservation", refusal.getMessage());
            }
            assertEquals(1, journal.entries().size());
            assertEquals(8, acknowledged.armedFlights().size(), "Observation must not cancel or prune authority");
        } finally { journal.stop(); }
    }
}
