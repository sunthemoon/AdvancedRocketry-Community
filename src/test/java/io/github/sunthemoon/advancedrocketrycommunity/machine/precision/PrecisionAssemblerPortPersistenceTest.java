package io.github.sunthemoon.advancedrocketrycommunity.machine.precision;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.sunthemoon.advancedrocketrycommunity.machine.multiblock.lifecycle.persistence.MultiblockNbtStatus;
import io.github.sunthemoon.advancedrocketrycommunity.testsupport.MinecraftBootstrap;
import net.minecraft.nbt.ByteArrayTag;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.IntTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class PrecisionAssemblerPortPersistenceTest {
    @BeforeAll
    static void bootstrap() {
        MinecraftBootstrap.initialize();
    }

    @Test
    void eachPhysicalPortRoundTripsOnlyItsOwnResource() {
        assertPortData(PrecisionAssemblerPortType.ITEM_INPUT, new ItemStack(Items.IRON_INGOT, 3), 0);
        assertPortData(PrecisionAssemblerPortType.ITEM_OUTPUT, new ItemStack(Items.IRON_BARS, 7), 0);
        assertPortData(PrecisionAssemblerPortType.ENERGY_INPUT, ItemStack.EMPTY, 12_345);
    }

    @Test
    void rejectedRootsArePreservedWithoutInterpretation() {
        CompoundTag future = new CompoundTag();
        future.putInt("schema_version", PrecisionAssemblerPortPersistence.SCHEMA_VERSION + 1);
        future.putString("future_payload", "keep");
        var result = PrecisionAssemblerPortPersistence.decode(
                parentWith(future), PrecisionAssemblerPortType.ITEM_INPUT);
        assertEquals(MultiblockNbtStatus.UNSUPPORTED_SCHEMA, result.status());
        assertEquals(future, result.preservedRoot().orElseThrow());
        assertNotSame(future, result.preservedRoot().orElseThrow());
        ((CompoundTag) result.preservedRoot().orElseThrow()).putString("future_payload", "changed");
        assertEquals(future, result.preservedRoot().orElseThrow());

        result = PrecisionAssemblerPortPersistence.decode(
                parentWith(IntTag.valueOf(17)), PrecisionAssemblerPortType.ITEM_INPUT);
        assertEquals(MultiblockNbtStatus.INVALID_DATA, result.status());
        assertEquals(IntTag.valueOf(17), result.preservedRoot().orElseThrow());

        CompoundTag oversized = new CompoundTag();
        oversized.putInt("schema_version", 2);
        oversized.put("payload", new ByteArrayTag(new byte[PrecisionAssemblerPortPersistence.MAX_ROOT_BYTES]));
        result = PrecisionAssemblerPortPersistence.decode(
                parentWith(oversized), PrecisionAssemblerPortType.ITEM_INPUT);
        assertEquals(MultiblockNbtStatus.INVALID_DATA, result.status());
        assertEquals(oversized, result.preservedRoot().orElseThrow());
    }

    @Test
    void mismatchedTypeAndExtraFieldsFailClosed() {
        CompoundTag encoded = PrecisionAssemblerPortPersistence.encode(
                PrecisionAssemblerPortType.ITEM_INPUT, new ItemStack(Items.IRON_INGOT), 0);
        assertEquals(MultiblockNbtStatus.INVALID_DATA,
                PrecisionAssemblerPortPersistence.decode(
                        parentWith(encoded), PrecisionAssemblerPortType.ITEM_OUTPUT).status());
        encoded.putString("unexpected", "field");
        assertEquals(MultiblockNbtStatus.INVALID_DATA,
                PrecisionAssemblerPortPersistence.decode(
                        parentWith(encoded), PrecisionAssemblerPortType.ITEM_INPUT).status());
    }

    @Test
    void taggedItemsMixedResourcesAndExcessEnergyAreRejected() {
        ItemStack tagged = new ItemStack(Items.IRON_INGOT);
        tagged.getOrCreateTag().putString("custom", "blocked");
        assertFalse(PrecisionAssemblerPortPersistence.acceptsItem(tagged));
        assertThrows(IllegalStateException.class, () -> PrecisionAssemblerPortPersistence.encode(
                PrecisionAssemblerPortType.ITEM_INPUT, tagged, 0));
        assertThrows(IllegalStateException.class, () -> PrecisionAssemblerPortPersistence.encode(
                PrecisionAssemblerPortType.ITEM_INPUT, ItemStack.EMPTY, 1));
        assertThrows(IllegalStateException.class, () -> PrecisionAssemblerPortPersistence.encode(
                PrecisionAssemblerPortType.ENERGY_INPUT, new ItemStack(Items.IRON_INGOT), 0));
        assertThrows(IllegalStateException.class, () -> PrecisionAssemblerPortPersistence.encode(
                PrecisionAssemblerPortType.ENERGY_INPUT, ItemStack.EMPTY,
                PrecisionAssemblerPortPersistence.ENERGY_CAPACITY + 1));
    }

    @Test
    void absentRootHasNoResourceOrPreservedPayload() {
        var result = PrecisionAssemblerPortPersistence.decode(
                new CompoundTag(), PrecisionAssemblerPortType.ITEM_INPUT);
        assertEquals(MultiblockNbtStatus.EMPTY, result.status());
        assertTrue(result.value().isEmpty());
        assertTrue(result.preservedRoot().isEmpty());
    }

    private static void assertPortData(PrecisionAssemblerPortType type, ItemStack item, int energy) {
        CompoundTag encoded = PrecisionAssemblerPortPersistence.encode(type, item, energy);
        var result = PrecisionAssemblerPortPersistence.decode(parentWith(encoded), type);
        assertEquals(MultiblockNbtStatus.SUPPORTED, result.status());
        var decoded = result.value().orElseThrow();
        assertTrue(ItemStack.matches(item, decoded.item()));
        assertEquals(energy, decoded.energy());
        ItemStack copy = decoded.item();
        copy.shrink(1);
        assertTrue(ItemStack.matches(item, decoded.item()));
    }

    private static CompoundTag parentWith(Tag root) {
        CompoundTag parent = new CompoundTag();
        parent.put(PrecisionAssemblerPortPersistence.ROOT, root);
        return parent;
    }
}
