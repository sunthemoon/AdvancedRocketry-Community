package io.github.sunthemoon.advancedrocketrycommunity.atmosphere.device;

import io.github.sunthemoon.advancedrocketrycommunity.equipment.suit.EquipmentNbt;
import io.github.sunthemoon.advancedrocketrycommunity.registry.ModItems;
import java.util.Set;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/** One unstackable finite cartridge; unrelated bounded item data survives consumption. */
public final class CarbonCartridge {
    public static final String KEY = "arce_carbon_scrubber_cartridge";
    public static final int MAX_USES = 32_766;
    public static int uses(ItemStack stack) { return uses(stack, ModItems.CARBON_SCRUBBER_CARTRIDGE.get()); }
    static int uses(ItemStack stack, Item cartridge) {
        if (stack.getCount() != 1 || !stack.is(cartridge)
                || !EquipmentNbt.safe(stack)) { return -1; }
        Tag raw = stack.hasTag() ? stack.getTag().get(KEY) : null;
        if (raw == null) { return MAX_USES; }
        if (!(raw instanceof CompoundTag root) || !root.getAllKeys().equals(Set.of("schema_version", "uses"))
                || !root.contains("schema_version", Tag.TAG_INT) || root.getInt("schema_version") != 1
                || !root.contains("uses", Tag.TAG_INT) || root.getInt("uses") < 0 || root.getInt("uses") > MAX_USES) { return -1; }
        return root.getInt("uses");
    }
    public static ItemStack spend(ItemStack stack) {
        int uses = uses(stack);
        return uses <= 0 ? null : withUses(stack, uses - 1);
    }
    public static ItemStack prepare(ItemStack stack) {
        int uses = uses(stack); return uses < 0 ? null : withUses(stack, uses);
    }
    /** ADR-066 §3.1 lossless chemical refresh of a spent cartridge: only its use count changes; null refuses. */
    public static ItemStack refresh(ItemStack stack) { return refresh(stack, ModItems.CARBON_SCRUBBER_CARTRIDGE.get()); }
    static ItemStack refresh(ItemStack stack, Item cartridge) {
        return uses(stack, cartridge) == 0 ? withUses(stack, MAX_USES) : null;
    }
    private static ItemStack withUses(ItemStack stack, int uses) {
        ItemStack result = stack.copy();
        CompoundTag root = new CompoundTag(); root.putInt("schema_version", 1); root.putInt("uses", uses);
        result.getOrCreateTag().put(KEY, root);
        return EquipmentNbt.safe(result) ? result : null;
    }
    public static ItemStack decode(CompoundTag saved) {
        if (saved.isEmpty()) { return ItemStack.EMPTY; }
        if (!EquipmentNbt.bounded(saved) || !Set.of("id", "Count", "tag", "ForgeCaps").containsAll(saved.getAllKeys())
                || !saved.contains("id", Tag.TAG_STRING) || !saved.getString("id").equals("advancedrocketrycommunity:carbon_scrubber_cartridge")
                || !saved.contains("Count", Tag.TAG_BYTE) || saved.getByte("Count") != 1
                || saved.contains("tag") && !saved.contains("tag", Tag.TAG_COMPOUND)
                || saved.contains("ForgeCaps") && !saved.contains("ForgeCaps", Tag.TAG_COMPOUND)) { return null; }
        ItemStack stack = ItemStack.of(saved.copy());
        return uses(stack) >= 0 && stack.serializeNBT().equals(saved) ? stack : null;
    }
    private CarbonCartridge() { }
}
