package io.github.sunthemoon.advancedrocketrycommunity.compat.atmosphere;

import io.github.sunthemoon.advancedrocketrycommunity.api.atmosphere.SuitOxygenProvider;
import java.util.List;
import java.util.OptionalInt;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiFunction;
import java.util.function.Function;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.IntTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class SuitOxygenAccessTest {
    private static final Function<CompoundTag, OptionalInt> READ = data -> data.contains("oxygen")
            && !data.contains("oxygen", 3) ? OptionalInt.empty() : OptionalInt.of(data.getInt("oxygen"));
    private static final BiFunction<CompoundTag, Integer, CompoundTag> WRITE = (data, value) -> {
        data.putInt("oxygen", value); return data;
    };

    @Test
    void initializesEmptyAndRoundTripsWithoutChangingOriginalOrRetainedOutput() {
        var access = new SuitOxygenAccess();
        AtomicReference<CompoundTag> retained = new AtomicReference<>();
        var provider = provider(READ, (data, units) -> { retained.set(data); return WRITE.apply(data, units); });
        var initial = access.read(provider, null);
        assertEquals(0, initial.oxygen());
        CompoundTag saved = access.prepare(initial, 1000);
        assertEquals(1000, access.read(provider, saved).oxygen());
        retained.get().putInt("oxygen", 2000);
        assertEquals(1000, access.read(provider, saved).oxygen());
        saved.getCompound("data").putString("marker", "keep");
        CompoundTag before = saved.copy();
        CompoundTag updated = access.prepare(access.read(provider, saved), 999);
        assertEquals(before, saved);
        assertEquals(999, access.read(provider, updated).oxygen());
        assertEquals("keep", updated.getCompound("data").getString("marker"));
        assertEquals(1, updated.getInt("schema_version"));
        assertEquals("fixture:suit", updated.getString("provider"));
        assertEquals(2, updated.getInt("payload_version"));
    }

    @Test
    void malformedBodyIsItemLocalAndCannotBePrepared() {
        var access = new SuitOxygenAccess();
        var provider = provider(READ, WRITE);
        CompoundTag bad = saved(5);
        bad.getCompound("data").putString("oxygen", "malformed");
        CompoundTag before = bad.copy();
        assertNull(access.read(provider, bad));
        assertNull(access.prepare(null, 1000));
        assertEquals(before, bad);
        assertFalse(access.disabled(provider));
        assertEquals(5, access.read(provider, saved(5)).oxygen());
    }

    @Test
    void futureVersionIdentityAndInvalidEnvelopesDoNotInvokeCallbacksOrMutate() {
        AtomicInteger calls = new AtomicInteger();
        var provider = provider(data -> { calls.incrementAndGet(); return READ.apply(data); }, WRITE);
        var access = new SuitOxygenAccess();
        for (String key : List.of("schema_version", "payload_version", "provider", "data", "extra")) {
            CompoundTag data = saved(12);
            switch (key) {
                case "schema_version" -> data.putInt(key, 2);
                case "payload_version" -> data.putInt(key, 3);
                case "provider" -> data.putString(key, "absent:suit");
                case "data" -> data.putInt(key, 1);
                default -> data.putString(key, "preserve");
            }
            CompoundTag before = data.copy();
            assertNull(access.read(provider, data));
            assertEquals(before, data);
        }
        assertNull(access.read(provider, IntTag.valueOf(1)));
        assertEquals(0, calls.get());
        assertFalse(access.disabled(provider));
    }

    @Test
    void nullOutOfRangeAndThrowingReadsDisableOnceAndResetOnClear() {
        List<Function<CompoundTag, OptionalInt>> invalid = List.of(data -> null, data -> OptionalInt.of(-1),
                data -> OptionalInt.of(2001), data -> { throw new IllegalStateException("not logged"); });
        for (var callback : invalid) {
            AtomicInteger calls = new AtomicInteger();
            var provider = provider(data -> { calls.incrementAndGet(); return callback.apply(data); }, WRITE);
            var access = new SuitOxygenAccess();
            CompoundTag saved = saved(40);
            CompoundTag before = saved.copy();
            assertNull(access.read(provider, saved));
            assertTrue(access.disabled(provider));
            assertNull(access.read(provider, saved));
            assertEquals(1, calls.get());
            assertEquals(before, saved);
            access.clear();
            assertFalse(access.disabled(provider));
            assertNull(access.read(provider, saved));
            assertEquals(2, calls.get());
        }
    }

    @Test
    void emptyStorageCannotCreateFreeOxygen() {
        var access = new SuitOxygenAccess();
        var provider = provider(data -> OptionalInt.of(2000), WRITE);
        assertNull(access.read(provider, null));
        assertTrue(access.disabled(provider));
    }

    @Test
    void emptyReadIsNotAProviderFaultEvenForAnUninitializedItem() {
        var access = new SuitOxygenAccess();
        var provider = provider(data -> OptionalInt.empty(), WRITE);
        assertNull(access.read(provider, null));
        assertFalse(access.disabled(provider));
    }

    @Test
    void readMutationAndCyclicMutationAreBoundedBeforeEqualityAndPreserveSavedData() {
        for (boolean cyclic : List.of(false, true)) {
            var provider = provider(data -> {
                if (cyclic) { data.put("cycle", data); } else { data.putInt("oxygen", 2000); }
                return OptionalInt.of(40);
            }, WRITE);
            var access = new SuitOxygenAccess();
            CompoundTag data = saved(40);
            CompoundTag before = data.copy();
            assertNull(access.read(provider, data));
            assertTrue(access.disabled(provider));
            assertEquals(before, data);
        }
    }

    @Test
    void rejectedWritesCannotChangeOriginalAuthority() {
        List<BiFunction<CompoundTag, Integer, CompoundTag>> invalid = List.of(
                (data, units) -> null,
                (data, units) -> { data.putInt("oxygen", units + 1); return data; },
                (data, units) -> { data.putString("oxygen", "bad"); return data; },
                (data, units) -> { data.putByteArray("huge", new byte[16384]); return data; },
                (data, units) -> { data.put("cycle", data); return data; },
                (data, units) -> { data.putInt("oxygen", 0); throw new IllegalArgumentException("not logged"); });
        for (var callback : invalid) {
            var provider = provider(READ, callback);
            var access = new SuitOxygenAccess();
            CompoundTag saved = saved(40);
            CompoundTag before = saved.copy();
            assertNull(access.prepare(access.read(provider, saved), 39));
            assertTrue(access.disabled(provider));
            assertEquals(before, saved);
        }
    }

    @Test
    void returnedTimeLimitAppliesToReadWriteAndPostWriteReadback() {
        for (int slowPhase = 0; slowPhase < 3; slowPhase++) {
            AtomicLong time = new AtomicLong();
            AtomicInteger calls = new AtomicInteger();
            int selected = slowPhase;
            Runnable step = () -> { if (calls.getAndIncrement() == selected) { time.addAndGet(5_000_001); } };
            var provider = provider(data -> { step.run(); return READ.apply(data); },
                    (data, units) -> { step.run(); return WRITE.apply(data, units); });
            var access = new SuitOxygenAccess(time::get);
            var read = access.read(provider, saved(40));
            if (slowPhase == 0) { assertNull(read); } else { assertNull(access.prepare(read, 39)); }
            assertTrue(access.disabled(provider));
        }
    }

    @Test
    void exactReturnedTimeAndOxygenBoundsAreAccepted() {
        AtomicLong time = new AtomicLong();
        var provider = provider(data -> { time.addAndGet(5_000_000); return READ.apply(data); }, WRITE);
        var access = new SuitOxygenAccess(time::get);
        var read = access.read(provider, saved(2000));
        assertNotNull(read);
        assertEquals(0, access.read(provider, access.prepare(read, 0)).oxygen());
        assertNull(access.prepare(read, -1));
        assertNull(access.prepare(read, 2001));
        assertFalse(access.disabled(provider));
    }

    @Test
    void recursiveReadAndClearAreRejectedWithoutPublishingData() {
        for (boolean clear : List.of(false, true)) {
            var access = new SuitOxygenAccess();
            var other = provider(READ, WRITE);
            var provider = provider(data -> {
                if (clear) { access.clear(); } else { access.read(other, null); }
                return READ.apply(data);
            }, WRITE);
            assertNull(access.read(provider, saved(40)));
            assertTrue(access.disabled(provider));
        }
    }

    @Test
    void preflightBoundsBytesNodesDepthArraysAndCyclesBeforeCopying() {
        CompoundTag exactBytes = new CompoundTag();
        exactBytes.putByteArray("a", new byte[16372]);
        assertTrue(SuitOxygenPayloads.bounded(exactBytes));
        exactBytes.putByteArray("a", new byte[16373]);
        assertFalse(SuitOxygenPayloads.bounded(exactBytes));
        CompoundTag root = new CompoundTag();
        for (int index = 0; index < 255; index++) { root.putInt("n" + index, index); }
        assertTrue(SuitOxygenPayloads.bounded(root));
        root.putInt("overflow", 0);
        assertFalse(SuitOxygenPayloads.bounded(root));
        root = new CompoundTag();
        CompoundTag tail = root;
        for (int depth = 1; depth < 16; depth++) {
            CompoundTag next = new CompoundTag(); tail.put("child", next); tail = next;
        }
        assertTrue(SuitOxygenPayloads.bounded(root));
        tail.putInt("overflow", 1);
        assertFalse(SuitOxygenPayloads.bounded(root));
        root = new CompoundTag();
        root.put("cycle", root);
        assertFalse(SuitOxygenPayloads.bounded(root));
        CompoundTag listRoot = new CompoundTag();
        ListTag list = new ListTag();
        for (int index = 0; index < 256; index++) { list.add(IntTag.valueOf(index)); }
        listRoot.put("list", list);
        assertFalse(SuitOxygenPayloads.bounded(listRoot));
        for (boolean longs : List.of(false, true)) {
            CompoundTag arrays = new CompoundTag();
            if (longs) { arrays.putLongArray("array", new long[2049]); }
            else { arrays.putIntArray("array", new int[4097]); }
            assertFalse(SuitOxygenPayloads.bounded(arrays));
        }
    }

    @Test
    void envelopeBudgetIncludesMetadataNotOnlyProviderBody() {
        CompoundTag large = new CompoundTag();
        large.putByteArray("a", new byte[16372]);
        assertTrue(SuitOxygenPayloads.bounded(large));
        assertNull(SuitOxygenPayloads.encode(provider(READ, WRITE), large));
    }

    private static CompoundTag saved(int units) {
        return SuitOxygenPayloads.encode(provider(READ, WRITE), WRITE.apply(new CompoundTag(), units));
    }

    private static SuitEquipmentCatalog.Provider provider(Function<CompoundTag, OptionalInt> read,
            BiFunction<CompoundTag, Integer, CompoundTag> write) {
        return new SuitEquipmentCatalog.Provider(ResourceLocation.tryParse("fixture:suit"), 2,
                new SuitOxygenProvider() {
                    public OptionalInt readOxygen(CompoundTag data) { return read.apply(data); }
                    public CompoundTag writeOxygen(CompoundTag data, int units) { return write.apply(data, units); }
                });
    }
}
