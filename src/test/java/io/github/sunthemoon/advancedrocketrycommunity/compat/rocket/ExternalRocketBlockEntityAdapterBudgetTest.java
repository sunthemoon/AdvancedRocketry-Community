package io.github.sunthemoon.advancedrocketrycommunity.compat.rocket;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.sunthemoon.advancedrocketrycommunity.api.rocket.RocketBlockEntityAdapter;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.LongSupplier;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.junit.jupiter.api.Test;

final class ExternalRocketBlockEntityAdapterBudgetTest {
    private static final RocketBlockEntityAdapter UNUSED_PROVIDER = new RocketBlockEntityAdapter() {
        @Override
        public boolean canMove(BlockEntity blockEntity) {
            throw new AssertionError("Clock tests must not invoke world callbacks");
        }

        @Override
        public CompoundTag capture(BlockEntity blockEntity) {
            throw new AssertionError("Clock tests must not invoke world callbacks");
        }

        @Override
        public boolean restore(BlockEntity blockEntity, CompoundTag body) {
            throw new AssertionError("Clock tests must not invoke world callbacks");
        }
    };

    @Test
    void returnedValuesAtOrBelowFiveMillisecondsAreAccepted() {
        assertEquals(5_000_000L, ExternalRocketBlockEntityAdapter.CALLBACK_NANOS);
        for (long elapsed : new long[] {0L, 1L, 4_999_999L, 5_000_000L}) {
            AtomicLong clock = new AtomicLong(17L);
            AtomicInteger calls = new AtomicInteger();
            ExternalRocketBlockEntityAdapter adapter = adapter(clock::get);
            Object expected = new Object();
            Object actual = adapter.call("capture", () -> {
                calls.incrementAndGet();
                clock.addAndGet(elapsed);
                return expected;
            });
            assertSame(expected, actual);
            assertEquals(1, calls.get());
        }
    }

    @Test
    void overBudgetReturnIsRejectedAfterTheCallbackCompletes() {
        AtomicLong clock = new AtomicLong(17L);
        AtomicBoolean returned = new AtomicBoolean();
        ExternalRocketBlockEntityAdapter adapter = adapter(clock::get);
        assertThrows(IllegalStateException.class, () -> adapter.call("restore", () -> {
            clock.addAndGet(5_000_001L);
            returned.set(true);
            return true;
        }));
        assertTrue(returned.get(), "Returned-time budget is not callback preemption");
    }

    @Test
    void warningStateDoesNotAllowLaterSlowReturnsOrRejectLaterFastReturns() {
        AtomicLong clock = new AtomicLong();
        ExternalRocketBlockEntityAdapter adapter = adapter(clock::get);
        for (String phase : new String[] {"canMove", "capture", "restore"}) {
            assertThrows(IllegalStateException.class, () -> adapter.call(phase, () -> {
                clock.addAndGet(5_000_001L);
                return true;
            }));
        }
        assertEquals("accepted", adapter.call("capture", () -> "accepted"));
    }

    @Test
    void callbackExceptionPropagatesUnchangedForOuterContainment() {
        AtomicLong clock = new AtomicLong();
        ExternalRocketBlockEntityAdapter adapter = adapter(clock::get);
        IllegalArgumentException expected = new IllegalArgumentException("Injected callback failure");
        assertSame(expected, assertThrows(IllegalArgumentException.class, () -> adapter.call("capture", () -> {
            clock.addAndGet(5_000_001L);
            throw expected;
        })));
    }

    @Test
    void nanoTimeWraparoundDoesNotChangeElapsedBudgetComparison() {
        AtomicLong clock = new AtomicLong(Long.MAX_VALUE - 2L);
        ExternalRocketBlockEntityAdapter adapter = adapter(clock::get);
        assertEquals("boundary", adapter.call("capture", () -> {
            clock.addAndGet(5_000_000L);
            return "boundary";
        }));
        clock.set(Long.MAX_VALUE - 2L);
        assertThrows(IllegalStateException.class, () -> adapter.call("capture", () -> {
            clock.addAndGet(5_000_001L);
            return "late";
        }));
    }

    private static ExternalRocketBlockEntityAdapter adapter(LongSupplier clock) {
        return new ExternalRocketBlockEntityAdapter(ResourceLocation.tryParse("fixture:container"),
                Set.of(ResourceLocation.tryParse("blocks:container")), 1, UNUSED_PROVIDER, clock);
    }
}
