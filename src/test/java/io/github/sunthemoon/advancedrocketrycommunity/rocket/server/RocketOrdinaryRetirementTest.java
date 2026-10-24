package io.github.sunthemoon.advancedrocketrycommunity.rocket.server;

import static org.junit.jupiter.api.Assertions.*;

import io.github.sunthemoon.advancedrocketrycommunity.persistence.migration.CheckedSavedDataPolicy;
import io.github.sunthemoon.advancedrocketrycommunity.persistence.migration.ManagedSavedDataType;
import io.github.sunthemoon.advancedrocketrycommunity.resourceescrow.EscrowBarrier;
import io.github.sunthemoon.advancedrocketrycommunity.resourceescrow.EscrowChunkStore;
import io.github.sunthemoon.advancedrocketrycommunity.resourceescrow.EscrowReason;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketTransferRecord;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.persistence.RocketJournalTestAccess;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.persistence.RocketTransferSavedData;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.model.RocketPosition;
import io.github.sunthemoon.advancedrocketrycommunity.testsupport.MinecraftBootstrap;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Predicate;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.DoubleTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.world.level.ChunkPos;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

/**
 * W116 (W98-R12): the ordinary retirement coordinator's state machine through a fake port and a real detached journal.
 * Every failure path releases the journal reservation, so whole-root {@code clean()} consumers wait for one attempt at
 * most; a proved return removes T and runs its effects once, after the removal's acknowledgement.
 */
@Timeout(60)
final class RocketOrdinaryRetirementTest {
    private static final ManagedSavedDataType TYPE = ManagedSavedDataType.ROCKET_TRANSFERS;
    @TempDir Path directory;

    @BeforeAll static void bootstrap() { MinecraftBootstrap.initialize(); }

    @Test void sealedEntriesAndAttemptsUseTheSameOrdinaryCapacity() throws Exception {
        var f = new Fixture(false);
        var entries = new AtomicInteger(64); var attempts = new AtomicInteger(4);
        var bind = RocketOrdinaryRetirement.class.getDeclaredMethod("shareSealedCapacity",
                java.util.function.IntSupplier.class, java.util.function.IntSupplier.class);
        bind.invoke(f.retirements, (java.util.function.IntSupplier) entries::get,
                (java.util.function.IntSupplier) attempts::get);
        var room = RocketOrdinaryRetirement.class.getDeclaredMethod("pendingRoom", UUID.class);
        assertEquals(false, room.invoke(f.retirements, UUID.randomUUID()));
        entries.set(63);
        assertEquals(true, room.invoke(f.retirements, f.logical()));
        f.begin(RocketOrdinaryRetirement.Site.RECOVERED_SOURCE);
        assertEquals(1, f.retirements.size());
        assertEquals(false, room.invoke(f.retirements, UUID.randomUUID()));
        assertEquals(true, room.invoke(f.retirements, f.logical()), "The existing entry can be nudged");
        assertEquals(0, f.port.captures.size(), "A caller-held reservation bypassed the shared four-attempt cap");
        assertFalse(f.reserved(), "A busy first attempt must release its token");
        attempts.set(3); f.tick();
        assertEquals(1, f.port.captures.size());
        var active = RocketOrdinaryRetirement.class.getDeclaredMethod("activeAttempts");
        assertEquals(1, active.invoke(f.retirements));
        f.complete(0, failed(EscrowReason.BARRIER_IO));
        assertEquals(0, active.invoke(f.retirements));
    }

    @Test
    void aProofThatNeverCompletesEndsAtItsDeadlineSoCleanIsHeldForOneAttemptAtMost() {
        var f = new Fixture(false);
        f.begin(RocketOrdinaryRetirement.Site.RECOVERED_SOURCE);
        assertTrue(f.reserved(), "The first attempt holds the caller's reservation");
        int held = 0, longest = 0;
        for (int tick = 0; tick < 1_000; tick++) {
            f.tick();
            held = f.journal.clean() ? 0 : held + 1;
            longest = Math.max(longest, held);
        }
        // An attempt holds its reservation from its start tick through ATTEMPT_TICKS more ticks, and never longer.
        assertEquals(RocketOrdinaryRetirement.ATTEMPT_TICKS + 1, longest, "clean() was false longer than one attempt");
        assertEquals("DEADLINE", f.retirements.reason(f.logical()));
        assertTrue(f.retirements.attempts(f.logical()) >= 3, "Later attempts retried after each backoff");
        assertSame(f.record, f.journal.find(f.record.transferId()).orElse(null), "A deadline never removes T");
        assertEquals(0, f.accepted.get() + f.discarded.get());
    }

    @Test
    void anIoFailureReleasesTheReservationAndBacksOff() {
        var f = new Fixture(false);
        f.begin(RocketOrdinaryRetirement.Site.RECOVERED_SOURCE);
        f.complete(0, failed(EscrowReason.BARRIER_IO));
        assertReleased(f, "BARRIER_IO");
        for (int tick = 1; tick < RocketOrdinaryRetirement.RETRY_TICKS; tick++) {
            f.tick();
            assertTrue(f.journal.clean() && f.retirements.attempts(f.logical()) == 1, "The backoff held a reservation or retried early");
        }
        f.tick();
        assertTrue(f.reserved() && f.retirements.attempts(f.logical()) == 2, "The retry did not start after the backoff");
    }

    @Test
    void aSupersedingSaveDuringTheProofReleasesTheReservationAndRetriesOnTheNextTick() {
        var f = new Fixture(true);
        f.begin(RocketOrdinaryRetirement.Site.RECOVERED_SOURCE);
        var generation = new AtomicLong(1); var released = new AtomicInteger();
        f.complete(0, confirmed(generation, released));
        assertEquals(2, f.port.captures.size(), "The discarded copy's slot was not captured next");
        assertTrue(f.reserved());
        generation.incrementAndGet(); // A vanilla store of the survivor's slot after its proof.
        f.tick();
        assertReleased(f, "SUPERSEDING_SAVE");
        assertEquals(1, released.get(), "The invalidated proof's watch was not released");
        f.tick();
        assertTrue(f.reserved() && f.retirements.attempts(f.logical()) == 2, "A superseded attempt waits one tick, not a backoff");
        f.complete(2, failed(EscrowReason.SUPERSEDING_SAVE));
        assertReleased(f, "SUPERSEDING_SAVE");
    }

    @Test
    void aBusyCaptureBudgetOrEscrowRuleReleasesTheReservationBeforeAnyAttempt() {
        var budget = new Fixture(false);
        budget.port.budget = false;
        budget.begin(RocketOrdinaryRetirement.Site.RECOVERED_SOURCE);
        assertReleased(budget, "BUSY");
        assertEquals(0, budget.retirements.attempts(budget.logical()));
        budget.port.budget = true;
        budget.tick();
        assertTrue(budget.reserved() && budget.retirements.attempts(budget.logical()) == 1, "The busy return did not retry on the next tick");

        var escrow = new Fixture(false);
        escrow.port.escrow = false;
        escrow.begin(RocketOrdinaryRetirement.Site.RECONNECT);
        assertReleased(escrow, "BUSY");
        assertEquals(0, escrow.retirements.attempts(escrow.logical()));
    }

    @Test
    void anUnloadedOrSaveOffSlotReleasesTheReservationAtTheStartAndDuringTheAttempt() {
        for (var state : List.of(RocketOrdinaryRetirement.SlotState.UNLOADED, RocketOrdinaryRetirement.SlotState.SAVE_OFF)) {
            String reason = state == RocketOrdinaryRetirement.SlotState.UNLOADED ? "SLOT_UNLOADED" : "SAVE_OFF";
            var atStart = new Fixture(true);
            atStart.port.slots.put(atStart.discardedSlot, state);
            atStart.begin(RocketOrdinaryRetirement.Site.RECOVERED_SOURCE);
            assertReleased(atStart, reason);
            assertEquals(0, atStart.retirements.attempts(atStart.logical()));

            var during = new Fixture(true);
            during.begin(RocketOrdinaryRetirement.Site.RECOVERED_SOURCE);
            during.port.slots.put(during.discardedSlot, state);
            var released = new AtomicInteger();
            during.complete(0, confirmed(new AtomicLong(1), released));
            assertReleased(during, reason);
            assertEquals(1, during.port.captures.size(), "The held slot was captured");
            assertEquals(1, released.get(), "The survivor's proof outlived its attempt");
            for (int tick = 0; tick < RocketOrdinaryRetirement.POLL_TICKS; tick++) {
                during.tick();
                assertTrue(during.journal.clean(), "A held slot kept a reservation");
            }
        }
    }

    @Test
    void aFailBackKeepsItsOwnReservationOnlyUntilTheNextTick() {
        var f = new Fixture(false);
        f.port.crew = false;
        f.begin(RocketOrdinaryRetirement.Site.FAIL_BACK);
        assertEquals("CREW_WAITING", f.retirements.reason(f.logical()));
        assertTrue(f.reserved(), "A countdown cancellation answers JOURNAL_PENDING while its request is outstanding");
        f.tick();
        assertTrue(f.journal.actionable(f.logical()) && f.journal.clean(), "The fail-back kept its reservation past the next tick");
    }

    @Test
    void aGoneSurvivorAbandonsTheReturnUnclassifiedWithItsRecordAndNoReservation() {
        var f = new Fixture(false);
        f.port.crew = false;
        f.begin(RocketOrdinaryRetirement.Site.RECOVERED_SOURCE);
        f.port.presence = RocketOrdinaryRetirement.Presence.GONE;
        for (int tick = 0; tick < RocketOrdinaryRetirement.POLL_TICKS; tick++) { f.tick(); }
        assertFalse(f.retirements.pending(f.logical()));
        assertFalse(f.settled.contains(f.record.transferId()), "T stayed settled, so recovery could not rebuild the survivor");
        assertSame(f.record, f.journal.find(f.record.transferId()).orElse(null));
        assertTrue(f.journal.clean() && f.discarded.get() == 1 && f.accepted.get() == 0);
    }

    @Test
    void aProvedReturnRemovesTheRecordAndRunsItsEffectOnceAfterTheAcknowledgement() {
        var f = new Fixture(true);
        f.begin(RocketOrdinaryRetirement.Site.RECOVERED_SOURCE);
        assertEquals(1, f.port.tickets, "The attempt did not refresh the census's origin tickets");
        f.complete(0, confirmed(new AtomicLong(1), new AtomicInteger()));
        f.complete(1, confirmed(new AtomicLong(1), new AtomicInteger()));
        assertTrue(f.journal.find(f.record.transferId()).isEmpty(), "The proved return did not remove T");
        assertEquals(0, f.accepted.get(), "The effect ran before the removal's acknowledgement");
        f.acknowledge();
        assertEquals(1, f.accepted.get());
        assertEquals(0, f.discarded.get());
        assertFalse(f.retirements.pending(f.logical()));
        assertFalse(f.live.contains(f.record.transferId()) || f.settled.contains(f.record.transferId()));
        assertTrue(f.journal.clean());
    }

    private static void assertReleased(Fixture f, String reason) {
        assertEquals(reason, f.retirements.reason(f.logical()));
        assertTrue(f.journal.actionable(f.logical()) && f.journal.clean(), reason + " kept the journal reservation");
        assertSame(f.record, f.journal.find(f.record.transferId()).orElse(null), reason + " removed T");
        assertTrue(f.retirements.pending(f.logical()), reason + " dropped the return");
    }

    private static EscrowBarrier.Result failed(EscrowReason reason) { return new EscrowBarrier.Result(null, reason); }

    private static EscrowBarrier.Result confirmed(AtomicLong generation, AtomicInteger released) {
        var capture = new EscrowChunkStore.Capture(CompletableFuture.completedFuture(null), null, Path.of("."), new ChunkPos(0, 0),
                generation, generation.get(), 0, () -> { }, released::incrementAndGet);
        return new EscrowBarrier.Result(new EscrowBarrier.Proof(null, capture, new AtomicBoolean(true), new AtomicBoolean(true)), EscrowReason.NONE);
    }

    /** A detached journal holding an acknowledged PREPARED T, and the coordinator with a fake port. */
    private final class Fixture {
        final RocketTransferSavedData journal = new RocketTransferSavedData();
        final RocketTransferRecord record = RocketStationMotionRuleTest.record(new RocketPosition(72, 80, 8));
        final Set<UUID> live = new HashSet<>(), settled = new HashSet<>();
        final FakePort port = new FakePort(journal);
        final RocketOrdinaryRetirement retirements = new RocketOrdinaryRetirement(live, settled, Set::of, server -> port);
        final RocketRetirementProof.Slot discardedSlot = RocketRetirementProof.Slot.origin(record.destinationSnapshot());
        final boolean discarding;
        final AtomicInteger accepted = new AtomicInteger(), discarded = new AtomicInteger();
        long tick;

        Fixture(boolean discarding) {
            this.discarding = discarding;
            journal.put(record);
            acknowledge();
            assertTrue(journal.clean() && journal.actionable(logical()));
        }

        UUID logical() { return record.logicalRocketId(); }

        void begin(RocketOrdinaryRetirement.Site site) {
            try (var held = RocketOrdinaryRetirement.hold(journal, logical())) {
                assertNotNull(held.token());
                retirements.begin(port, journal, site, record, record.sourceEntityId(), discarding ? List.of(discardedSlot) : List.of(),
                        false, held, new RocketOrdinaryRetirement.Effects(() -> { accepted.incrementAndGet(); return true; }, discarded::incrementAndGet));
            }
        }

        /** One server tick: the barrier's one-tick retries of the previous tick, then the service's coordinator tick. */
        void tick() {
            port.now++;
            var due = List.copyOf(port.retries); port.retries.clear();
            due.forEach(Runnable::run);
            retirements.tick();
        }

        void complete(int capture, EscrowBarrier.Result result) { port.captures.get(capture).callback().accept(result); }

        boolean reserved() { return !journal.actionable(logical()) && !journal.clean(); }

        void acknowledge() {
            RocketJournalTestAccess.write(journal, directory.resolve(TYPE.fileName()), CheckedSavedDataPolicy.atomic(TYPE), tick += 1_000);
        }
    }

    private record Capture(RocketRetirementProof.Slot slot, Predicate<CompoundTag> validator, Consumer<EscrowBarrier.Result> callback) { }

    /** A port whose world answers are fields; captures wait for the test to complete them, as the barrier's callbacks do. */
    private static final class FakePort implements RocketOrdinaryRetirement.Port {
        final RocketTransferSavedData journal;
        final Map<RocketRetirementProof.Slot, RocketOrdinaryRetirement.SlotState> slots = new HashMap<>();
        final List<Capture> captures = new ArrayList<>();
        final List<Runnable> retries = new ArrayList<>();
        final CompoundTag projection = image();
        RocketOrdinaryRetirement.Presence presence = RocketOrdinaryRetirement.Presence.LOADED;
        boolean crew = true, budget = true, escrow = true;
        long now = 100;
        int tickets;

        FakePort(RocketTransferSavedData journal) { this.journal = journal; }

        @Override public long now() { return now; }
        @Override public RocketOrdinaryRetirement.Presence survivor(RocketTransferRecord record, UUID survivor, RocketRetirementProof.Slot slot) {
            return presence;
        }
        @Override public boolean crewReady(RocketTransferRecord record, UUID survivor, boolean offlineAllowed, Set<UUID> delivered) { return crew; }
        @Override public RocketOrdinaryRetirement.SlotState slot(RocketRetirementProof.Slot slot) {
            return slots.getOrDefault(slot, RocketOrdinaryRetirement.SlotState.LOADED);
        }
        @Override public boolean captureSupported(RocketRetirementProof.Slot slot) { return true; }
        @Override public boolean captureAvailable(RocketRetirementProof.Slot slot, long now) { return budget; }
        @Override public boolean capture(RocketRetirementProof.Slot slot, Predicate<CompoundTag> validator, long now, Consumer<String> sink,
                                         Consumer<EscrowBarrier.Result> callback) {
            captures.add(new Capture(slot, validator, callback)); return true;
        }
        @Override public void retryNextTick(Runnable retry) { if (!retries.contains(retry)) { retries.add(retry); } }
        @Override public boolean bayAllows(RocketTransferSavedData journal, RocketTransferRecord record, RocketTransferBayRecoveryGate.Action action) {
            return true;
        }
        @Override public boolean bayClaimed(UUID transfer) { return false; }
        /** The real rule's journal part: L actionable with the attempt's own reservation recognized. */
        @Override public boolean escrowAllows(RocketTransferSavedData journal, RocketTransferRecord record, RocketTransferSavedData.Reservation own) {
            UUID logical = record.logicalRocketId();
            return escrow && (own == null ? journal.actionable(logical) : journal.actionable(logical, own));
        }
        @Override public CompoundTag projection(RocketTransferRecord record, UUID survivor) {
            return presence == RocketOrdinaryRetirement.Presence.LOADED ? projection.copy() : null;
        }
        @Override public void keepLoaded(RocketTransferRecord record, List<RocketRetirementProof.Slot> census) { tickets++; }
        @Override public Consumer<String> sink() { return label -> { }; }
        @Override public boolean afterAcknowledged(RocketTransferSavedData journal, RocketTransferSavedData.Reservation token,
                                                   BooleanSupplier effect, Runnable discarded) {
            return RocketJournalTestAccess.queue(journal, token, effect, discarded);
        }

        private static CompoundTag image() {
            var data = new CompoundTag(); data.putLong("fuel", 1_000L);
            var pos = new ListTag(); pos.add(DoubleTag.valueOf(12.5D)); pos.add(DoubleTag.valueOf(72.0D)); pos.add(DoubleTag.valueOf(12.5D));
            var tag = new CompoundTag(); tag.putString("id", "advancedrocketrycommunity:rocket"); tag.putUUID("UUID", UUID.randomUUID());
            tag.put("Pos", pos); tag.put(RocketRetirementProof.DATA, data);
            return tag;
        }
    }
}
