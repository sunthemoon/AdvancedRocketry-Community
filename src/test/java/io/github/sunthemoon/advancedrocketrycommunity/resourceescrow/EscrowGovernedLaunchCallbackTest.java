package io.github.sunthemoon.advancedrocketrycommunity.resourceescrow;

import static org.junit.jupiter.api.Assertions.*;

import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.persistence.RocketFlightNbtCodec;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.persistence.RocketTransferSavedData;
import io.github.sunthemoon.advancedrocketrycommunity.testsupport.GovernedTransferFixture;
import io.github.sunthemoon.advancedrocketrycommunity.testsupport.MinecraftBootstrap;
import java.nio.file.Path;
import java.util.List;
import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class EscrowGovernedLaunchCallbackTest {
    @BeforeAll static void bootstrap() { MinecraftBootstrap.initialize(); }
    @TempDir Path directory;

    @Test void completeCheckedCallbackProducesOnlyAPreparedModelNotAnEffect() throws Exception {
        try (var state = state()) {
            var before = state.journal.save(new CompoundTag()); long sequence = state.journal.mutationSequence();
            var record = state.launch.afterArm(state.root, state.arm(), state.fixture.source(), state.journal);
            assertEquals(state.arm(), record.nativeTransfer().orElseThrow().armed());
            assertEquals(state.fixture.source(), record.nativeTransfer().orElseThrow().sourceEnvelope(record));
            assertEquals(before, state.journal.save(new CompoundTag())); assertEquals(sequence, state.journal.mutationSequence());
            assertTrue(state.journal.acknowledgedCurrent()); assertTrue(state.journal.entries().isEmpty());
        }
    }

    @Test void stagedArmMissingArmAndTerminalArmCannotPublishF2() throws Exception {
        try (var state = state()) {
            assertThrows(IllegalArgumentException.class, () -> state.launch.afterArm(state.root.generation(0),
                    state.arm(), state.fixture.source(), state.journal));
            assertThrows(IllegalArgumentException.class, () -> state.launch.afterArm(EscrowLedger.empty(state.root.id()),
                    state.arm(), state.fixture.source(), state.journal));
            var tag = state.arm().encode(); tag.putString("outcome", "CANCELLED");
            var terminal = EscrowFlightTables.Armed.decode(tag);
            var root = new EscrowLedger(state.root.id(), 0, 2, List.of(), List.of(), List.of(), 1, List.of(), List.of(terminal));
            assertThrows(IllegalArgumentException.class, () -> state.launch.afterArm(root, terminal, state.fixture.source(), state.journal));
        }
    }

    @Test void lateDismountRelocationAndWrapperChangesRetainArmAndRefuseF2() throws Exception {
        try (var state = state()) {
            var before = EscrowCodec.encode(state.root);
            for (String field : List.of("passengers", "wrapper", "pos", "flight", "snapshot", "motion")) {
                var changed = state.fixture.source().copy(); changed.remove(field);
                assertThrows(IllegalArgumentException.class,
                        () -> state.launch.afterArm(state.root, state.arm(), changed, state.journal), field);
            }
            var dismounted = state.fixture.source().copy(); dismounted.put("passengers", new net.minecraft.nbt.ListTag());
            assertThrows(IllegalArgumentException.class,
                    () -> state.launch.afterArm(state.root, state.arm(), dismounted, state.journal));
            var relocated = state.fixture.source().copy(); relocated.put("pos", GovernedTransferFixture.vector(8.5, 80.5, 8.5));
            assertThrows(IllegalArgumentException.class,
                    () -> state.launch.afterArm(state.root, state.arm(), relocated, state.journal));
            assertEquals(before, EscrowCodec.encode(state.root)); assertTrue(state.journal.entries().isEmpty());
        }
    }

    @Test void lsOwnPendingOrNamedRecordIsNeverCheckedNoT() throws Exception {
        try (var state = state()) {
            // R17 item 3 replaces journal-wide quiescence: a dirty journal with no change of L keeps L's checked absence.
            state.journal.setDirty();
            assertNotNull(state.launch.afterArm(state.root, state.arm(), state.fixture.source(), state.journal));
            acknowledge(state.journal);
            var record = state.launch.afterArm(state.root, state.arm(), state.fixture.source(), state.journal);
            state.journal.put(record);
            assertThrows(IllegalArgumentException.class, () -> state.launch.afterArm(state.root, state.arm(), state.fixture.source(), state.journal));
            acknowledge(state.journal);
            assertThrows(IllegalArgumentException.class, () -> state.launch.afterArm(state.root, state.arm(), state.fixture.source(), state.journal));
        }
    }

    @Test void stoppedEmptyJournalCannotAcceptF2DespiteItsOldCheckedBaseline() throws Exception {
        try (var state = state()) {
            state.journal.stop();
            assertTrue(state.journal.entries().isEmpty());
            assertThrows(IllegalArgumentException.class, () -> state.launch.afterArm(state.root, state.arm(), state.fixture.source(), state.journal));
        }
    }

    private State state() throws Exception {
        var fixture = GovernedTransferFixture.create(); var journal = new RocketTransferSavedData();
        try {
            acknowledge(journal);
            var oldArm = fixture.record().nativeTransfer().orElseThrow().armed();
            var observed = journal.acknowledgedGuard(oldArm.logical()).orElseThrow();
            var guard = new EscrowGuard(oldArm.logical(), null, Math.max(1, observed.sequence()), observed.rootDigest());
            var initial = RocketFlightNbtCodec.decode(fixture.source().getCompound("flight")).data().orElseThrow();
            var countdown = initial.withPlan(fixture.record().sourceFlightData().plan().orElseThrow()).startCountdown(1);
            var root = EscrowLedger.empty(oldArm.ledger());
            var launch = EscrowGovernedLaunch.prepare(root, fixture.source(), fixture.record().sourceSnapshot(), countdown,
                    fixture.record().destinationSnapshot(), oldArm.destination(), guard);
            return new State(fixture, journal, launch, launch.preflight(root, 1).generation(1));
        } catch (Exception failure) { journal.stop(); throw failure; }
    }
    private void acknowledge(RocketTransferSavedData journal) throws Exception {
        EscrowArmedRestartBoundaryTest.acknowledge(journal, directory.resolve(
                io.github.sunthemoon.advancedrocketrycommunity.persistence.migration.ManagedSavedDataType.ROCKET_TRANSFERS.fileName()));
    }
    private record State(GovernedTransferFixture.Fixture fixture, RocketTransferSavedData journal,
                         EscrowGovernedLaunch launch, EscrowLedger root) implements AutoCloseable {
        EscrowFlightTables.Armed arm() { return root.armedFlights().get(0); }
        @Override public void close() { journal.stop(); }
    }
}
