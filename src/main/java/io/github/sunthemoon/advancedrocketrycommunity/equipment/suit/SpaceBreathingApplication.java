package io.github.sunthemoon.advancedrocketrycommunity.equipment.suit;

import io.github.sunthemoon.advancedrocketrycommunity.atmosphere.content.SpaceSuitArmorItem;
import java.util.Optional;
import java.util.function.Predicate;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;

/**
 * ADR-066 §5.1: the C16 chemical recipe is the only source of {@code space_breathing} level 1. The armor piece
 * keeps its damage, name, enchantments and unknown tags; only the one enchantment entry is added.
 */
public final class SpaceBreathingApplication {
    public enum Refusal {
        UNSUPPORTED_STACK, NOT_NATIVE_ARMOR, WRONG_SLOT, BUILT_IN_SUIT, PROVIDER_CLAIMED,
        ALREADY_SEALED, NOT_APPLICABLE, INCOMPATIBLE_ENCHANTMENT, UNSUPPORTED_OXYGEN_DATA
    }

    /** {@code claimed} names items an ADR-025 provider owns; the enchantment never aliases their roots. */
    public static Optional<Refusal> refusal(ItemStack stack, EquipmentSlot slot, Enchantment enchantment,
                                            Predicate<Item> claimed) {
        if (stack.isEmpty() || !EquipmentNbt.safe(stack)) { return Optional.of(Refusal.UNSUPPORTED_STACK); }
        if (!(stack.getItem() instanceof ArmorItem armor)) { return Optional.of(Refusal.NOT_NATIVE_ARMOR); }
        if (armor.getEquipmentSlot() != slot) { return Optional.of(Refusal.WRONG_SLOT); }
        if (armor instanceof SpaceSuitArmorItem) { return Optional.of(Refusal.BUILT_IN_SUIT); }
        if (claimed.test(armor)) { return Optional.of(Refusal.PROVIDER_CLAIMED); }
        var existing = EnchantmentHelper.getEnchantments(stack);
        if (existing.containsKey(enchantment)) { return Optional.of(Refusal.ALREADY_SEALED); }
        // Forge routes Enchantment.canEnchant to the table check, which this enchantment refuses by design.
        if (!enchantment.category.canEnchant(armor)) { return Optional.of(Refusal.NOT_APPLICABLE); }
        if (!EnchantmentHelper.isEnchantmentCompatible(existing.keySet(), enchantment)) {
            return Optional.of(Refusal.INCOMPATIBLE_ENCHANTMENT);
        }
        // An unsupported enchanted-chest oxygen root would become an unusable sealed chest; refuse and preserve it.
        if (slot == EquipmentSlot.CHEST && EnchantedSuitOxygen.read(stack.getTag()) < 0) {
            return Optional.of(Refusal.UNSUPPORTED_OXYGEN_DATA);
        }
        return EquipmentNbt.safe(applied(stack, enchantment)) ? Optional.empty() : Optional.of(Refusal.UNSUPPORTED_STACK);
    }

    /** A detached copy with level 1 added, or null when {@link #refusal} refuses the stack. */
    public static ItemStack apply(ItemStack stack, EquipmentSlot slot, Enchantment enchantment, Predicate<Item> claimed) {
        return refusal(stack, slot, enchantment, claimed).isEmpty() ? applied(stack, enchantment) : null;
    }

    /** W03: a recipe never destroys a tank's gas, so a consumed tank must hold a supported root storing none. */
    public static boolean gasFree(ItemStack stack, int base) {
        if (!EquipmentNbt.safe(stack)) { return false; }
        TankData tank = TankData.read(stack.hasTag() ? stack.getTag().get(TankData.KEY) : null, base, 1);
        return tank != null && tank.stored() == 0;
    }

    private static ItemStack applied(ItemStack stack, Enchantment enchantment) {
        ItemStack result = stack.copy(); result.enchant(enchantment, 1); return result;
    }

    private SpaceBreathingApplication() { }
}
