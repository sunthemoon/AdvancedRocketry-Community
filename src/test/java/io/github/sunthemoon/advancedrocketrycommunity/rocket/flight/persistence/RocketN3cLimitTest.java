package io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.persistence;

import static org.junit.jupiter.api.Assertions.*;

import io.github.sunthemoon.advancedrocketrycommunity.testsupport.MinecraftBootstrap;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class RocketN3cLimitTest {
    @BeforeAll static void bootstrap() { MinecraftBootstrap.initialize(); }

    @Test void normalServerIsRejectedBeforeReadingOrReplacingTheRoot() {
        assertThrows(IllegalStateException.class, () -> RocketJournalSchedulingGameTestSupport.lower(false,
                () -> { fail("Normal server root read"); return null; },
                limit -> { fail("Normal server root replaced"); return null; }, 1));
    }

    @Test void defaultsStillHaveExactly128Reservations() {
        assertCapacity(new RocketTransferSavedData(), 128);
        assertCapacity(RocketTransferSavedData.load(new RocketTransferSavedData().save(new CompoundTag())), 128);
    }

    @Test void exceptionalExitRestoresTheFullBoundAndPayload() {
        var root = new AtomicReference<>(new RocketTransferSavedData());
        var payload = root.get().save(new CompoundTag());
        assertThrows(Injected.class, () -> {
            try (var fixture = RocketJournalSchedulingGameTestSupport.lower(true, root::get, limit -> {
                var previous = root.get();
                var replacement = RocketTransferSavedData.load(previous.save(new CompoundTag()), limit);
                previous.stop(); root.set(replacement); return replacement;
            }, 1)) {
                assertSame(root.get(), fixture.journal());
                assertCapacity(fixture.journal(), 1);
                throw new Injected();
            }
        });
        assertEquals(payload, root.get().save(new CompoundTag()));
        assertCapacity(root.get(), 128);
    }

    @Test void invalidLimitIsRejectedBeforeReplacingAnything() {
        for (int limit : new int[] {0, 129}) {
            assertThrows(IllegalArgumentException.class, () -> RocketJournalSchedulingGameTestSupport.lower(true,
                    RocketTransferSavedData::new, ignored -> { fail("Invalid replacement"); return null; }, limit));
        }
    }

    @Test void closingTwiceCannotReplaceAnotherFixturesRoot() {
        var root = new AtomicReference<>(new RocketTransferSavedData());
        var replacements = new java.util.concurrent.atomic.AtomicInteger();
        var fixture = RocketJournalSchedulingGameTestSupport.lower(true, root::get, limit -> {
            replacements.incrementAndGet(); var replacement = new RocketTransferSavedData(limit);
            root.set(replacement); return replacement;
        }, 1);
        var other = new RocketTransferSavedData(); root.set(other);
        fixture.close(); fixture.close();
        assertSame(other, root.get()); assertEquals(1, replacements.get());
    }

    private static void assertCapacity(RocketTransferSavedData root, int count) {
        var tokens = new java.util.ArrayList<RocketTransferSavedData.Reservation>();
        try {
            for (int i = 0; i < count; i++) { tokens.add(root.reserve(UUID.randomUUID()).orElseThrow()); }
            assertTrue(root.reserve(UUID.randomUUID()).isEmpty());
        } finally { tokens.forEach(RocketTransferSavedData.Reservation::close); }
    }
    private static final class Injected extends RuntimeException { }
}
