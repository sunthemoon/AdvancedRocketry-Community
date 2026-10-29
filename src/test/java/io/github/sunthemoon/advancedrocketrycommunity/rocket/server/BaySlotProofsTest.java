package io.github.sunthemoon.advancedrocketrycommunity.rocket.server;

import static io.github.sunthemoon.advancedrocketrycommunity.rocket.server.BayConsumptionFixtures.*;
import static org.junit.jupiter.api.Assertions.*;

import io.github.sunthemoon.advancedrocketrycommunity.resourceescrow.EscrowBarrier;
import io.github.sunthemoon.advancedrocketrycommunity.resourceescrow.EscrowChunkStore;
import io.github.sunthemoon.advancedrocketrycommunity.resourceescrow.EscrowReason;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.bay.deployment.BayStepQueue;
import io.github.sunthemoon.advancedrocketrycommunity.testsupport.MinecraftBootstrap;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;
import java.util.function.Predicate;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.world.level.ChunkPos;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

final class BaySlotProofsTest {
    @BeforeAll static void bootstrap() { MinecraftBootstrap.initialize(); }

    @Test void everySlotMustCompleteAndValidatorsRejectACopyInEitherSlotIncludingNestedPassengers() {
        var f = new Fixture();
        assertEquals(BayStepQueue.Result.IDLE, f.attempt.step(f.port, 1));
        assertTrue(f.port.validators.get(0).test(root(f.frozen)));
        assertFalse(f.port.validators.get(0).test(root(f.frozen, f.frozen)));
        assertTrue(f.results.isEmpty()); f.complete(0); assertTrue(f.results.isEmpty());
        f.attempt.step(f.port, 2);
        assertTrue(f.port.validators.get(1).test(root()));
        assertFalse(f.port.validators.get(1).test(root(f.frozen)));
        var parent = new CompoundTag(); var passengers = new ListTag(); passengers.add(f.frozen); parent.put("Passengers", passengers);
        assertFalse(f.port.validators.get(1).test(root(parent)));
        f.complete(1); assertEquals(1, f.results.size()); assertTrue(f.results.get(0).confirmed());
        f.results.get(0).proof().release(); assertEquals(2, f.released.get());
    }

    @Test void aSupersedingSaveOfAnyEarlierSlotRefusesAndReleasesTheWholeAttempt() {
        var f = new Fixture(); f.attempt.step(f.port, 1); f.complete(0); f.generation.incrementAndGet();
        f.attempt.step(f.port, 2);
        assertEquals(EscrowReason.SUPERSEDING_SAVE, f.results.get(0).reason());
        assertEquals(1, f.released.get()); assertEquals(1, f.port.callbacks.size());
    }

    @Test void aCopyJoinOrLeaseChangeInvalidatesTheAttemptBeforeTheNextSlot() {
        var f = new Fixture(); f.attempt.step(f.port, 1); f.complete(0); f.current.set(false);
        f.attempt.step(f.port, 2);
        assertEquals(EscrowReason.BARRIER_UNSTABLE, f.results.get(0).reason());
        assertEquals(1, f.released.get()); assertFalse(f.results.get(0).confirmed());
    }

    @Test void busyBudgetsAndColdSlotsNeverBecomeProofs() {
        var f = new Fixture(); f.port.available = false;
        assertEquals(BayStepQueue.Result.NEXT_TICK, f.attempt.step(f.port, 1));
        assertEquals(0, f.port.callbacks.size()); assertTrue(f.results.isEmpty());
        f.port.available = true; f.port.loaded = false;
        f.attempt.step(f.port, 2);
        assertEquals(EscrowReason.BARRIER_UNSTABLE, f.results.get(0).reason()); assertFalse(f.results.get(0).confirmed());
    }

    @Test void aStoppedAttemptReleasesALateCompletionWithoutPublishingIt() {
        var f = new Fixture(); f.attempt.step(f.port, 1); f.attempt.close(); f.complete(0);
        assertEquals(1, f.released.get()); assertTrue(f.results.isEmpty());
    }

    @Test void aFailedLaterCaptureReleasesEarlierProofsAndFinalProofsRemainInvalidatable() {
        var failed = new Fixture(); failed.attempt.step(failed.port, 1); failed.complete(0); failed.attempt.step(failed.port, 2);
        failed.port.callbacks.get(1).accept(new EscrowBarrier.Result(null, EscrowReason.BARRIER_IO));
        assertEquals(1, failed.released.get()); assertFalse(failed.results.get(0).confirmed());
        var f = new Fixture(); f.attempt.step(f.port, 1); f.complete(0); f.attempt.step(f.port, 2); f.complete(1);
        assertTrue(f.results.get(0).proof().valid()); f.generation.incrementAndGet(); assertFalse(f.results.get(0).proof().valid());
        f.results.get(0).proof().release(); assertEquals(2, f.released.get());
    }

    @Test void theExistingW98CensusBoundRejectsOverflowAndRepeatedSlots() {
        var f = new Fixture(); var slot = f.slots.get(0);
        assertThrows(IllegalArgumentException.class, () -> new BaySlotProofs(List.of(slot, slot), LOGICAL, SOURCE, f.frozen,
                () -> true, () -> { }, result -> { }));
        var overflow = new ArrayList<RocketRetirementProof.Slot>();
        for (int i = 0; i <= RocketOrdinaryRetirement.MAX_SLOTS; i++) {
            overflow.add(new RocketRetirementProof.Slot(slot.dimension(), new ChunkPos(i, 0)));
        }
        assertThrows(IllegalArgumentException.class, () -> new BaySlotProofs(overflow, LOGICAL, SOURCE, f.frozen,
                () -> true, () -> { }, result -> { }));
    }

    static CompoundTag root(CompoundTag... entities) {
        var root = new CompoundTag(); var list = new ListTag(); for (var entity : entities) { list.add(entity); }
        root.put("Entities", list); return root;
    }

    private static final class Fixture {
        final Port port = new Port();
        final List<RocketRetirementProof.Slot> slots = BayAbsenceCensus.slots(record(1));
        final CompoundTag frozen = new CompoundTag();
        final AtomicBoolean current = new AtomicBoolean(true);
        final AtomicLong generation = new AtomicLong(1);
        final AtomicInteger released = new AtomicInteger();
        final List<BaySlotProofs.Result> results = new ArrayList<>();
        final BaySlotProofs attempt;
        Fixture() {
            assertEquals(2, slots.size()); frozen.putUUID("UUID", SOURCE); frozen.putString("id", "probe:rocket");
            var data = new CompoundTag(); data.putUUID("assembly_transaction_id", LOGICAL); frozen.put(RocketRetirementProof.DATA, data);
            attempt = new BaySlotProofs(slots, LOGICAL, SOURCE, frozen, current::get, () -> { }, results::add);
        }
        void complete(int index) {
            var capture = new EscrowChunkStore.Capture(CompletableFuture.completedFuture(null), null, Path.of("."), slots.get(index).chunk(),
                    generation, generation.get(), 1, () -> { }, released::incrementAndGet);
            port.callbacks.get(index).accept(new EscrowBarrier.Result(new EscrowBarrier.Proof(null, capture,
                    new AtomicBoolean(true), new AtomicBoolean(true)), EscrowReason.NONE));
        }
    }

    private static final class Port extends RocketRetirementWorld {
        final List<Predicate<CompoundTag>> validators = new ArrayList<>();
        final List<Consumer<EscrowBarrier.Result>> callbacks = new ArrayList<>();
        boolean available = true, loaded = true;
        Port() { super(null, null); }
        @Override public RocketOrdinaryRetirement.SlotState slot(RocketRetirementProof.Slot slot) {
            return loaded ? RocketOrdinaryRetirement.SlotState.LOADED : RocketOrdinaryRetirement.SlotState.UNLOADED;
        }
        @Override public boolean captureSupported(RocketRetirementProof.Slot slot) { return true; }
        @Override public boolean captureAvailable(RocketRetirementProof.Slot slot, long now) { return available; }
        @Override public boolean capture(RocketRetirementProof.Slot slot, Predicate<CompoundTag> validator, long now, Consumer<String> sink,
                                         Consumer<EscrowBarrier.Result> callback) {
            validators.add(validator); callbacks.add(callback); return true;
        }
    }
}
