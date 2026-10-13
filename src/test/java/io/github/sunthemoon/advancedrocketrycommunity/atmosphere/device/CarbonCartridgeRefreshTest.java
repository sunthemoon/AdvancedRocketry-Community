package io.github.sunthemoon.advancedrocketrycommunity.atmosphere.device;

import static org.junit.jupiter.api.Assertions.*;

import io.github.sunthemoon.advancedrocketrycommunity.testsupport.MinecraftBootstrap;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/** ADR-066 §3.1 lossless refresh; an unstackable vanilla Item stands in for the registered cartridge. */
class CarbonCartridgeRefreshTest {
    private static Item cartridge;

    @BeforeAll static void bootstrap() { MinecraftBootstrap.initialize(); cartridge = Items.SADDLE; }

    private static ItemStack withUses(int uses) {
        ItemStack stack = new ItemStack(cartridge);
        CompoundTag root = new CompoundTag(); root.putInt("schema_version", 1); root.putInt("uses", uses);
        stack.getOrCreateTag().put(CarbonCartridge.KEY, root);
        return stack;
    }

    @Test void spentCartridgeRefreshKeepsEveryOtherTagIncludingUnknownData() {
        ItemStack spent = withUses(0);
        spent.setHoverName(Component.literal("Vent 7 cartridge"));
        CompoundTag unknown = new CompoundTag(); unknown.putString("serial", "A-17"); unknown.putLong("installed", 42L);
        ListTag notes = new ListTag(); notes.add(StringTag.valueOf("first")); unknown.put("notes", notes);
        spent.getOrCreateTag().put("other_mod_data", unknown);
        spent.getOrCreateTag().putInt("RepairCost", 3);
        CompoundTag before = spent.serializeNBT();

        ItemStack refreshed = CarbonCartridge.refresh(spent, cartridge);

        assertNotNull(refreshed);
        assertNotSame(spent, refreshed);
        assertEquals(before, spent.serializeNBT(), "The input stack must stay unchanged");
        assertEquals(CarbonCartridge.MAX_USES, CarbonCartridge.uses(refreshed, cartridge));
        assertEquals(1, refreshed.getCount());
        assertSame(cartridge, refreshed.getItem());
        // Only the use count differs: replace it in the original and the full stacks are equal.
        CompoundTag expected = before.copy();
        expected.getCompound("tag").getCompound(CarbonCartridge.KEY).putInt("uses", CarbonCartridge.MAX_USES);
        assertEquals(expected, refreshed.serializeNBT());
        assertEquals("Vent 7 cartridge", refreshed.getHoverName().getString());
    }

    @Test void refreshRoundTripsThroughNativeSaveAndLoad() {
        ItemStack spent = withUses(0); spent.getOrCreateTag().putString("unknown", "kept");
        ItemStack refreshed = CarbonCartridge.refresh(spent, cartridge);
        ItemStack loaded = ItemStack.of(refreshed.serializeNBT());
        assertTrue(ItemStack.matches(refreshed, loaded));
        assertEquals(CarbonCartridge.MAX_USES, CarbonCartridge.uses(loaded, cartridge));
        assertEquals("kept", loaded.getTag().getString("unknown"));
    }

    @Test void onlyAFullySpentSupportedSingleCartridgeIsRefreshed() {
        assertNull(CarbonCartridge.refresh(new ItemStack(cartridge), cartridge), "A fresh cartridge has nothing to refresh");
        assertNull(CarbonCartridge.refresh(withUses(1), cartridge), "Legacy refreshed only fully spent cartridges");
        assertNull(CarbonCartridge.refresh(withUses(CarbonCartridge.MAX_USES), cartridge));
        assertNull(CarbonCartridge.refresh(new ItemStack(Items.DIAMOND_PICKAXE), cartridge), "Another Item is never refreshed");

        ItemStack future = withUses(0); future.getTag().getCompound(CarbonCartridge.KEY).putInt("schema_version", 2);
        CompoundTag futureBefore = future.serializeNBT();
        assertNull(CarbonCartridge.refresh(future, cartridge), "An unsupported root is preserved, not refreshed");
        assertEquals(futureBefore, future.serializeNBT());

        ItemStack extraField = withUses(0); extraField.getTag().getCompound(CarbonCartridge.KEY).putInt("bonus", 1);
        assertNull(CarbonCartridge.refresh(extraField, cartridge));

        ItemStack wrongType = new ItemStack(cartridge); wrongType.getOrCreateTag().putString(CarbonCartridge.KEY, "spent");
        assertNull(CarbonCartridge.refresh(wrongType, cartridge));

        ItemStack oversized = withUses(0);
        oversized.getTag().putString("padding", "x".repeat(20_000));
        assertNull(CarbonCartridge.refresh(oversized, cartridge), "Unbounded item data is refused");
    }

    @Test void aStackOfMoreThanOneIsRefused() {
        ItemStack stack = new ItemStack(Items.IRON_INGOT, 2);
        CompoundTag root = new CompoundTag(); root.putInt("schema_version", 1); root.putInt("uses", 0);
        stack.getOrCreateTag().put(CarbonCartridge.KEY, root);
        assertNull(CarbonCartridge.refresh(stack, Items.IRON_INGOT));
    }
}
