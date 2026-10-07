package io.github.sunthemoon.advancedrocketrycommunity.machine.classic.adapter;

import static org.junit.jupiter.api.Assertions.*;
import java.util.Set;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.IntArrayTag;
import net.minecraft.nbt.IntTag;
import net.minecraft.nbt.StringTag;
import org.junit.jupiter.api.Test;

class ClassicPowerCarrierFormatTest {
    @Test void readyParsesInclusiveEnergyEndpointsWithoutPendingIdentities() {
        for (int energy : new int[] {0, 1, 5_000, 10_000}) {
            var result = ClassicPowerCarrierFormat.read(ready(energy)).orElseThrow();
            assertEquals(ClassicPowerCarrierFormat.Phase.READY, result.phase());
            assertEquals(energy, result.energy());
            assertTrue(result.operationId().isEmpty()); assertTrue(result.entityId().isEmpty());
        }
    }

    @Test void pendingDecodesAllSignedUuidWordsAsDetachedFormatValues() {
        int[] operation = {Integer.MIN_VALUE, -1, 0x12345678, Integer.MAX_VALUE};
        int[] entity = {-1, Integer.MIN_VALUE, Integer.MAX_VALUE, -1};
        CompoundTag root = pending(10_000, operation, entity);
        var result = ClassicPowerCarrierFormat.read(root).orElseThrow();
        assertEquals(ClassicPowerCarrierFormat.Phase.PENDING, result.phase());
        assertEquals(10_000, result.energy());
        assertEquals(new UUID(0x80000000ffffffffL, 0x123456787fffffffL), result.operationId().orElseThrow());
        assertEquals(new UUID(0xffffffff80000000L, 0x7fffffffffffffffL), result.entityId().orElseThrow());
        operation[0] = 0; entity[0] = 0;
        assertEquals(new UUID(0x80000000ffffffffL, 0x123456787fffffffL), result.operationId().orElseThrow());
        assertEquals(new UUID(0xffffffff80000000L, 0x7fffffffffffffffL), result.entityId().orElseThrow());
    }

    @Test void absentAndWrongRootShapesDoNotMeanAnEmptyItem() {
        assertTrue(ClassicPowerCarrierFormat.read(null).isEmpty());
        assertTrue(ClassicPowerCarrierFormat.read(new CompoundTag()).isEmpty());
        assertTrue(ClassicPowerCarrierFormat.read(IntTag.valueOf(0)).isEmpty());
        assertTrue(ClassicPowerCarrierFormat.read(StringTag.valueOf("ready")).isEmpty());
    }

    @Test void allRequiredKeysMustBePresentAndUnknownKeysRefuse() {
        for (String key : Set.of("schema_version", "phase", "energy")) {
            CompoundTag root = ready(0); root.remove(key);
            assertTrue(ClassicPowerCarrierFormat.read(root).isEmpty(), key);
        }
        CompoundTag root = ready(0); root.putInt("extra", 1);
        assertTrue(ClassicPowerCarrierFormat.read(root).isEmpty());
        root = pending(1, new int[4], new int[4]); root.putInt("extra", 1);
        assertTrue(ClassicPowerCarrierFormat.read(root).isEmpty());
    }

    @Test void exactIntegerFieldsRejectNumericCoercionAndStringValues() {
        for (String key : Set.of("schema_version", "energy")) {
            CompoundTag root = ready(1); root.putByte(key, (byte) 1);
            assertTrue(ClassicPowerCarrierFormat.read(root).isEmpty());
            root = ready(1); root.putShort(key, (short) 1);
            assertTrue(ClassicPowerCarrierFormat.read(root).isEmpty());
            root = ready(1); root.putLong(key, 1);
            assertTrue(ClassicPowerCarrierFormat.read(root).isEmpty());
            root = ready(1); root.putFloat(key, 1);
            assertTrue(ClassicPowerCarrierFormat.read(root).isEmpty());
            root = ready(1); root.putString(key, "1");
            assertTrue(ClassicPowerCarrierFormat.read(root).isEmpty());
        }
    }

    @Test void unknownAndFutureSchemasDoNotNormalize() {
        for (int schema : new int[] {-1, 0, 2, Integer.MAX_VALUE}) {
            CompoundTag root = ready(0); root.putInt("schema_version", schema);
            assertTrue(ClassicPowerCarrierFormat.read(root).isEmpty()); assertEquals(schema, root.getInt("schema_version"));
        }
    }

    @Test void unsupportedEnergyDoesNotClamp() {
        for (int energy : new int[] {Integer.MIN_VALUE, -1, 10_001, Integer.MAX_VALUE}) {
            CompoundTag root = ready(energy);
            assertTrue(ClassicPowerCarrierFormat.read(root).isEmpty()); assertEquals(energy, root.getInt("energy"));
        }
    }

    @Test void onlyExactLowercaseNativeStringPhasesAreAccepted() {
        for (String phase : new String[] {"", "READY", "Pending", "ready ", "pending\u0000", "future"}) {
            CompoundTag root = ready(0); root.putString("phase", phase);
            assertTrue(ClassicPowerCarrierFormat.read(root).isEmpty()); assertEquals(phase, root.getString("phase"));
        }
        CompoundTag root = ready(0); root.putInt("phase", 0);
        assertTrue(ClassicPowerCarrierFormat.read(root).isEmpty());
    }

    @Test void readyAndPendingIdentityPresenceIsExact() {
        for (String key : Set.of("operation_id", "entity_id")) {
            CompoundTag root = ready(0); root.putIntArray(key, new int[4]);
            assertTrue(ClassicPowerCarrierFormat.read(root).isEmpty());
            root = pending(0, new int[4], new int[4]); root.remove(key);
            assertTrue(ClassicPowerCarrierFormat.read(root).isEmpty());
        }
        CompoundTag root = pending(0, new int[4], new int[4]); root.putString("phase", "ready");
        assertTrue(ClassicPowerCarrierFormat.read(root).isEmpty());
        root = ready(0); root.putString("phase", "pending");
        assertTrue(ClassicPowerCarrierFormat.read(root).isEmpty());
    }

    @Test void eachIdentityMustBeAnExactFourWordIntArray() {
        for (String key : Set.of("operation_id", "entity_id")) {
            for (int length : new int[] {0, 1, 3, 5, 250}) {
                CompoundTag root = pending(0, new int[4], new int[4]); root.putIntArray(key, new int[length]);
                assertTrue(ClassicPowerCarrierFormat.read(root).isEmpty());
            }
            CompoundTag root = pending(0, new int[4], new int[4]); root.putLongArray(key, new long[4]);
            assertTrue(ClassicPowerCarrierFormat.read(root).isEmpty());
            root = pending(0, new int[4], new int[4]); root.putString(key, new UUID(0, 0).toString());
            assertTrue(ClassicPowerCarrierFormat.read(root).isEmpty());
        }
    }

    @Test void nullNativeArrayBackingIsRefusedWithoutThrowing() {
        CompoundTag root = pending(0, new int[4], new int[4]);
        root.put("operation_id", new IntArrayTag((int[]) null));
        assertTrue(ClassicPowerCarrierFormat.read(root).isEmpty());
    }

    @Test void oversizedCyclicAndForeignShapeInputsFailClosed() {
        CompoundTag oversized = ready(0); oversized.putString("extra", "x".repeat(1_024));
        assertTrue(ClassicPowerCarrierFormat.read(oversized).isEmpty());
        CompoundTag cyclic = ready(0); cyclic.put("extra", cyclic);
        assertTrue(ClassicPowerCarrierFormat.read(cyclic).isEmpty());
        CompoundTag foreign = new CompoundTag() {
            @Override public Set<String> getAllKeys() { throw new AssertionError("Foreign callback"); }
        };
        assertTrue(ClassicPowerCarrierFormat.read(foreign).isEmpty());
        CompoundTag nested = ready(0); nested.put("extra", foreign);
        assertTrue(ClassicPowerCarrierFormat.read(nested).isEmpty());
    }

    @Test void parsingNeitherMutatesNorRetainsNativeTagsOrArrays() {
        CompoundTag root = pending(73, new int[] {1, 2, 3, 4}, new int[] {5, 6, 7, 8});
        CompoundTag before = root.copy();
        var result = ClassicPowerCarrierFormat.read(root).orElseThrow();
        assertEquals(before, root); assertEquals(5, root.size());
        root.putInt("energy", 91); root.getIntArray("operation_id")[0] = 17;
        assertEquals(73, result.energy());
        assertEquals(new UUID(0x0000000100000002L, 0x0000000300000004L), result.operationId().orElseThrow());
    }

    private static CompoundTag ready(int energy) {
        CompoundTag value = new CompoundTag();
        value.putInt("schema_version", 1); value.putString("phase", "ready"); value.putInt("energy", energy);
        return value;
    }

    private static CompoundTag pending(int energy, int[] operation, int[] entity) {
        CompoundTag value = ready(energy); value.putString("phase", "pending");
        value.putIntArray("operation_id", operation); value.putIntArray("entity_id", entity);
        return value;
    }
}
