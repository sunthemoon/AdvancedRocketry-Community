package io.github.sunthemoon.advancedrocketrycommunity.equipment.motion;

import io.github.sunthemoon.advancedrocketrycommunity.atmosphere.content.SpaceSuitArmorItem;
import io.github.sunthemoon.advancedrocketrycommunity.equipment.suit.ArmorEquipment;
import io.github.sunthemoon.advancedrocketrycommunity.model.api.equipment.ModuleKind;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;

/** Whether a built-in suit piece is correctly worn in its own slot with a module installed in a valid root. */
public final class WornModules {
    public static boolean installed(LivingEntity wearer, EquipmentSlot slot, ModuleKind kind) {
        ItemStack stack = wearer.getItemBySlot(slot);
        if (!(stack.getItem() instanceof SpaceSuitArmorItem armor) || armor.getEquipmentSlot() != slot) { return false; }
        var modules = ArmorEquipment.modules(stack);
        return modules != null && modules.stream().anyMatch(module -> module.kind() == kind);
    }
    private WornModules() { }
}
