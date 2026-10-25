package io.github.sunthemoon.advancedrocketrycommunity.rocket.server;

import static org.junit.jupiter.api.Assertions.*;

import io.github.sunthemoon.advancedrocketrycommunity.persistence.migration.CheckedSavedDataPolicy;
import io.github.sunthemoon.advancedrocketrycommunity.persistence.migration.ManagedSavedDataType;
import io.github.sunthemoon.advancedrocketrycommunity.resourceescrow.EscrowBarrier;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.*;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.persistence.*;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.model.RocketPosition;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.sealed.*;
import io.github.sunthemoon.advancedrocketrycommunity.testsupport.MinecraftBootstrap;
import java.lang.reflect.*;
import java.nio.file.Path;
import java.util.*;
import java.util.function.*;
import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

/** Real scheduler, batches, retirement coordinator, checked journals and native quotas; callbacks arrive on later ticks. */
class SealedOrdinaryHandoffTest {
    @TempDir Path directory;
    @BeforeAll static void bootstrap() { MinecraftBootstrap.initialize(); }

    @ParameterizedTest @CsvSource({"2,true", "2,false", "63,true", "63,false"})
    void mixedQueueHandsOrdinaryTheFirstFreeCapture(int count, boolean callbacksFirst) throws Exception {
        try (var f = new Fixture(count, callbacksFirst, false)) {
            f.tick(0); f.tick(1); assertTrue(f.entries.get(0).activeAttempt());
            var ordinary = f.enqueue();
            for (int tick = 2; tick < 20_000 && ordinary.started < 0; tick++) { f.tick(tick); }
            var first = f.entries.get(0);
            assertTrue(first.finished >= 0);
            assertTrue(ordinary.started >= first.finished && ordinary.started <= first.finished + 2,
                    "Ordinary started at " + ordinary.started + ", active sealed attempt ended at " + first.finished);
            assertEquals(1, f.entries.stream().filter(e -> e.done).count(), "Another sealed owner passed the waiting return");
            for (long tick = f.now + 1; tick < 20_000 && f.entries.stream().anyMatch(e -> !e.done); tick++) { f.tick(tick); }
            assertTrue(f.entries.stream().allMatch(e -> e.done));
            assertEquals(0, f.deadlines);
        }
    }

    @ParameterizedTest @ValueSource(booleans = {true, false})
    void fullHundredTickAttemptStillHandsOffWithinTwoTicks(boolean callbacksFirst) throws Exception {
        try (var f = new Fixture(2, callbacksFirst, true)) {
            f.tick(0); f.tick(1); var ordinary = f.enqueue();
            for (int tick = 2; tick < 300 && ordinary.started < 0; tick++) { f.tick(tick); }
            var first = f.entries.get(0);
            assertEquals(100, first.finished - first.started);
            assertTrue(ordinary.started >= first.finished && ordinary.started <= first.finished + 2,
                    "Ordinary missed the deadline-length hand-off: " + ordinary.started);
            assertEquals(0, f.deadlines);
        }
    }

    @ParameterizedTest @ValueSource(booleans = {true, false})
    void continuousOrdinaryTrafficCannotRefillAheadOfWaitingSealedWork(boolean callbacksFirst) throws Exception {
        try (var f = new Fixture(1, callbacksFirst, false)) {
            var first = f.enqueue(); assertEquals(0, first.started);
            for (int tick = 1; tick < 500 && f.entries.get(0).started < 0; tick++) {
                f.now = tick;
                if (f.ordinary.size() < 4) { f.enqueue(); }
                f.tick(tick);
            }
            var sealed = f.entries.get(0);
            assertTrue(sealed.started >= 0, "Continuous ordinary traffic starved sealed work");
            long lastEnd = f.returns.stream().filter(r -> r.started >= 0 && r.started < sealed.started)
                    .mapToLong(r -> r.finished).max().orElseThrow();
            assertTrue(lastEnd >= 0 && sealed.started <= lastEnd + 2,
                    "Sealed reserved at " + sealed.started + ", preceding ordinary turn ended at " + lastEnd);
        }
    }

    private final class Fixture implements AutoCloseable {
        final boolean callbacksFirst;
        final Quota quota = new Quota();
        final SealedWorkBudget budget = new SealedWorkBudget();
        final SealedWorkScheduler scheduler = new SealedWorkScheduler();
        final SealedRecoverySelection selection = new SealedRecoverySelection();
        final List<Entry> entries = new ArrayList<>();
        final List<Return> returns = new ArrayList<>();
        final List<Runnable> retries = new ArrayList<>();
        final List<Completion> completions = new ArrayList<>();
        final SealedProofTurns turns;
        final RocketOrdinaryRetirement ordinary = new RocketOrdinaryRetirement(new HashSet<>(), new HashSet<>(), Set::of,
                server -> { throw new AssertionError("No native server in A0 composition"); });
        final SealedLazyWork<UUID> lazy = new SealedLazyWork<>(new SealedLazyWork.Port<>() {
            public UUID discover() { return null; }
            public void attempt(UUID hint, SealedWorkBudget allowance) { fail("No carriers"); }
        });
        long now, writes;
        boolean sealedDemand;
        int deadlines;
        Fixture(int count, boolean callbacksFirst, boolean fullDeadline) throws Exception {
            this.callbacksFirst = callbacksFirst;
            for (int i = 0; i < count; i++) { entries.add(new Entry(i + 1, i == 0 ? 18 : 16, fullDeadline && i == 0)); }
            ordinary.shareSealedCapacity(() -> (int) entries.stream().filter(e -> !e.done).count(),
                    () -> SealedProofAdmission.nativeReservations(active()));
            turns = ordinary.shareSealedTurns(() -> sealedDemand && entries.stream().anyMatch(e -> !e.activeAttempt() && e.eligible(now + 1)));
        }
        int active() { return (int) entries.stream().filter(Entry::activeAttempt).count(); }
        Return enqueue() {
            var r = new Return(returns.size()); returns.add(r);
            try (var held = RocketOrdinaryRetirement.hold(r.journal, r.record.logicalRocketId())) {
                ordinary.begin(r.port, r.journal, RocketOrdinaryRetirement.Site.RECOVERED_SOURCE, r.record,
                        r.record.sourceEntityId(), List.of(), false, held, RocketOrdinaryRetirement.Effects.of(() -> true));
            }
            return r;
        }
        void tick(long tick) {
            now = tick; sealedDemand = true; budget.beginTick(tick);
            var due = completions.stream().filter(c -> c.tick <= now).toList(); completions.removeAll(due);
            due.forEach(c -> c.action.run());
            if (callbacksFirst) { retry(); }
            UUID selected = selection.select(now, entries.stream().map(Entry::id).toList(), id -> entries.stream()
                    .anyMatch(e -> e.id.equals(id) && !e.activeAttempt() && e.eligible(now))).orElse(null);
            scheduler.tick(now, entries, selected, budget, () -> lazy.tick(budget), selection::serviced);
            if (!callbacksFirst) { retry(); }
            ordinary.tick();
            for (var r : returns) { if (r.finished >= 0 && ordinary.pending(r.record.logicalRocketId())) { r.ack(); } }
            assertTrue(budget.remaining() >= 0 && budget.remaining() <= 256);
            assertTrue(ordinary.activeAttempts() + SealedProofAdmission.nativeReservations(active()) <= 4);
        }
        void retry() { var due = List.copyOf(retries); retries.clear(); due.forEach(Runnable::run); }
        public void close() { entries.forEach(e -> e.batch.close()); ordinary.clear(); }
        record Completion(long tick, Runnable action) { }

        final class Entry implements SealedWorkScheduler.Entry, SealedProofBatch.Port {
            final UUID id; final SealedProofBatch batch; final boolean fullDeadline;
            long censusTick = -1, started = -1, finished = -1; boolean done, ready;
            Entry(int id, int slots, boolean fullDeadline) {
                this.id = new UUID(0, id); this.fullDeadline = fullDeadline; batch = new SealedProofBatch(slots, this);
            }
            public UUID id() { return id; }
            public boolean recovery() { return true; }
            public boolean activeAttempt() { return batch.active(); }
            public boolean eligible(long tick) { return !done && tick >= batch.retryAt(); }
            public void tickDeadline() { batch.tickDeadline(); }
            public void tickProof() { batch.tick(); }
            public void work() { if (budget.take() && batch.current()) { done = true; finished = now; batch.close(); } }
            public long now() { return now; }
            public SealedProofBatch.Readiness readiness() {
                if (censusTick != now) { censusTick = now; ready = budget.take(); }
                return ready ? SealedProofBatch.Readiness.READY : SealedProofBatch.Readiness.UNLOADED;
            }
            public boolean attemptRoom() { return budget.remaining() > 0 && turns.sealedRoom() && SealedProofAdmission.canStart(active(), ordinary.activeAttempts()); }
            public void attemptEnded() { turns.sealedEnded(); }
            public boolean available(int slot) { return quota.available(now); }
            public boolean capture(int slot, Consumer<EscrowBarrier.Result> callback) {
                if (!quota.capture(now)) { return false; }
                if (slot == 0) { started = now; }
                completions.add(new Completion(fullDeadline ? started + 100 : now + 1,
                        () -> callback.accept(SealedRecoveryProofSchedulingTest.proved())));
                return true;
            }
            public void held(String reason) { if (reason.equals("DEADLINE")) { deadlines++; } }
        }

        final class Return {
            final RocketTransferSavedData journal = new RocketTransferSavedData();
            final RocketTransferRecord record = RocketStationMotionRuleTest.record(new RocketPosition(72 + returns.size() * 16, 80, 8));
            final Path file;
            final CompoundTag image = new CompoundTag();
            final RocketOrdinaryRetirement.Port port;
            long started = -1, finished = -1;
            Return(int index) {
                file = directory.resolve("return-" + index + "-" + UUID.randomUUID()).resolve(ManagedSavedDataType.ROCKET_TRANSFERS.fileName());
                try { java.nio.file.Files.createDirectories(file.getParent()); }
                catch (java.io.IOException failure) { throw new java.io.UncheckedIOException(failure); }
                image.putString("id", "advancedrocketrycommunity:rocket"); image.putUUID("UUID", record.sourceEntityId());
                image.put(RocketRetirementProof.DATA, new CompoundTag());
                port = (RocketOrdinaryRetirement.Port) Proxy.newProxyInstance(RocketOrdinaryRetirement.Port.class.getClassLoader(),
                        new Class<?>[] {RocketOrdinaryRetirement.Port.class}, (proxy, method, args) -> switch (method.getName()) {
                            case "now" -> now;
                            case "survivor" -> RocketOrdinaryRetirement.Presence.LOADED;
                            case "slot" -> RocketOrdinaryRetirement.SlotState.LOADED;
                            case "captureAvailable" -> quota.available(now);
                            case "capture" -> {
                                if (!quota.capture(now)) { yield false; }
                                if (started < 0) { started = now; }
                                @SuppressWarnings("unchecked") var callback = (Consumer<EscrowBarrier.Result>) args[4];
                                completions.add(new Completion(now + 1, () -> callback.accept(SealedRecoveryProofSchedulingTest.proved())));
                                yield true;
                            }
                            case "retryNextTick" -> { if (!retries.contains(args[0])) { retries.add((Runnable) args[0]); } yield null; }
                            case "projection" -> image.copy();
                            case "bayClaimed", "saveOff" -> false;
                            case "escrowAllows" -> args[2] == null ? journal.actionable(record.logicalRocketId())
                                    : journal.actionable(record.logicalRocketId(), (RocketTransferSavedData.Reservation) args[2]);
                            case "sink" -> (Consumer<String>) label -> { };
                            case "prepare", "recheck", "reservation", "keepLoaded", "releaseTickets" -> null;
                            case "terminal" -> { journal.remove(record.transferId()); finished = now; yield true; }
                            case "afterAcknowledged" -> RocketJournalTestAccess.queue(journal, (RocketTransferSavedData.Reservation) args[1],
                                    (BooleanSupplier) args[2], (Runnable) args[3]);
                            default -> true;
                        });
                journal.put(record); ack(); assertTrue(journal.actionable(record.logicalRocketId()), "Fixture journal acknowledgement failed");
            }
            void ack() { RocketJournalTestAccess.write(journal, file, CheckedSavedDataPolicy.atomic(ManagedSavedDataType.ROCKET_TRANSFERS), ++writes * 1000); }
        }
    }
    private static final class Quota {
        final Object quota; final Method available, reserve, captured, release;
        Quota() throws Exception {
            var type = Class.forName("io.github.sunthemoon.advancedrocketrycommunity.resourceescrow.EscrowBarrierBudget");
            var ctor = type.getDeclaredConstructor(); ctor.setAccessible(true); quota = ctor.newInstance();
            available = method(type, "availableOrdinary", String.class, boolean.class, long.class);
            reserve = method(type, "reserveOrdinary", String.class, boolean.class, long.class);
            captured = method(type, "captured", int.class); release = method(type, "releaseOrdinary", int.class);
        }
        boolean available(long tick) { return (boolean) invoke(available, "minecraft:overworld", true, tick); }
        boolean capture(long tick) {
            if (!(boolean) invoke(reserve, "minecraft:overworld", true, tick)) { return false; }
            invoke(captured, 0); invoke(release, 0); return true;
        }
        Object invoke(Method method, Object... args) {
            try { return method.invoke(quota, args); } catch (ReflectiveOperationException e) { throw new AssertionError(e); }
        }
        static Method method(Class<?> type, String name, Class<?>... args) throws Exception {
            var method = type.getDeclaredMethod(name, args); method.setAccessible(true); return method;
        }
    }
}
