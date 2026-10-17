package io.github.sunthemoon.advancedrocketrycommunity.resourceescrow;

import static org.junit.jupiter.api.Assertions.*;

import io.github.sunthemoon.advancedrocketrycommunity.testsupport.MinecraftBootstrap;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class EscrowGovernedEnvelopeHoldsTest {
    @BeforeAll static void bootstrap() { MinecraftBootstrap.initialize(); }

    @Test void unchangedNegativeObservationDoesNotRecaptureUntilRootOrEitherCounterChanges() {
        var holds = new EscrowGovernedEnvelopeHolds(); var logical = UUID.randomUUID();
        var root = new Object(); var source = new Object(); var destination = new Object();
        var captures = new AtomicInteger();
        Supplier<Object> reject = () -> { captures.incrementAndGet(); return null; };
        for (int tick = 0; tick < 2_000; tick++) { assertNull(holds.capture(logical, root, source, 3, destination, 4, reject)); }
        assertEquals(1, captures.get());
        assertNull(holds.capture(logical, root, source, 4, destination, 4, reject)); assertEquals(2, captures.get());
        assertNull(holds.capture(logical, root, source, 4, destination, 5, reject)); assertEquals(3, captures.get());
        var acknowledged = new Object();
        assertNull(holds.capture(logical, acknowledged, source, 4, destination, 5, reject)); assertEquals(4, captures.get());
        for (int tick = 0; tick < 2_000; tick++) { assertNull(holds.capture(logical, acknowledged, source, 4, destination, 5, reject)); }
        assertEquals(4, captures.get());
    }

    @Test void replacedEntityAndLifecycleCleanupCannotReuseNegativeState() {
        var holds = new EscrowGovernedEnvelopeHolds(); var logical = UUID.randomUUID(); var root = new Object();
        var first = new Object(); var captures = new AtomicInteger();
        Supplier<Object> reject = () -> { captures.incrementAndGet(); return null; };
        assertNull(holds.capture(logical, root, first, 0, null, -1, reject));
        var replacement = new Object();
        assertNull(holds.capture(logical, root, replacement, 0, null, -1, reject)); assertEquals(2, captures.get());
        holds.retain(id -> false);
        assertNull(holds.capture(logical, root, replacement, 0, null, -1, reject)); assertEquals(3, captures.get());
        holds.clear();
        assertNull(holds.capture(logical, root, replacement, 0, null, -1, reject)); assertEquals(4, captures.get());
    }

    @Test void malformedCapturesAreHeldButPositiveObservationsNeverBecomeAuthority() {
        var holds = new EscrowGovernedEnvelopeHolds(); var logical = UUID.randomUUID(); var root = new Object();
        var captures = new AtomicInteger();
        Supplier<Object> malformed = () -> { captures.incrementAndGet(); throw new IllegalArgumentException("Malformed envelope"); };
        assertThrows(IllegalArgumentException.class, () -> holds.capture(logical, root, null, -1, null, -1, malformed));
        for (int tick = 0; tick < 2_000; tick++) { assertNull(holds.capture(logical, root, null, -1, null, -1, malformed)); }
        assertEquals(1, captures.get()); var newRoot = new Object();
        for (int capture = 0; capture < 3; capture++) {
            assertEquals(Boolean.TRUE, holds.capture(logical, newRoot, null, -1, null, -1, () -> {
                captures.incrementAndGet(); return Boolean.TRUE;
            }));
        }
        assertEquals(4, captures.get(), "Success was reused instead of repeating final validation");
    }

    @Test void negativeHistoryIsBoundedByTheUnchangedActiveFlightLimit() {
        var holds = new EscrowGovernedEnvelopeHolds(); var root = new Object();
        for (int i = 0; i < 64; i++) { assertNull(holds.capture(new UUID(0, i), root, null, -1, null, -1, () -> null)); }
        assertThrows(IllegalArgumentException.class, () -> holds.capture(new UUID(0, 64), root, null, -1, null, -1,
                () -> { fail("Over-capacity observation was captured"); return null; }));
        assertNull(holds.capture(new UUID(0, 63), root, null, -1, null, -1,
                () -> { fail("Existing hold was evicted"); return null; }));
        holds.retain(id -> id.getLeastSignificantBits() != 0);
        assertEquals(Boolean.TRUE, holds.capture(new UUID(0, 64), root, null, -1, null, -1, () -> Boolean.TRUE));
    }
}
