package io.github.sunthemoon.advancedrocketrycommunity.rocket.server;

import static org.junit.jupiter.api.Assertions.*;

import io.github.sunthemoon.advancedrocketrycommunity.resourceescrow.EscrowBarrier;
import io.github.sunthemoon.advancedrocketrycommunity.resourceescrow.EscrowChunkStore;
import io.github.sunthemoon.advancedrocketrycommunity.resourceescrow.EscrowReason;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.sealed.SealedDisassembly;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.sealed.SealedLazyWork;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.sealed.SealedWorkBudget;
import io.github.sunthemoon.advancedrocketrycommunity.testsupport.MinecraftBootstrap;
import java.lang.reflect.Proxy;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;
import java.util.stream.Stream;
import net.minecraft.world.level.ChunkPos;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/** Coordinator composition using real proof lifetimes, including the native entity/terrain two-slot plan. */
class SealedRecoveryProofSchedulingTest {
    @BeforeAll static void bootstrap() { MinecraftBootstrap.initialize(); }

    static Stream<Arguments> heldRecords() {
        var cases = new ArrayList<Arguments>();
        for (int count : new int[] {2, 3, 51, 64}) {
            for (int position : Stream.of(0, count / 2, count - 1).distinct().toList()) {
                for (var held : List.of(SealedDisassembly.Gate.CONFLICT, SealedDisassembly.Gate.UNLOADED)) {
                    cases.add(Arguments.of(count, position, held));
                }
            }
        }
        return cases.stream();
    }

    @ParameterizedTest @MethodSource("heldRecords")
    void readyRecoverySealsWithoutDiscardingHeldRecords(int count, int readyIndex, SealedDisassembly.Gate held) {
        var budget = new SealedWorkBudget();
        var selection = new SealedRecoverySelection();
        var scheduler = new SealedWorkScheduler();
        var lazy = new SealedLazyWork<UUID>(new SealedLazyWork.Port<>() {
            public UUID discover() { return null; }
            public void attempt(UUID id, SealedWorkBudget allowance) { fail("No carriers"); }
        });
        var entries = new ArrayList<Entry>();
        long[] now = {0};
        for (int index = 0; index < count; index++) {
            entries.add(new Entry(new UUID(0, index + 1), index == readyIndex ? SealedDisassembly.Gate.READY : held, now, budget));
        }
        var ready = entries.get(readyIndex);
        try {
            for (int tick = 0; tick < 20_000 && ready.machine.result() == null; tick++) {
                now[0] = tick; budget.beginTick(tick);
                var selected = selection.select(tick, entries.stream().map(entry -> entry.id).toList(), id -> {
                    var entry = entries.stream().filter(value -> value.id.equals(id)).findFirst().orElseThrow();
                    return !entry.activeAttempt() && entry.eligible(now[0]);
                }).orElse(null);
                scheduler.tick(tick, entries, selected, budget, () -> lazy.tick(budget), selection::serviced);
                assertTrue(budget.remaining() >= 0 && budget.remaining() <= 256);
            }
            System.out.printf("count=%d ready=%d held=%s ticks=%d captures=%d deadlines=%d result=%s%n",
                    count, readyIndex, held, now[0] + 1, ready.captures, ready.deadlines, ready.machine.result());
            for (var entry : entries) {
                if (entry != ready) {
                    assertNull(entry.machine.result(), "Held record discarded");
                    assertTrue(entry.retained, "Held U removed");
                    assertEquals(0, entry.captures, "Held record started a native attempt");
                }
            }
            assertEquals(SealedDisassembly.Result.SEALED, ready.machine.result());
            assertEquals(0, ready.deadlines, "Scheduler expired an immediately provable attempt");
            assertEquals(6, ready.captures, "Three complete two-slot proof phases");
        } finally { entries.forEach(entry -> { if (entry.batch != null) { entry.batch.close(); } }); }
    }

    private static final class Entry implements SealedWorkScheduler.Entry {
        final UUID id;
        final SealedDisassembly machine;
        SealedProofBatch batch;
        SealedDisassembly.Goal goal;
        boolean retained = true;
        int captures, deadlines;
        final SealedWorkBudget budget;

        public UUID id() { return id; }
        public boolean recovery() { return true; }
        public boolean activeAttempt() { return batch != null && batch.active(); }
        public boolean eligible(long now) {
            return machine.result() == null && now >= machine.retryAt() && (batch == null || now >= batch.retryAt());
        }
        public void tickDeadline() { if (batch != null) { batch.tickDeadline(); } }
        public void tickProof() { if (batch != null) { batch.tick(); } }
        public void work() { while (machine.step(budget)) { } }

        Entry(UUID id, SealedDisassembly.Gate gate, long[] now, SealedWorkBudget budget) {
            this.id = id;
            this.budget = budget;
            var port = (SealedDisassembly.Port) Proxy.newProxyInstance(SealedDisassembly.Port.class.getClassLoader(),
                    new Class<?>[] {SealedDisassembly.Port.class}, (proxy, method, args) -> switch (method.getName()) {
                        case "now" -> now[0];
                        case "gate" -> gate;
                        case "inspect" -> SealedDisassembly.Cell.OWNED;
                        case "entity" -> SealedDisassembly.Entity.ABSENT;
                        case "proofCurrent" -> goal == args[0] && batch != null && batch.current();
                        case "requestProof" -> {
                            if (goal != args[0] || batch == null) {
                                long retry = batch == null ? Long.MIN_VALUE : batch.retryAt();
                                if (batch != null) { batch.close(); }
                                goal = (SealedDisassembly.Goal) args[0];
                                batch = new SealedProofBatch(2, new SealedProofBatch.Port() {
                                    public long now() { return now[0]; }
                                    public SealedProofBatch.Readiness readiness() { return SealedProofBatch.Readiness.valueOf(gate.name()); }
                                    public boolean attemptRoom() { return true; }
                                    public boolean available(int slot) { return true; }
                                    public boolean capture(int slot, Consumer<EscrowBarrier.Result> callback) {
                                        captures++;
                                        callback.accept(proved());
                                        return true;
                                    }
                                    public void held(String reason) { if (reason.equals("DEADLINE")) { deadlines++; } }
                                });
                                batch.deferUntil(retry);
                            }
                            yield null;
                        }
                        case "removeRecord" -> { retained = false; yield null; }
                        case "held", "checkpoint", "elect", "retain" -> null;
                        default -> true;
                    });
            machine = SealedDisassembly.recover(1, port, true);
        }
    }

    static EscrowBarrier.Result proved() {
        var capture = new EscrowChunkStore.Capture(CompletableFuture.completedFuture(null), null, Path.of("."),
                new ChunkPos(0, 0), new AtomicLong(1), 1, 0, () -> { }, () -> { });
        return new EscrowBarrier.Result(new EscrowBarrier.Proof(null, capture,
                new AtomicBoolean(true), new AtomicBoolean(true)), EscrowReason.NONE);
    }
}
