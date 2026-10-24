package io.github.sunthemoon.advancedrocketrycommunity.rocket.server;

import static org.junit.jupiter.api.Assertions.*;

import io.github.sunthemoon.advancedrocketrycommunity.persistence.migration.CheckedSavedDataPolicy;
import io.github.sunthemoon.advancedrocketrycommunity.persistence.migration.CheckedSavedDataFile;
import io.github.sunthemoon.advancedrocketrycommunity.persistence.migration.ManagedSavedDataType;
import io.github.sunthemoon.advancedrocketrycommunity.resourceescrow.EscrowBarrier;
import io.github.sunthemoon.advancedrocketrycommunity.resourceescrow.EscrowChunkStore;
import io.github.sunthemoon.advancedrocketrycommunity.resourceescrow.EscrowReason;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketTransferPhase;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketFlightData;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketFlightPlanner;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketFuelState;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketTransferRecord;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketFlightRequestCode;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.transaction.RocketOperationLedger;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.persistence.RocketJournalTestAccess;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.persistence.RocketTransferSavedData;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.model.RocketPosition;
import io.github.sunthemoon.advancedrocketrycommunity.testsupport.MinecraftBootstrap;
import java.nio.file.Path;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Predicate;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.ChunkPos;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** W150: the terminal action shares the proof's reservation, never an acknowledged empty slot. */
class RocketRetirementTerminalTest {
    @TempDir Path directory;
    @BeforeAll static void bootstrap() { MinecraftBootstrap.initialize(); }

    @Test void replacementWaitsForBothProofsThenAcknowledgesExactlyOneMutation() {
        var f = new Fixture(); f.begin();
        long sequence = f.journal.mutationSequence();
        f.complete(0);
        assertSame(f.old, f.journal.findByLogicalRocket(f.logical()).orElseThrow());
        assertEquals(0, f.port.terminals);
        assertEquals(List.of(RocketRetirementProof.Slot.origin(f.old.destinationSnapshot()),
                RocketRetirementProof.Slot.origin(f.old.sourceSnapshot())), f.port.slots);
        f.complete(1);
        assertSame(f.next, f.journal.findByLogicalRocket(f.logical()).orElseThrow(), "Proof must replace T, not remove it");
        assertEquals(sequence + 1, f.journal.mutationSequence(), "There must be only one atomic mutation");
        assertEquals(1, f.port.terminals);
        assertEquals(0, f.accepted);
        assertTrue(f.retirements.pending(f.logical()));
        f.acknowledge(); f.acknowledge();
        assertEquals(1, f.accepted);
        assertEquals(0, f.discarded);
        assertFalse(f.retirements.pending(f.logical()));
        assertTrue(f.journal.clean());
    }

    @Test void anUnloadedDestinationConsumesAPacedTicketThenProvesAndRelaunches() {
        var f = new Fixture(); f.port.coldSurvivor = true; f.begin();
        assertEquals(1, f.port.tickets, "A cold I7 survivor must request its ordinary ticket");
        assertTrue(f.port.callbacks.isEmpty());
        assertSame(f.old, f.journal.findByLogicalRocket(f.logical()).orElseThrow());
        f.port.coldSurvivor = false;
        for (int tick = 101; tick < 300; tick++) { f.port.now = tick; f.retirements.tick(); }
        assertEquals(1, f.port.tickets, "Loading must not bypass the 200-tick spacing");
        f.port.now = 300; f.retirements.tick(); f.complete(0); f.complete(1); f.acknowledge();
        assertEquals(2, f.port.tickets);
        assertSame(f.next, f.journal.findByLogicalRocket(f.logical()).orElseThrow());
        assertEquals(1, f.accepted); assertEquals(0, f.discarded);
    }

    @Test void aDestinationThatStaysUnloadedSpendsThreeStepsWithoutHoldingForever() {
        var f = new Fixture(); f.port.coldSurvivor = true; f.begin();
        for (int tick = 101; tick <= 1000; tick++) { f.port.now = tick; f.retirements.tick(); }
        assertEquals(3, f.port.tickets);
        assertTrue(f.port.callbacks.isEmpty()); assertEquals(0, f.accepted); assertEquals(1, f.discarded);
        assertFalse(f.retirements.pending(f.logical()));
        assertSame(f.old, f.journal.findByLogicalRocket(f.logical()).orElseThrow());
        assertTrue(f.journal.clean());
    }

    @Test void fiveColdReplacementsShareTheFourStartsPerTickBudget() {
        var shared = new RocketOrdinaryRetirement(new HashSet<>(), new HashSet<>(), Set::of, server -> null);
        var entries = new ArrayList<Fixture>();
        for (int i = 0; i < 5; i++) {
            var f = new Fixture(); entries.add(f); f.port.coldSurvivor = true;
            try (var held = RocketOrdinaryRetirement.hold(f.journal, f.logical())) {
                shared.begin(f.port, f.journal, RocketOrdinaryRetirement.Site.BEFORE_REPLACE, f.old,
                        f.old.destinationEntityId().orElseThrow(), List.of(), false, held,
                        new RocketOrdinaryRetirement.Effects(() -> { f.accepted++; return true; }, () -> f.discarded++));
            }
        }
        shared.tick();
        assertEquals(4, entries.stream().mapToInt(f -> f.port.tickets).sum());
        entries.forEach(f -> f.port.now = 101); shared.tick();
        assertEquals(5, entries.stream().mapToInt(f -> f.port.tickets).sum());
        for (int tick = 102; tick < 300; tick++) { for (var f : entries) { f.port.now = tick; } shared.tick(); }
        assertEquals(5, entries.stream().mapToInt(f -> f.port.tickets).sum());
        shared.clear(); assertTrue(entries.stream().allMatch(f -> f.discarded == 1 && f.accepted == 0 && f.journal.clean()));
    }

    @Test void saveOffRefusesABoundedDemandWithoutTicketingOrChangingAuthority() {
        var f = new Fixture(); f.port.saveOff = true; f.begin();
        for (int tick = 101; tick <= 1000; tick++) { f.port.now = tick; f.retirements.tick(); }
        assertEquals(0, f.port.tickets); assertTrue(f.port.callbacks.isEmpty());
        assertFalse(f.retirements.pending(f.logical())); assertEquals(1, f.discarded); assertEquals(0, f.accepted);
        assertSame(f.old, f.journal.findByLogicalRocket(f.logical()).orElseThrow()); assertTrue(f.journal.clean());
    }

    @Test void aRefusedTerminalKeepsTheOldAuthorityAndDiscardsOnce() {
        var f = new Fixture(); f.port.allowed = false; f.begin(); f.complete(0); f.complete(1);
        assertSame(f.old, f.journal.findByLogicalRocket(f.logical()).orElseThrow());
        assertEquals(1, f.port.terminals);
        assertEquals(1, f.discarded);
        assertEquals(0, f.accepted);
        assertTrue(f.journal.clean());
        assertFalse(f.retirements.pending(f.logical()));
        f.retirements.clear(); f.acknowledge(); assertEquals(1, f.discarded);
    }

    @Test void anIoFailureOrLateProofNeverInvokesTheTerminal() {
        var f = new Fixture(); f.begin();
        f.port.callbacks.get(0).accept(new EscrowBarrier.Result(null, EscrowReason.BARRIER_IO));
        f.complete(0);
        assertEquals(0, f.port.terminals);
        assertSame(f.old, f.journal.findByLogicalRocket(f.logical()).orElseThrow());
        assertTrue(f.journal.clean());
        f.retirements.clear(); assertEquals(1, f.discarded);
    }

    @Test void changedStorageBeforeTheNextCaptureRetainsThePredecessor() {
        var f = new Fixture(); f.begin(); f.port.supported = false; f.complete(0);
        assertEquals(1, f.port.callbacks.size(), "Unsupported storage must not capture another slot");
        assertSame(f.old, f.journal.findByLogicalRocket(f.logical()).orElseThrow());
        assertEquals(0, f.port.terminals);
        assertTrue(f.journal.clean());
    }

    @Test void savingDisabledAfterReadbackRefusesTheTerminal() {
        var f = new Fixture(); f.begin(); f.complete(0); f.port.saveOff = true; f.complete(1);
        assertSame(f.old, f.journal.findByLogicalRocket(f.logical()).orElseThrow());
        assertEquals(0, f.port.terminals);
        assertTrue(f.journal.clean());
    }

    @Test void replacementDemandWaitsForTheCoordinatorTickAndKeepsTheSharedTicketCap() {
        var f = new Fixture(); f.port.loaded = false; f.begin(RocketOrdinaryRetirement.Site.BEFORE_REPLACE);
        assertEquals(0, f.port.tickets);
        assertTrue(f.retirements.replacing(f.logical()));
        assertTrue(f.journal.clean());
        for (int tick = 100; tick <= 1000; tick++) { f.port.now = tick; f.retirements.tick(); }
        assertEquals(3, f.port.tickets);
        assertEquals(1, f.discarded);
        assertFalse(f.retirements.pending(f.logical()));
        assertSame(f.old, f.journal.findByLogicalRocket(f.logical()).orElseThrow());
    }

    @Test void spentDemandReportsExactlyOneExistingRefusalToItsRequesterAndNeverTicketsAgain() {
        var f = new Fixture(); var requests = new RocketOperationLedger();
        var request = f.next.transferId(); var requester = UUID.randomUUID();
        assertEquals(RocketOperationLedger.BeginResult.STARTED, requests.begin(request));
        var replies = new ArrayList<RocketFlightRequestCode>();
        f.discardedEffect = RocketFlightService.launchDiscarded(requests, request, requester, f.journal, f.old,
                (player, result) -> { assertEquals(requester, player); replies.add(result.code()); });
        f.port.coldSurvivor = true; f.begin();
        for (int tick = 101; tick < 500; tick++) { f.port.now = tick; f.retirements.tick(); }
        assertTrue(replies.isEmpty()); assertEquals(RocketOperationLedger.Outcome.ACTIVE, requests.outcome(request));
        f.port.now = 500; f.retirements.tick();
        assertEquals(List.of(RocketFlightRequestCode.TRANSFER_JOURNAL_BLOCKED), replies);
        assertEquals(RocketOperationLedger.Outcome.FAILED, requests.outcome(request));
        assertEquals(3, f.port.tickets); assertFalse(f.retirements.pending(f.logical()));
        for (int tick = 501; tick <= 1200; tick++) { f.port.now = tick; f.retirements.tick(); }
        f.discardedEffect.run(); f.discardedEffect.run(); f.retirements.clear(); f.acknowledge();
        assertEquals(1, replies.size()); assertEquals(3, f.port.tickets); assertEquals(0, f.accepted);
        assertSame(f.old, f.journal.findByLogicalRocket(f.logical()).orElseThrow());
    }

    @Test void submittedUnknownSuccessorNeverReportsAPreReplacementRefusal() {
        var f = new Fixture(); var requests = new RocketOperationLedger(); var request = f.next.transferId();
        requests.begin(request); var replies = new ArrayList<RocketFlightRequestCode>();
        f.discardedEffect = RocketFlightService.launchDiscarded(requests, request, UUID.randomUUID(), f.journal, f.old,
                (player, result) -> replies.add(result.code()));
        f.begin(); f.complete(0); f.complete(1);
        var type = ManagedSavedDataType.ROCKET_TRANSFERS;
        var unknown = new CheckedSavedDataPolicy(type, (staged, target) -> {
            CheckedSavedDataFile.atomicMove(staged, target); throw new IOException("Uncertain I7 move");
        }, (target, managed) -> { throw new IllegalStateException("Unreadable I7 readback"); });
        RocketJournalTestAccess.write(f.journal, directory.resolve(type.fileName()), unknown, 2000);
        assertEquals(RocketTransferSavedData.WriteOutcome.UNKNOWN, f.journal.checkedWriteResult().outcome());
        for (int tick = 101; tick <= 1200; tick++) { f.port.now = tick; f.retirements.tick(); }
        assertTrue(replies.isEmpty()); assertEquals(RocketOperationLedger.Outcome.ACTIVE, requests.outcome(request));
        f.retirements.clear(); f.discardedEffect.run(); f.acknowledge();
        assertTrue(replies.isEmpty()); assertEquals(0, f.accepted); assertEquals(1, f.port.tickets);
        assertSame(f.next, f.journal.findByLogicalRocket(f.logical()).orElseThrow());
    }

    @Test void completedAndNonPlayerRequestsDoNotReceiveLateRefusals() {
        var f = new Fixture(); var requests = new RocketOperationLedger(); var request = f.next.transferId();
        requests.begin(request); requests.finish(request, true);
        var replies = new ArrayList<RocketFlightRequestCode>();
        RocketFlightService.launchDiscarded(requests, request, UUID.randomUUID(), f.journal, f.old,
                (player, result) -> replies.add(result.code())).run();
        assertEquals(RocketOperationLedger.Outcome.SUCCEEDED, requests.outcome(request));
        requests.clear(); requests.begin(request);
        RocketFlightService.launchDiscarded(requests, request, null, f.journal, f.old,
                (player, result) -> replies.add(result.code())).run();
        assertEquals(RocketOperationLedger.Outcome.FAILED, requests.outcome(request)); assertTrue(replies.isEmpty());
    }

    @Test void aCandidateLoadedAfterCapturePreventsTheTerminal() {
        var f = new Fixture(); f.begin(); f.complete(0); f.port.changed = "CENSUS_CHANGED"; f.complete(1);
        assertSame(f.old, f.journal.findByLogicalRocket(f.logical()).orElseThrow());
        assertEquals(0, f.port.terminals);
        assertTrue(f.journal.clean());
    }

    @Test void stoppingAfterReplacementKeepsTheCandidateButNeverStartsCountdown() {
        var f = new Fixture(); f.begin(RocketOrdinaryRetirement.Site.BEFORE_REPLACE); f.retirements.tick();
        f.complete(0); f.complete(1); f.retirements.clear(); f.acknowledge();
        assertSame(f.next, f.journal.findByLogicalRocket(f.logical()).orElseThrow());
        assertEquals(0, f.accepted);
        assertEquals(1, f.discarded);
    }

    @Test void uncertainReplacementRetainsTheCandidateAndCompletesOnlyAfterItsCheckedRetry() {
        var f = new Fixture(); f.begin(); f.complete(0); f.complete(1);
        var type = ManagedSavedDataType.ROCKET_TRANSFERS; var file = directory.resolve(type.fileName());
        var unknown = new CheckedSavedDataPolicy(type, (staged, target) -> {
            CheckedSavedDataFile.atomicMove(staged, target); throw new IOException("Uncertain I7 move");
        }, (target, managed) -> { throw new IllegalStateException("Unreadable I7 readback"); });
        RocketJournalTestAccess.write(f.journal, file, unknown, 2000);
        assertEquals(RocketTransferSavedData.WriteOutcome.UNKNOWN, f.journal.checkedWriteResult().outcome());
        f.retirements.tick();
        assertSame(f.next, f.journal.findByLogicalRocket(f.logical()).orElseThrow());
        assertTrue(f.retirements.replacing(f.logical()));
        assertEquals(0, f.accepted); assertEquals(0, f.discarded);
        // Two independent reloads select T', with the accepted source fuel/debits and no old T.
        for (int restart = 0; restart < 2; restart++) {
            var loaded = RocketTransferSavedData.load(CheckedSavedDataFile.readPayload(file, type).orElseThrow());
            assertEquals(1, loaded.entries().size());
            var record = loaded.findByLogicalRocket(f.logical()).orElseThrow();
            assertEquals(f.next.transferId(), record.transferId());
            assertEquals(f.next.sourceFlightData(), record.sourceFlightData());
            assertTrue(loaded.find(f.old.transferId()).isEmpty());
        }
        f.acknowledge(); f.acknowledge();
        assertEquals(1, f.accepted); assertEquals(0, f.discarded); assertEquals(1, f.port.terminals);
    }

    @Test void reservationExhaustionDoesNotDiscardCandidatesOrMutateThePredecessor() {
        var f = new Fixture(); f.begin(RocketOrdinaryRetirement.Site.BEFORE_REPLACE);
        long sequence = f.journal.mutationSequence();
        var tokens = new ArrayList<RocketTransferSavedData.Reservation>();
        try {
            for (int i = 0; i < 128; i++) { tokens.add(f.journal.reserve(UUID.randomUUID()).orElseThrow()); }
            assertTrue(f.journal.reserve(UUID.randomUUID()).isEmpty());
            f.retirements.tick();
            assertEquals(0, f.port.preparations); assertEquals(0, f.port.terminals);
            assertTrue(f.port.callbacks.isEmpty()); assertEquals(sequence, f.journal.mutationSequence());
            assertSame(f.old, f.journal.findByLogicalRocket(f.logical()).orElseThrow());
        } finally { tokens.forEach(RocketTransferSavedData.Reservation::close); f.retirements.clear(); }
        assertEquals(1, f.discarded);
        try (var token = f.journal.reserve(f.logical()).orElseThrow()) { assertTrue(f.journal.actionable(f.logical(), token)); }
    }

    @Test void unsupportedDestinationNeverUsesTheSourceFallbackOrTickets() {
        var f = new Fixture(); f.port.supported = false; f.begin();
        for (int tick = 101; tick < 1000; tick++) { f.port.now = tick; f.retirements.tick(); }
        assertEquals(0, f.port.terminals); assertEquals(0, f.port.tickets); assertTrue(f.port.callbacks.isEmpty());
        assertSame(f.old, f.journal.findByLogicalRocket(f.logical()).orElseThrow());
        assertTrue(f.journal.clean()); assertFalse(f.retirements.replacing(f.logical()), "I7 must finish a refused demand, not hold forever");
        assertEquals(1, f.discarded); assertEquals(0, f.accepted);
        f.retirements.clear(); assertEquals(1, f.discarded);
    }

    private final class Fixture {
        final RocketTransferSavedData journal = new RocketTransferSavedData();
        final RocketTransferRecord old = RocketStationMotionRuleTest.record(new RocketPosition(72, 80, 8))
                .destinationSpawned(UUID.randomUUID()).advance(RocketTransferPhase.PASSENGERS_TRANSFERRED)
                .advance(RocketTransferPhase.SOURCE_REMOVED).advance(RocketTransferPhase.COMMITTED);
        final RocketTransferRecord next = successor(old);
        final Port port = new Port(next);
        final RocketOrdinaryRetirement retirements = new RocketOrdinaryRetirement(new HashSet<>(), new HashSet<>(), Set::of, s -> port);
        int accepted, discarded; long writes;
        Runnable discardedEffect = () -> discarded++;
        Fixture() { journal.put(old); acknowledge(); }
        UUID logical() { return old.logicalRocketId(); }
        void acknowledge() {
            var type = ManagedSavedDataType.ROCKET_TRANSFERS;
            RocketJournalTestAccess.write(journal, directory.resolve(type.fileName()), CheckedSavedDataPolicy.atomic(type), ++writes * 1000);
        }
        void begin() { begin(RocketOrdinaryRetirement.Site.BEFORE_REPLACE); retirements.tick(); }
        void begin(RocketOrdinaryRetirement.Site site) {
            try (var held = RocketOrdinaryRetirement.hold(journal, logical())) {
                retirements.begin(port, journal, site, old, old.destinationEntityId().orElseThrow(),
                        List.of(), false, held, new RocketOrdinaryRetirement.Effects(() -> { accepted++; return true; }, () -> discardedEffect.run()));
            }
        }
        void complete(int index) {
            var generation = new AtomicLong(1);
            var capture = new EscrowChunkStore.Capture(CompletableFuture.completedFuture(null), null, directory, new ChunkPos(0, 0),
                    generation, 1, 0, () -> { }, () -> { });
            port.callbacks.get(index).accept(new EscrowBarrier.Result(new EscrowBarrier.Proof(null, capture,
                    new AtomicBoolean(true), new AtomicBoolean(true)), EscrowReason.NONE));
        }
    }

    private static RocketTransferRecord successor(RocketTransferRecord old) {
        var source = old.destinationSnapshot(); var destination = old.sourceSnapshot();
        UUID request = UUID.randomUUID();
        var fuel = RocketFuelState.empty(1000).fill(1000).state();
        var plan = RocketFlightPlanner.plan(source.stats(), fuel, RocketFlightPlanner.MOON, RocketFlightPlanner.EARTH, request, 1000).plan();
        var transit = RocketFlightData.initial(old.logicalRocketId(), 1000, source.stats().seatCount(),
                plan.sourceBody(), plan.sourceDimension(), source.sourceOrigin(), 1000).withFuel(fuel, 1000)
                .withPlan(plan).startCountdown(1000).completeCountdown(1060).beginTransit(request, 1120);
        return RocketTransferRecord.create(request, old.logicalRocketId(), old.ownerId(), old.destinationEntityId().orElseThrow(),
                source, destination, transit, transit.arriveAtDestination(fuel.debit(plan).state(), plan.destinationBody(),
                        plan.destinationDimension(), destination.sourceOrigin(), 1160), plan.requiredFuel(), 1000);
    }

    private static final class Port implements RocketOrdinaryRetirement.Port {
        final RocketTransferRecord replacement;
        final List<RocketRetirementProof.Slot> slots = new ArrayList<>();
        final List<Consumer<EscrowBarrier.Result>> callbacks = new ArrayList<>();
        int terminals, tickets, preparations; long now = 100;
        boolean allowed = true, supported = true, loaded = true, saveOff, coldSurvivor;
        String changed;
        Port(RocketTransferRecord replacement) { this.replacement = replacement; }
        public long now() { return now; }
        public RocketOrdinaryRetirement.Presence survivor(RocketTransferRecord r, UUID id, RocketRetirementProof.Slot slot) {
            return coldSurvivor ? RocketOrdinaryRetirement.Presence.UNLOADED : RocketOrdinaryRetirement.Presence.LOADED;
        }
        public boolean crewReady(RocketTransferRecord r, UUID id, boolean offline, Set<UUID> delivered) { return true; }
        public RocketOrdinaryRetirement.SlotState slot(RocketRetirementProof.Slot slot) {
            return saveOff ? RocketOrdinaryRetirement.SlotState.SAVE_OFF : loaded ? RocketOrdinaryRetirement.SlotState.LOADED
                    : RocketOrdinaryRetirement.SlotState.UNLOADED;
        }
        public boolean saveOff(RocketRetirementProof.Slot slot) { return saveOff; }
        public boolean captureSupported(RocketRetirementProof.Slot slot) { return supported; }
        public boolean captureAvailable(RocketRetirementProof.Slot slot, long now) { return true; }
        public boolean capture(RocketRetirementProof.Slot slot, Predicate<CompoundTag> validator, long now, Consumer<String> sink,
                Consumer<EscrowBarrier.Result> callback) { slots.add(slot); callbacks.add(callback); return true; }
        public void retryNextTick(Runnable retry) { fail("Destination proof must retain its backoff"); }
        public boolean bayAllows(RocketTransferSavedData j, RocketTransferRecord r, RocketTransferBayRecoveryGate.Action a) { return true; }
        public boolean bayClaimed(UUID id) { return false; }
        public boolean escrowAllows(RocketTransferSavedData j, RocketTransferRecord r, RocketTransferSavedData.Reservation own) {
            return own == null ? j.actionable(r.logicalRocketId()) : j.actionable(r.logicalRocketId(), own);
        }
        public CompoundTag projection(RocketTransferRecord r, UUID survivor) {
            var tag = new CompoundTag(); tag.putUUID("UUID", survivor); tag.put(RocketRetirementProof.DATA, new CompoundTag()); return tag;
        }
        public void keepLoaded(RocketTransferRecord r, List<RocketRetirementProof.Slot> slots) { tickets++; }
        public String prepare(RocketTransferRecord r, UUID id, List<RocketRetirementProof.Slot> slots) { preparations++; return null; }
        public String recheck(RocketTransferRecord r, UUID survivor) { return changed; }
        public Consumer<String> sink() { return label -> { }; }
        // Deliberately compiles before the port is introduced: the old coordinator ignores this terminal.
        public boolean terminal(RocketTransferSavedData j, RocketTransferRecord old, CompoundTag frozen,
                RocketTransferSavedData.Reservation own) {
            terminals++;
            assertTrue(j.actionable(old.logicalRocketId(), own));
            assertFalse(j.actionable(old.logicalRocketId()));
            assertSame(old, j.find(old.transferId()).orElseThrow());
            if (!allowed) { return false; }
            j.replace(old.transferId(), replacement); return true;
        }
        public boolean afterAcknowledged(RocketTransferSavedData j, RocketTransferSavedData.Reservation token,
                BooleanSupplier effect, Runnable discarded) { return RocketJournalTestAccess.queue(j, token, effect, discarded); }
    }
}
