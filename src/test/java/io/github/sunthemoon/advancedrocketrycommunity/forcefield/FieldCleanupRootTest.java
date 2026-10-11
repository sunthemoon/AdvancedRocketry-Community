package io.github.sunthemoon.advancedrocketrycommunity.forcefield;

import static org.junit.jupiter.api.Assertions.*;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.Level;
import org.junit.jupiter.api.Test;

class FieldCleanupRootTest {
    private static FieldCleanupRoot.Entry entry(int index) {
        return new FieldCleanupRoot.Entry(new FieldIdentity(new UUID(4, index), new UUID(4, 99999), 1,
                Level.OVERWORLD, new BlockPos(index, 80, 0), Direction.EAST), -1, false);
    }
    @Test void reservedClaimsBecomeOrphansWithoutDuplicationAndRoundTripExactly() {
        var root = new FieldCleanupRoot();
        var original = entry(1);
        assertTrue(root.put(original));
        assertTrue(root.orphan(original));
        assertEquals(1, root.size());
        assertTrue(root.get(original.identity().key()).orphan());
        var decoded = FieldCleanupRoot.decode(root.encode());
        assertEquals(root.encode(), decoded.encode());
        assertEquals(FieldCleanupRoot.encodedSize(root.encode()), root.bytes());
        decoded.remove(original.identity().key());
        assertEquals(0, decoded.size());
        assertEquals(FieldCleanupRoot.encodedSize(decoded.encode()), decoded.bytes());
    }
    @Test void fullRootRefusesNewAdmissionAndOrdinaryRetirementWithoutLosingAnyClaim() {
        var root = new FieldCleanupRoot();
        for (int i = 0; i < 4096; i++) { assertTrue(root.put(entry(i))); }
        var before = root.encode();
        assertTrue(root.full());
        assertFalse(root.put(entry(4096)));
        assertFalse(root.orphan(entry(0)));
        assertEquals(before, root.encode());
        assertEquals(4096, FieldCleanupRoot.decode(before).size());
        assertEquals(FieldCleanupRoot.encodedSize(before), root.bytes());
        assertTrue(root.bytes() <= FieldCleanupRoot.MAX_BYTES);
    }
    @Test void aggregateByteBudgetAlsoRefusesBeforeTheCountLimit() {
        var root = new FieldCleanupRoot();
        var longLevel = ResourceKey.create(Registries.DIMENSION, ResourceLocation.tryParse("test:" + "x".repeat(250)));
        int count = 0;
        for (; count < 4096; count++) {
            var id = new FieldIdentity(new UUID(5, count), new UUID(5, 5000), 1, longLevel, BlockPos.ZERO, Direction.DOWN);
            if (!root.put(new FieldCleanupRoot.Entry(id, -1, false))) { break; }
        }
        assertTrue(count < 4096 && count > 0);
        assertEquals(count, root.size());
        assertTrue(root.bytes() <= FieldCleanupRoot.MAX_BYTES);
        assertEquals(root.encode(), FieldCleanupRoot.decode(root.encode()).encode());
    }
    @Test void conflictingIdentitiesDuplicateKeysFutureAndOversizedRootsAreNotNormalized() {
        var root = new FieldCleanupRoot(); assertTrue(root.put(entry(1)));
        var conflict = new FieldCleanupRoot.Entry(new FieldIdentity(entry(1).identity().projector(), UUID.randomUUID(), 1,
                Level.OVERWORLD, BlockPos.ZERO, Direction.UP), 0, false);
        assertFalse(root.put(conflict));
        var duplicate = root.encode(); ((ListTag) duplicate.get("entries")).add(((ListTag) duplicate.get("entries")).get(0).copy());
        assertThrows(IllegalArgumentException.class, () -> FieldCleanupRoot.decode(duplicate));
        var future = root.encode(); future.putInt("schema", 2);
        var saved = FieldCleanupSavedData.load(future);
        assertFalse(saved.operational()); assertEquals(future, saved.save(new CompoundTag()));
        assertFalse(saved.reserve(entry(2).identity(), 1));
        var huge = new CompoundTag(); huge.putByteArray("huge", new byte[FieldCleanupRoot.MAX_BYTES]);
        assertFalse(FieldCleanupRoot.bounded(huge));
        assertSame(huge, FieldCleanupSavedData.load(huge).save(new CompoundTag()));
    }
    @Test void sharedBudgetIncludesInspectionsCooldownAndIndependentDevices() {
        var budget = new FieldWorkBudget();
        for (int i = 0; i < 32; i++) { assertTrue(budget.acquire(entry(i).identity().key(), 100, 2)); }
        assertEquals(64, budget.cells());
        assertFalse(budget.acquire(entry(99).identity().key(), 100, 1));
        assertFalse(budget.acquire(entry(0).identity().key(), 104, 2));
        assertTrue(budget.acquire(entry(0).identity().key(), 105, 2));
        assertFalse(budget.acquire(entry(0).identity().key(), 105, 2));
        assertEquals(2, budget.cells());
    }
}
