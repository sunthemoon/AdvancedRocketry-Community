package io.github.sunthemoon.advancedrocketrycommunity.rocket.server;

import static org.junit.jupiter.api.Assertions.*;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.rejoin.RocketReleaseResult;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class RocketReleaseAdmissionTest {
    @org.junit.jupiter.api.BeforeAll static void bootstrap() {
        io.github.sunthemoon.advancedrocketrycommunity.testsupport.MinecraftBootstrap.initialize();
    }
    @Test void pendingSeatsNeverReplanButCompletedSeatsCanReleaseDifferentRiders() {
        UUID previous = UUID.randomUUID(), next = UUID.randomUUID(); var calls = new AtomicInteger();
        var plan = (java.util.function.Supplier<UUID>) () -> { calls.incrementAndGet(); return next; };
        var pending = RocketReleaseAccess.resolve(Optional.of(previous), true, true, id -> "S1", plan);
        assertEquals(RocketReleaseResult.Status.PENDING, pending.status()); assertEquals(previous, pending.release()); assertEquals(0, calls.get());
        var admitted = RocketReleaseAccess.resolve(Optional.of(previous), false, true, id -> "PLACEMENT", plan);
        assertEquals(RocketReleaseResult.Status.ADMITTED, admitted.status()); assertEquals(next, admitted.release()); assertEquals(1, calls.get());
        var repeat = RocketReleaseAccess.resolve(Optional.of(previous), false, true, id -> "PLACEMENT", () -> null);
        assertEquals(RocketReleaseResult.Status.PENDING, repeat.status()); assertEquals(previous, repeat.release());
    }
    @Test void unavailableWriterNeverCallsThePlanner() {
        var calls = new AtomicInteger(); var plan = (java.util.function.Supplier<UUID>) () -> { calls.incrementAndGet(); return UUID.randomUUID(); };
        assertEquals(RocketReleaseResult.Status.UNAVAILABLE, RocketReleaseAccess.resolve(Optional.empty(), false, false, id -> "NONE", plan).status());
        assertEquals(RocketReleaseResult.Status.PENDING, RocketReleaseAccess.resolve(Optional.of(UUID.randomUUID()), false, false, id -> "PLACEMENT", plan).status());
        assertEquals(0, calls.get());
    }
}
