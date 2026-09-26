package io.github.sunthemoon.advancedrocketrycommunity.machine.precision;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.sunthemoon.advancedrocketrycommunity.machine.multiblock.lifecycle.persistence.MultiblockNbtStatus;
import io.github.sunthemoon.advancedrocketrycommunity.testsupport.MinecraftBootstrap;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.nbt.ByteArrayTag;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.IntTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class PrecisionAssemblerResourcePersistenceTest {
    private static final UUID MACHINE_ID = UUID.fromString("43c2f118-98aa-4cb8-9081-a0c107267c8e");

    @BeforeAll
    static void bootstrap() {
        MinecraftBootstrap.initialize();
    }

    @Test
    void sevenItemsRoundTripInBothMigrationPhases() {
        for (var phase : PrecisionAssemblerResourcePersistence.Phase.values()) {
            List<ItemStack> items = emptyItems();
            items.set(0, new ItemStack(Items.IRON_INGOT, 3));
            items.set(5, new ItemStack(Items.IRON_BARS, 7));
            var data = new PrecisionAssemblerResourcePersistence.ResourceData(MACHINE_ID, phase, items);
            CompoundTag encoded = PrecisionAssemblerResourcePersistence.encode(data);
            var decoded = PrecisionAssemblerResourcePersistence.decode(parentWith(encoded), MACHINE_ID);
            assertEquals(MultiblockNbtStatus.SUPPORTED, decoded.status());
            assertEquals(phase, decoded.value().orElseThrow().phase());
            assertEquals(3, decoded.value().orElseThrow().items().get(0).getCount());
            assertEquals(7, decoded.value().orElseThrow().items().get(5).getCount());
            assertTrue(decoded.value().orElseThrow().items().get(6).isEmpty());

            items.get(0).shrink(2);
            ItemStack exposed = decoded.value().orElseThrow().items().get(5);
            exposed.shrink(4);
            assertEquals(3, decoded.value().orElseThrow().items().get(0).getCount());
            assertEquals(7, decoded.value().orElseThrow().items().get(5).getCount());
        }
    }

    @Test
    void missingAndFutureRootsDoNotInventResources() {
        var empty = PrecisionAssemblerResourcePersistence.decode(new CompoundTag(), MACHINE_ID);
        assertEquals(MultiblockNbtStatus.EMPTY, empty.status());
        assertTrue(empty.value().isEmpty());

        CompoundTag future = new CompoundTag();
        future.putInt("schema_version", 2);
        future.putString("future_payload", "keep");
        var decoded = PrecisionAssemblerResourcePersistence.decode(parentWith(future), MACHINE_ID);
        assertEquals(MultiblockNbtStatus.UNSUPPORTED_SCHEMA, decoded.status());
        assertEquals(future, decoded.preservedRoot().orElseThrow());
        assertNotSame(future, decoded.preservedRoot().orElseThrow());
        ((CompoundTag) decoded.preservedRoot().orElseThrow()).putString("future_payload", "changed");
        assertEquals(future, decoded.preservedRoot().orElseThrow());
    }

    @Test
    void mismatchedOwnerPhaseAndShapeAreRejectedWithoutRewriting() {
        CompoundTag valid = PrecisionAssemblerResourcePersistence.encode(
                new PrecisionAssemblerResourcePersistence.ResourceData(
                        MACHINE_ID, PrecisionAssemblerResourcePersistence.Phase.ACTIVE, emptyItems()));
        assertRejected(valid, UUID.randomUUID());

        CompoundTag invalid = valid.copy();
        invalid.putString("phase", "unknown");
        assertRejected(invalid, MACHINE_ID);
        invalid = valid.copy();
        invalid.putString("extra", "blocked");
        assertRejected(invalid, MACHINE_ID);
        invalid = valid.copy();
        invalid.remove("items");
        assertRejected(invalid, MACHINE_ID);
        invalid = valid.copy();
        invalid.putString("machine_id", "not-a-uuid");
        assertRejected(invalid, MACHINE_ID);
        invalid = valid.copy();
        ListTag shortItems = new ListTag();
        shortItems.add(new CompoundTag());
        invalid.put("items", shortItems);
        assertRejected(invalid, MACHINE_ID);
        invalid = valid.copy();
        ListTag wrongType = new ListTag();
        for (int index = 0; index < PrecisionAssemblerResourcePersistence.ITEM_COUNT; index++) {
            wrongType.add(IntTag.valueOf(index));
        }
        invalid.put("items", wrongType);
        assertRejected(invalid, MACHINE_ID);
    }

    @Test
    void invalidAndOversizedItemsAreRejected() {
        CompoundTag valid = PrecisionAssemblerResourcePersistence.encode(
                new PrecisionAssemblerResourcePersistence.ResourceData(
                        MACHINE_ID, PrecisionAssemblerResourcePersistence.Phase.ACTIVE, emptyItems()));
        CompoundTag tagged = new ItemStack(Items.IRON_INGOT).save(new CompoundTag());
        tagged.put("tag", new CompoundTag());
        CompoundTag invalid = valid.copy();
        invalid.getList("items", Tag.TAG_COMPOUND).set(0, tagged);
        assertRejected(invalid, MACHINE_ID);

        invalid = valid.copy();
        invalid.put("oversized", new ByteArrayTag(new byte[PrecisionAssemblerResourcePersistence.MAX_ROOT_BYTES]));
        var oversized = PrecisionAssemblerResourcePersistence.decode(parentWith(invalid), MACHINE_ID);
        assertEquals(MultiblockNbtStatus.INVALID_DATA, oversized.status());
        assertEquals(invalid, oversized.preservedRoot().orElseThrow());

        List<ItemStack> badItems = emptyItems();
        ItemStack bad = new ItemStack(Items.IRON_INGOT);
        bad.getOrCreateTag().putString("custom", "blocked");
        badItems.set(0, bad);
        assertThrows(IllegalArgumentException.class, () ->
                new PrecisionAssemblerResourcePersistence.ResourceData(
                        MACHINE_ID, PrecisionAssemblerResourcePersistence.Phase.ACTIVE, badItems));
    }

    private static List<ItemStack> emptyItems() {
        return new ArrayList<>(java.util.Collections.nCopies(
                PrecisionAssemblerResourcePersistence.ITEM_COUNT, ItemStack.EMPTY));
    }

    private static CompoundTag parentWith(Tag root) {
        CompoundTag parent = new CompoundTag();
        parent.put(PrecisionAssemblerResourcePersistence.ROOT, root);
        return parent;
    }

    private static void assertRejected(CompoundTag root, UUID expectedMachineId) {
        var result = PrecisionAssemblerResourcePersistence.decode(parentWith(root), expectedMachineId);
        assertEquals(MultiblockNbtStatus.INVALID_DATA, result.status());
        assertEquals(root, result.preservedRoot().orElseThrow());
    }
}
