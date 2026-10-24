package io.github.sunthemoon.advancedrocketrycommunity.rocket.server;

import static org.junit.jupiter.api.Assertions.*;

import io.github.sunthemoon.advancedrocketrycommunity.resourceescrow.EscrowBarrier;
import io.github.sunthemoon.advancedrocketrycommunity.resourceescrow.EscrowChunkStore;
import io.github.sunthemoon.advancedrocketrycommunity.resourceescrow.EscrowReason;
import io.github.sunthemoon.advancedrocketrycommunity.testsupport.MinecraftBootstrap;
import java.lang.reflect.Proxy;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import net.minecraft.world.level.ChunkPos;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class SealedProofBatchTest {
    @BeforeAll static void bootstrap() { MinecraftBootstrap.initialize(); }

    @Test void finalProofSetExpiresAndReleasesEveryWatchAtTheExistingAttemptDeadline() throws Exception {
        var f = new Fixture(2); f.tick(); assertTrue(f.active()); assertEquals(1, f.callbacks.size());
        f.complete(0); f.now++; f.tick(); f.complete(1); assertTrue(f.current());
        f.now = 101; assertFalse(f.current()); f.tick();
        assertFalse(f.active()); assertEquals(2, f.released.get()); assertEquals("DEADLINE", f.held);
        f.now = 300; f.tick(); assertEquals(2, f.callbacks.size());
        f.now = 301; f.tick(); assertEquals(3, f.callbacks.size());
    }

    @Test void unselectedRecoveryOnlyExpiresItsAttemptWithoutStartingAnotherCapture() throws Exception {
        var f = new Fixture(2); f.tick(); f.complete(0);
        f.now = 50; f.call("tickDeadline");
        assertEquals(1, f.callbacks.size()); assertTrue(f.active());
        f.now = 101; f.call("tickDeadline");
        assertEquals(1, f.released.get()); assertFalse(f.active()); assertEquals("DEADLINE", f.held);
        f.now = 500; f.call("tickDeadline");
        assertEquals(1, f.callbacks.size(), "Unselected recovery must not acquire another proof attempt");
        f.tick(); assertEquals(2, f.callbacks.size());
    }

    @Test void replacingAnInvalidatedPlanCannotEraseItsExistingFailureBackoff() throws Exception {
        var old = new Fixture(1); old.tick(); old.callbacks.get(0).accept(new EscrowBarrier.Result(null, EscrowReason.BARRIER_IO));
        long retry = (long) old.call("retryAt"); old.call("close");
        var next = new Fixture(1);
        var defer = next.batch.getClass().getDeclaredMethod("deferUntil", long.class); defer.setAccessible(true);
        defer.invoke(next.batch, retry);
        next.now = 199; next.tick(); assertTrue(next.callbacks.isEmpty());
        next.now = 200; next.tick(); assertEquals(1, next.callbacks.size());
        var active = assertThrows(java.lang.reflect.InvocationTargetException.class, () -> defer.invoke(next.batch, 500L));
        assertInstanceOf(IllegalStateException.class, active.getCause(), "Deferral must not extend an active attempt");
    }

    @Test void supersedingGenerationRetriesNextTickWithoutExtendingTheOldAttempt() throws Exception {
        var f = new Fixture(1); f.tick(); f.complete(0); assertTrue(f.current());
        f.generation.incrementAndGet(); assertFalse(f.current()); f.now++; f.tick();
        assertEquals(1, f.released.get()); assertFalse(f.active());
        f.tick(); assertEquals(1, f.callbacks.size());
        f.now++; f.tick(); assertEquals(2, f.callbacks.size());
    }

    @Test void ioAndBoundsFailuresWait200TicksWhileReadinessWaits20() throws Exception {
        for (var reason : List.of(EscrowReason.BARRIER_IO, EscrowReason.BARRIER_BOUNDS)) {
            var f = new Fixture(1); f.tick(); f.callbacks.get(0).accept(new EscrowBarrier.Result(null, reason));
            assertFalse(f.active()); f.now = 199; f.tick(); assertEquals(1, f.callbacks.size());
            f.now = 200; f.tick(); assertEquals(2, f.callbacks.size());
        }
        for (var state : List.of("UNLOADED", "SAVE_OFF")) {
            var f = new Fixture(1); f.readiness = state; f.tick(); assertFalse(f.active());
            f.readiness = "READY"; f.now = 19; f.tick(); assertTrue(f.callbacks.isEmpty());
            f.now = 20; f.tick(); assertEquals(1, f.callbacks.size());
        }
    }

    @Test void busyCaptureOrSharedCapacityCannotHoldAnAttempt() throws Exception {
        var f = new Fixture(1); f.available = false; f.tick(); assertFalse(f.active());
        f.available = true; f.now++; f.room = () -> false; f.tick(); assertFalse(f.active());
        f.room = () -> true; f.now++; f.tick(); assertTrue(f.active());
        f.tick(); assertEquals(1, f.callbacks.size(), "Same-tick reentry captured twice");
    }

    @Test void aClosedOrReplacedAttemptReleasesLateProofsWithoutAdoptingThem() throws Exception {
        var f = new Fixture(1); f.tick(); f.call("close"); f.complete(0);
        assertEquals(1, f.released.get()); assertFalse(f.current()); assertFalse(f.active());
        f.now = 500; f.tick(); assertEquals(1, f.callbacks.size());
        var old = new Fixture(1); old.tick(); old.now = 101; old.tick(); old.now = 301; old.tick();
        old.complete(0); assertEquals(1, old.released.get()); assertFalse(old.current());
        old.complete(1); assertTrue(old.current());
    }

    @Test void saveOffDuringAnAttemptReleasesItsProofsAndPreventsTheNextCapture() throws Exception {
        var f = new Fixture(2); f.tick(); f.complete(0); f.readiness = "SAVE_OFF";
        assertFalse(f.current()); f.now++; f.tick();
        assertEquals(1, f.callbacks.size()); assertEquals(1, f.released.get()); assertFalse(f.active());
    }

    @Test void fourAttemptsAreSharedAcrossIndependentBatches() throws Exception {
        var fixtures = new ArrayList<Fixture>();
        for (int i = 0; i < 5; i++) { fixtures.add(new Fixture(1)); }
        for (var f : fixtures) {
            f.room = () -> fixtures.stream().filter(Fixture::activeUnchecked).count() < 4;
            f.tick();
        }
        assertEquals(4, fixtures.stream().filter(Fixture::activeUnchecked).count());
        assertTrue(fixtures.get(4).callbacks.isEmpty());
        fixtures.get(0).call("close"); fixtures.get(4).now++; fixtures.get(4).tick();
        assertEquals(4, fixtures.stream().filter(Fixture::activeUnchecked).count());
        assertEquals(1, fixtures.get(4).callbacks.size());
    }

    @Test void slotBoundRejectsAnIncompleteCensusInsteadOfTruncatingIt() throws Exception {
        assertNotNull(new Fixture(129));
        var error = assertThrows(java.lang.reflect.InvocationTargetException.class, () -> new Fixture(130));
        assertInstanceOf(IllegalArgumentException.class, error.getCause());
    }

    private static final class Fixture {
        final Object batch;
        long now; String readiness = "READY", held;
        boolean available = true;
        BooleanSupplier room = () -> true;
        final List<Consumer<EscrowBarrier.Result>> callbacks = new ArrayList<>();
        final AtomicInteger released = new AtomicInteger(); final AtomicLong generation = new AtomicLong(1);
        @SuppressWarnings("unchecked") Fixture(int count) throws Exception {
            var type = Class.forName(SealedProofBatchTest.class.getPackageName() + ".SealedProofBatch");
            var portType = Class.forName(type.getName() + "$Port");
            var port = Proxy.newProxyInstance(portType.getClassLoader(), new Class<?>[] {portType}, (obj, method, args) -> {
                return switch (method.getName()) {
                    case "now" -> now;
                    case "readiness" -> {
                        for (Object constant : method.getReturnType().getEnumConstants()) {
                            if (constant.toString().equals(readiness)) { yield constant; }
                        }
                        throw new AssertionError(readiness);
                    }
                    case "attemptRoom" -> room.getAsBoolean();
                    case "available" -> available;
                    case "capture" -> { callbacks.add((Consumer<EscrowBarrier.Result>) args[1]); yield true; }
                    case "held" -> { held = (String) args[0]; yield null; }
                    default -> throw new AssertionError(method);
                };
            });
            var constructor = type.getDeclaredConstructor(int.class, portType); constructor.setAccessible(true);
            batch = constructor.newInstance(count, port);
        }
        Object call(String method) throws Exception { var m = batch.getClass().getDeclaredMethod(method); m.setAccessible(true); return m.invoke(batch); }
        void tick() throws Exception { call("tick"); }
        boolean active() throws Exception { return (boolean) call("active"); }
        boolean current() throws Exception { return (boolean) call("current"); }
        boolean activeUnchecked() { try { return active(); } catch (Exception e) { throw new AssertionError(e); } }
        void complete(int index) {
            var capture = new EscrowChunkStore.Capture(CompletableFuture.completedFuture(null), null, Path.of("."), new ChunkPos(0, 0),
                    generation, generation.get(), 0, () -> { }, released::incrementAndGet);
            callbacks.get(index).accept(new EscrowBarrier.Result(new EscrowBarrier.Proof(null, capture,
                    new AtomicBoolean(true), new AtomicBoolean(true)), EscrowReason.NONE));
        }
    }
}
