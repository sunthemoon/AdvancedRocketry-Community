package io.github.sunthemoon.advancedrocketrycommunity.rocket.server;

import static org.junit.jupiter.api.Assertions.*;

import io.github.sunthemoon.advancedrocketrycommunity.persistence.migration.CheckedSavedDataPolicy;
import io.github.sunthemoon.advancedrocketrycommunity.persistence.migration.ManagedSavedDataType;
import io.github.sunthemoon.advancedrocketrycommunity.resourceescrow.*;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketTransferPhase;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketTransferRecord;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.persistence.RocketJournalTestAccess;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.persistence.RocketTransferSavedData;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.model.RocketPosition;
import io.github.sunthemoon.advancedrocketrycommunity.testsupport.MinecraftBootstrap;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.*;
import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** W149-R2: real monitor pins must not extend a fill demand or the partial-census deadline. */
class RocketPinnedRetirementTest {
    @TempDir Path directory;
    @BeforeAll static void bootstrap() { MinecraftBootstrap.initialize(); }

    @Test void spentDemandDoesNotRestartWhenPinReleasesAndNewDemandKeepsCooldown() {
        var f = new Fixture(true); Runnable release = f.pin(f.first()); f.begin(); f.run(400);
        assertEquals(List.of(100L, 300L, 500L), f.port.ticketTicks);
        assertFalse(f.retirements.pending(f.logical())); f.kept();
        release.run(); f.run(50);
        assertEquals(3, f.port.ticketTicks.size()); assertTrue(f.port.captures.isEmpty());
        assertFalse(f.retirements.pending(f.logical()), "Release recreated a spent demand");
        f.begin(); f.run(149); assertEquals(3, f.port.ticketTicks.size());
        f.run(1); assertEquals(List.of(100L, 300L, 500L, 700L), f.port.ticketTicks);
        f.completeAll(); f.assertRemovedAfterAck();
    }

    @Test void releaseBeforeExhaustionAllowsFreshFullCensusOnTheNextExistingStep() {
        var f = new Fixture(true); Runnable release = f.pin(f.first()); f.begin(); f.run(50); f.kept();
        release.run(); f.run(149); assertEquals(1, f.port.ticketTicks.size());
        f.run(1); assertEquals(List.of(100L, 300L), f.port.ticketTicks);
        f.completeAll(); f.assertRemovedAfterAck();
    }

    @Test void pinnedLaterSlotExpiresPartialCensusAndReleasesProofBeforeRetry() {
        var f = new Fixture(false); Runnable release = f.pin(f.second()); f.begin();
        assertEquals(1, f.port.captures.size()); var first = f.confirm(0); f.run(101);
        assertEquals("DEADLINE", f.retirements.reason(f.logical()));
        assertFalse(first.valid()); assertTrue(f.journal.clean());
        assertSame(f.record, f.journal.find(f.record.transferId()).orElseThrow());
        assertEquals(1, f.port.captures.size(), "Skipped pinned absence slot");
        release.run(); f.run(199); assertEquals(1, f.port.captures.size());
        f.run(1); assertEquals(2, f.port.captures.size());
        f.completeAll(); f.assertRemovedAfterAck();
    }

    private final class Fixture {
        final RocketTransferSavedData journal = new RocketTransferSavedData();
        final EscrowSaveMonitor monitor = new EscrowSaveMonitor();
        final Port port = new Port(monitor);
        final RocketOrdinaryRetirement retirements = new RocketOrdinaryRetirement(new HashSet<>(), new HashSet<>(), Set::of, server -> port);
        final RocketTransferRecord record;
        final boolean fill;
        int effects, completed;
        long writeTick = 1000;
        Fixture(boolean fill) {
            this.fill = fill;
            var prepared = RocketStationMotionRuleTest.record(new RocketPosition(72, 80, 8));
            record = fill ? prepared.destinationSpawned(UUID.randomUUID()).advance(RocketTransferPhase.PASSENGERS_TRANSFERRED)
                    .advance(RocketTransferPhase.SOURCE_REMOVED).advance(RocketTransferPhase.COMMITTED) : prepared;
            journal.put(record); acknowledge();
        }
        UUID logical() { return record.logicalRocketId(); }
        RocketRetirementProof.Slot first() { return RocketRetirementProof.Slot.origin(fill ? record.destinationSnapshot() : record.sourceSnapshot()); }
        RocketRetirementProof.Slot second() { return RocketRetirementProof.Slot.origin(fill ? record.sourceSnapshot() : record.destinationSnapshot()); }
        Runnable pin(RocketRetirementProof.Slot slot) { return EscrowPinTestAccess.protect(monitor, slot.dimension().toString(), slot.chunk()); }
        void begin() {
            try (var held = RocketOrdinaryRetirement.hold(journal, logical())) {
                retirements.begin(port, journal, fill ? RocketOrdinaryRetirement.Site.BEFORE_FILL : RocketOrdinaryRetirement.Site.RECOVERED_SOURCE,
                        record, fill ? record.destinationEntityId().orElseThrow() : record.sourceEntityId(),
                        fill ? List.of() : List.of(second()), false, held, RocketOrdinaryRetirement.Effects.of(() -> { effects++; return true; }));
            }
        }
        void run(int count) {
            for (int i = 0; i < count; i++) {
                port.now++; var retries = List.copyOf(port.retries); port.retries.clear(); retries.forEach(Runnable::run); retirements.tick();
            }
        }
        void kept() { assertSame(record, journal.find(record.transferId()).orElseThrow()); assertTrue(journal.clean()); assertTrue(port.captures.isEmpty()); }
        EscrowBarrier.Proof confirm(int index) {
            var call = port.captures.get(index); var gen = new AtomicLong(1);
            var capture = new EscrowChunkStore.Capture(CompletableFuture.completedFuture(null), null, directory, call.slot().chunk(),
                    gen, 1, 0, () -> { }, () -> { });
            var proof = new EscrowBarrier.Proof(null, capture, new AtomicBoolean(true), new AtomicBoolean(true));
            call.callback().accept(new EscrowBarrier.Result(proof, EscrowReason.NONE)); completed = index + 1; return proof;
        }
        void completeAll() { for (int i = 0; i < 10 && journal.find(record.transferId()).isPresent(); i++) {
            if (completed < port.captures.size()) { confirm(completed); } run(1);
        } }
        void assertRemovedAfterAck() {
            assertTrue(journal.find(record.transferId()).isEmpty()); assertEquals(0, effects);
            assertTrue(port.captures.stream().anyMatch(call -> call.slot().equals(first())));
            assertTrue(port.captures.stream().anyMatch(call -> call.slot().equals(second())));
            acknowledge(); assertEquals(1, effects); assertFalse(retirements.pending(logical()));
        }
        void acknowledge() {
            var type = ManagedSavedDataType.ROCKET_TRANSFERS;
            RocketJournalTestAccess.write(journal, directory.resolve(type.fileName()), CheckedSavedDataPolicy.atomic(type), writeTick += 1000);
        }
    }
    private record Call(RocketRetirementProof.Slot slot, Consumer<EscrowBarrier.Result> callback) { }
    private static final class Port implements RocketOrdinaryRetirement.Port {
        final EscrowSaveMonitor monitor;
        final List<Long> ticketTicks = new ArrayList<>(); final List<Call> captures = new ArrayList<>(); final List<Runnable> retries = new ArrayList<>();
        long now = 100;
        Port(EscrowSaveMonitor monitor) { this.monitor = monitor; }
        public long now() { return now; }
        public RocketOrdinaryRetirement.Presence survivor(RocketTransferRecord r, UUID id, RocketRetirementProof.Slot slot) { return RocketOrdinaryRetirement.Presence.LOADED; }
        public boolean crewReady(RocketTransferRecord r, UUID id, boolean offline, Set<UUID> delivered) { return true; }
        public RocketOrdinaryRetirement.SlotState slot(RocketRetirementProof.Slot slot) { return RocketOrdinaryRetirement.SlotState.LOADED; }
        public boolean captureSupported(RocketRetirementProof.Slot slot) { return true; }
        public boolean captureAvailable(RocketRetirementProof.Slot slot, long tick) { return monitor.ordinaryAvailable(slot.dimension().toString(), slot.chunk()); }
        public boolean capture(RocketRetirementProof.Slot slot, Predicate<CompoundTag> validator, long tick, Consumer<String> sink, Consumer<EscrowBarrier.Result> callback) {
            assertTrue(captureAvailable(slot, tick)); captures.add(new Call(slot, callback)); return true;
        }
        public void retryNextTick(Runnable retry) { if (!retries.contains(retry)) { retries.add(retry); } }
        public boolean bayAllows(RocketTransferSavedData j, RocketTransferRecord r, RocketTransferBayRecoveryGate.Action action) { return true; }
        public boolean bayClaimed(UUID id) { return false; }
        public boolean escrowAllows(RocketTransferSavedData j, RocketTransferRecord r, RocketTransferSavedData.Reservation token) {
            return token == null ? j.actionable(r.logicalRocketId()) : j.actionable(r.logicalRocketId(), token);
        }
        public CompoundTag projection(RocketTransferRecord r, UUID survivor) {
            var tag = new CompoundTag(); tag.putUUID("UUID", survivor);
            tag.putString("id", "advancedrocketrycommunity:rocket"); tag.put(RocketRetirementProof.DATA, new CompoundTag());
            return tag;
        }
        public void keepLoaded(RocketTransferRecord r, List<RocketRetirementProof.Slot> slots) { ticketTicks.add(now); }
        public Consumer<String> sink() { return label -> { }; }
        public boolean afterAcknowledged(RocketTransferSavedData j, RocketTransferSavedData.Reservation token, BooleanSupplier effect, Runnable discarded) {
            return RocketJournalTestAccess.queue(j, token, effect, discarded);
        }
    }
}
