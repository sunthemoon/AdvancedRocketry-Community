package io.github.sunthemoon.advancedrocketrycommunity.equipment.workstation;

import io.github.sunthemoon.advancedrocketrycommunity.equipment.suit.ArmorEquipment;
import io.github.sunthemoon.advancedrocketrycommunity.equipment.suit.EquipmentNbt;
import io.github.sunthemoon.advancedrocketrycommunity.persistence.BoundedNbt;
import java.util.Set;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.item.ItemStack;

/** Exactly two held stacks, without a second equipment store. */
public final class SuitWorkstationSave {
    public static final String KEY = "arce_suit_workstation";
    public static boolean bounded(Tag root) { return BoundedNbt.fits(root, 65_536, 16, 1_024); }
    public static CompoundTag encode(ItemStack armor, ItemStack transfer) {
        if (!EquipmentNbt.safe(armor) || !EquipmentNbt.safe(transfer)) { return null; }
        CompoundTag root = new CompoundTag(); root.putInt("schema_version", 1);
        root.put("armor", armor.isEmpty() ? new CompoundTag() : armor.serializeNBT());
        root.put("transfer", transfer.isEmpty() ? new CompoundTag() : transfer.serializeNBT());
        return bounded(root) ? root : null;
    }
    public static ItemStack[] decode(Tag raw) {
        if (!(raw instanceof CompoundTag root) || !bounded(root)
                || !root.getAllKeys().equals(Set.of("schema_version", "armor", "transfer"))
                || !root.contains("schema_version", Tag.TAG_INT) || root.getInt("schema_version") != 1
                || !root.contains("armor", Tag.TAG_COMPOUND) || !root.contains("transfer", Tag.TAG_COMPOUND)) { return null; }
        // Preflight both envelopes first: native ItemStack loading may write into the tag it is given.
        CompoundTag[] slots = new CompoundTag[2];
        for (int i = 0; i < 2; i++) {
            CompoundTag saved = root.getCompound(i == 0 ? "armor" : "transfer");
            if (saved.isEmpty()) { continue; }
            if (!EquipmentNbt.bounded(saved) || !Set.of("id", "Count", "tag", "ForgeCaps").containsAll(saved.getAllKeys())
                    || !saved.contains("id", Tag.TAG_STRING) || !saved.contains("Count", Tag.TAG_BYTE)
                    || saved.getByte("Count") != 1 || saved.contains("tag") && !saved.contains("tag", Tag.TAG_COMPOUND)
                    || saved.contains("ForgeCaps") && !saved.contains("ForgeCaps", Tag.TAG_COMPOUND)) { return null; }
            String id = saved.getString("id");
            boolean recognized = i == 0 ? Set.of("advancedrocketrycommunity:space_suit_helmet",
                    "advancedrocketrycommunity:space_suit_chestplate", "advancedrocketrycommunity:space_suit_leggings",
                    "advancedrocketrycommunity:space_suit_boots").contains(id)
                    : io.github.sunthemoon.advancedrocketrycommunity.model.api.equipment.ModuleKind.forItem(id) != null;
            if (!recognized) { return null; }
            slots[i] = saved;
        }
        ItemStack[] result = new ItemStack[2];
        for (int i = 0; i < 2; i++) {
            if (slots[i] == null) { result[i] = ItemStack.EMPTY; continue; }
            // A bounded detached copy, so a later refusal keeps the original raw root exactly.
            result[i] = ItemStack.of(slots[i].copy());
            if (result[i].isEmpty() || !EquipmentNbt.safe(result[i])
                    || i == 0 && ArmorEquipment.part(result[i]) == null
                    || i == 1 && ArmorEquipment.kind(result[i]) == null) { return null; }
        }
        return result;
    }
    private SuitWorkstationSave() { }
}
