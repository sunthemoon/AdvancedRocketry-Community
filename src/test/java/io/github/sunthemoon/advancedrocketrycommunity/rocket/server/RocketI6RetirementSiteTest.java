package io.github.sunthemoon.advancedrocketrycommunity.rocket.server;

import static org.junit.jupiter.api.Assertions.*;

import io.github.sunthemoon.advancedrocketrycommunity.persistence.migration.CheckedSavedDataPolicy;
import io.github.sunthemoon.advancedrocketrycommunity.persistence.migration.ManagedSavedDataType;
import io.github.sunthemoon.advancedrocketrycommunity.resourceescrow.EscrowBarrier;
import io.github.sunthemoon.advancedrocketrycommunity.resourceescrow.EscrowChunkStore;
import io.github.sunthemoon.advancedrocketrycommunity.resourceescrow.EscrowReason;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketTransferPhase;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketTransferRecord;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.persistence.RocketJournalTestAccess;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.persistence.RocketTransferSavedData;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.model.RocketPosition;
import io.github.sunthemoon.advancedrocketrycommunity.testsupport.MinecraftBootstrap;
import java.nio.file.Path;
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

class RocketI6RetirementSiteTest {
    @TempDir Path directory;
    @BeforeAll static void bootstrap() { MinecraftBootstrap.initialize(); }

    @Test void plainReleaseWaitsForCoordinatorBothSlotsAndCheckedRemoval() {
        var f = new Fixture(); f.begin();
        assertEquals(0, f.port.tickets); assertTrue(f.port.callbacks.isEmpty());
        assertSame(f.record, f.journal.find(f.record.transferId()).orElseThrow());
        f.retirements.tick(); f.complete(0);
        assertEquals(List.of(RocketRetirementProof.Slot.origin(f.record.destinationSnapshot()),
                RocketRetirementProof.Slot.origin(f.record.sourceSnapshot())), f.port.slots);
        assertSame(f.record, f.journal.find(f.record.transferId()).orElseThrow());
        f.complete(1);
        assertTrue(f.journal.find(f.record.transferId()).isEmpty());
        assertEquals(0, f.effects); assertTrue(f.retirements.pending(f.logical()));
        f.acknowledge(); f.acknowledge();
        assertEquals(1, f.effects); assertEquals(0, f.discarded);
        assertFalse(f.retirements.pending(f.logical())); assertTrue(f.journal.clean());
    }

    @Test void unsupportedDestinationNeverUsesSourceFallback() {
        var f = new Fixture(); f.port.supported = false; f.begin();
        for (int tick = 100; tick <= 800; tick++) { f.port.now = tick; f.retirements.tick(); }
        assertEquals(0, f.port.tickets); assertTrue(f.port.callbacks.isEmpty());
        assertSame(f.record, f.journal.find(f.record.transferId()).orElseThrow());
        assertEquals(0, f.effects); assertEquals(1, f.discarded);
        assertFalse(f.retirements.pending(f.logical()));
    }

    @Test void coldDemandStopsAfterThreePacedStepsWithoutRemovingT() {
        var f = new Fixture(); f.port.loaded = false; f.begin();
        for (int tick = 100; tick <= 800; tick++) { f.port.now = tick; f.retirements.tick(); }
        assertEquals(List.of(100L, 300L, 500L), f.port.ticketTicks);
        assertTrue(f.port.callbacks.isEmpty()); assertEquals(1, f.discarded);
        assertEquals(0, f.effects); assertSame(f.record, f.journal.find(f.record.transferId()).orElseThrow());
    }

    @Test void stopBeforeProofKeepsAuthorityAndReleasesOnlyTransientWork() {
        var f = new Fixture(); f.begin(); f.retirements.tick(); f.retirements.clear();
        f.complete(0); f.acknowledge();
        assertSame(f.record, f.journal.find(f.record.transferId()).orElseThrow());
        assertEquals(0, f.effects); assertEquals(1, f.discarded); assertTrue(f.journal.clean());
    }

    private final class Fixture {
        final RocketTransferSavedData journal = new RocketTransferSavedData();
        final RocketTransferRecord record = RocketStationMotionRuleTest.record(new RocketPosition(72, 80, 8))
                .destinationSpawned(UUID.randomUUID()).advance(RocketTransferPhase.PASSENGERS_TRANSFERRED)
                .advance(RocketTransferPhase.SOURCE_REMOVED).advance(RocketTransferPhase.COMMITTED);
        final Port port = new Port();
        final RocketOrdinaryRetirement retirements = new RocketOrdinaryRetirement(new HashSet<>(), new HashSet<>(), Set::of, s -> port);
        int effects, discarded; long writes;
        Fixture() { journal.put(record); acknowledge(); }
        UUID logical() { return record.logicalRocketId(); }
        void acknowledge() {
            var type = ManagedSavedDataType.ROCKET_TRANSFERS;
            RocketJournalTestAccess.write(journal, directory.resolve(type.fileName()), CheckedSavedDataPolicy.atomic(type), ++writes * 1000);
        }
        void begin() {
            var site = RocketOrdinaryRetirement.Site.valueOf("BEFORE_RELEASE");
            try (var held = RocketOrdinaryRetirement.hold(journal, logical())) {
                retirements.begin(port, journal, site, record, record.destinationEntityId().orElseThrow(), List.of(), false, held,
                        new RocketOrdinaryRetirement.Effects(() -> { effects++; return true; }, () -> discarded++));
            }
        }
        void complete(int index) {
            var capture = new EscrowChunkStore.Capture(CompletableFuture.completedFuture(null), null, directory, new ChunkPos(0, 0),
                    new AtomicLong(1), 1, 0, () -> { }, () -> { });
            port.callbacks.get(index).accept(new EscrowBarrier.Result(new EscrowBarrier.Proof(null, capture,
                    new AtomicBoolean(true), new AtomicBoolean(true)), EscrowReason.NONE));
        }
    }

    private static final class Port implements RocketOrdinaryRetirement.Port {
        long now = 100; int tickets;
        boolean supported = true, loaded = true;
        final List<Long> ticketTicks = new ArrayList<>();
        final List<RocketRetirementProof.Slot> slots = new ArrayList<>();
        final List<Consumer<EscrowBarrier.Result>> callbacks = new ArrayList<>();
        public long now() { return now; }
        public RocketOrdinaryRetirement.Presence survivor(RocketTransferRecord r, UUID id, RocketRetirementProof.Slot slot) {
            return RocketOrdinaryRetirement.Presence.LOADED;
        }
        public boolean crewReady(RocketTransferRecord r, UUID id, boolean offline, Set<UUID> delivered) { return true; }
        public RocketOrdinaryRetirement.SlotState slot(RocketRetirementProof.Slot slot) {
            return loaded ? RocketOrdinaryRetirement.SlotState.LOADED : RocketOrdinaryRetirement.SlotState.UNLOADED;
        }
        public boolean captureSupported(RocketRetirementProof.Slot slot) { return supported; }
        public boolean captureAvailable(RocketRetirementProof.Slot slot, long tick) { return true; }
        public boolean capture(RocketRetirementProof.Slot slot, Predicate<CompoundTag> validator, long tick,
                Consumer<String> sink, Consumer<EscrowBarrier.Result> callback) { slots.add(slot); callbacks.add(callback); return true; }
        public void retryNextTick(Runnable retry) { fail("Destination retry must respect demand pacing"); }
        public boolean bayAllows(RocketTransferSavedData j, RocketTransferRecord r, RocketTransferBayRecoveryGate.Action action) { return true; }
        public boolean bayClaimed(UUID id) { return false; }
        public boolean escrowAllows(RocketTransferSavedData j, RocketTransferRecord r, RocketTransferSavedData.Reservation own) {
            return own == null ? j.actionable(r.logicalRocketId()) : j.actionable(r.logicalRocketId(), own);
        }
        public CompoundTag projection(RocketTransferRecord r, UUID survivor) {
            var tag = new CompoundTag(); tag.putUUID("UUID", survivor); tag.put(RocketRetirementProof.DATA, new CompoundTag()); return tag;
        }
        public void keepLoaded(RocketTransferRecord r, List<RocketRetirementProof.Slot> slots) { tickets++; ticketTicks.add(now); }
        public Consumer<String> sink() { return label -> { }; }
        public boolean afterAcknowledged(RocketTransferSavedData j, RocketTransferSavedData.Reservation token,
                BooleanSupplier effect, Runnable discarded) { return RocketJournalTestAccess.queue(j, token, effect, discarded); }
    }
}
