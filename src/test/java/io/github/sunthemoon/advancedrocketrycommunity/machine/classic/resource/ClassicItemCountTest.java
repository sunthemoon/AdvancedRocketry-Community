package io.github.sunthemoon.advancedrocketrycommunity.machine.classic.resource;

import static org.junit.jupiter.api.Assertions.*;
import io.github.sunthemoon.advancedrocketrycommunity.testsupport.MinecraftBootstrap;
import java.util.List;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class ClassicItemCountTest {
    private static final ClassicBankKey KEY = new ClassicBankKey(ClassicBankKind.ITEM_INPUT, 1, 2, 3);
    private static final int[] INVALID_COUNTS = {
        257, 319, 320, 65, 127, 128, 255, 256, 511, 512, 513, Integer.MAX_VALUE
    };
    @BeforeAll static void bootstrap() { MinecraftBootstrap.initialize(); }

    @Test void constructorRefusesOriginalCountsBeforeNativeByteNarrowing() {
        for (int count : INVALID_COUNTS) {
            ItemStack stack = tagged(Items.STONE, count);
            CompoundTag metadata = stack.getTag().copy();
            assertFalse(stack.isEmpty()); assertEquals(count, stack.getCount());
            assertThrows(IllegalArgumentException.class,
                    () -> ClassicResourceBank.items(KEY, ClassicResourceBankTest.slots(stack)), "count=" + count);
            assertEquals(count, stack.getCount()); assertEquals(metadata, stack.getTag());
        }
    }

    @Test void replacementRefusalPreservesOriginalBankAndOfferedMetadata() {
        ClassicResourceBank before = ClassicResourceBank.items(KEY, ClassicResourceBankTest.slots(tagged(Items.STONE, 7)));
        CompoundTag snapshot = ClassicResourcesCodec.encode(ClassicResources.empty(new UUID(81, 82))
                .replace(List.of(before), false));
        for (int count : INVALID_COUNTS) {
            ItemStack stack = tagged(Items.STONE, count);
            assertThrows(IllegalArgumentException.class, () -> before.withItem(0, stack), "count=" + count);
            assertEquals(count, stack.getCount()); assertEquals("retained", stack.getTag().getString("batch"));
            assertEquals(7, before.item(0).getCount());
            assertEquals(snapshot, ClassicResourcesCodec.encode(ClassicResources.empty(new UUID(81, 82))
                    .replace(List.of(before), false)));
        }
    }

    @Test void insertionRefusesWrappedOffersInSimulationAndExecutionWithoutChangingResources() {
        ClassicResources before = ClassicResources.empty(new UUID(83, 84))
                .replace(List.of(ClassicResourceBank.items(KEY, ClassicResourceBankTest.slots(tagged(Items.STONE, 7)))), false);
        CompoundTag snapshot = ClassicResourcesCodec.encode(before);
        for (boolean simulate : new boolean[] {true, false}) {
            for (int count : INVALID_COUNTS) {
                ItemStack offer = tagged(Items.STONE, count);
                assertThrows(IllegalArgumentException.class,
                        () -> ClassicBankTransfers.insertItem(before, KEY, 0, offer, simulate),
                        "count=" + count + ", simulate=" + simulate);
                assertEquals(count, offer.getCount()); assertEquals("retained", offer.getTag().getString("batch"));
                assertEquals(snapshot, ClassicResourcesCodec.encode(before)); assertEquals(1, before.revision());
            }
        }
    }

    @Test void nativePerItemLimitsAreAppliedToOriginalCountsAndInclusiveValidCountsRemainLossless() {
        for (Item item : new Item[] {Items.STONE, Items.ENDER_PEARL, Items.IRON_SWORD}) {
            int limit = Math.min(64, new ItemStack(item).getMaxStackSize());
            for (int count : new int[] {1, limit}) {
                ItemStack stack = tagged(item, count);
                ClassicResourceBank bank = ClassicResourceBank.items(KEY, ClassicResourceBankTest.slots(stack));
                assertEquals(count, bank.item(0).getCount()); assertTrue(ItemStack.isSameItemSameTags(stack, bank.item(0)));
                ClassicResources resources = ClassicResources.empty(new UUID(85, 86)).replace(List.of(bank), false);
                ClassicResources decoded = ClassicResourcesCodec.decode(ClassicResourcesCodec.encode(resources), resources.machineId())
                        .value().orElseThrow();
                assertEquals(count, decoded.bank(KEY).orElseThrow().item(0).getCount());
                assertEquals(ClassicResourcesCodec.encode(resources), ClassicResourcesCodec.encode(decoded));
            }
            for (int count : new int[] {limit + 1, 256 + 1, 256 + limit, 256 + limit + 1}) {
                ItemStack stack = tagged(item, count);
                assertThrows(IllegalArgumentException.class,
                        () -> ClassicResourceBank.items(KEY, ClassicResourceBankTest.slots(stack)), "limit=" + limit + ", count=" + count);
                assertEquals(count, stack.getCount()); assertEquals("retained", stack.getTag().getString("batch"));
            }
        }
    }

    private static ItemStack tagged(Item item, int count) {
        return ClassicResourceBankTest.taggedItem(item, count, "retained");
    }
}
