package io.github.sunthemoon.advancedrocketrycommunity.atmosphere.content;

import io.github.sunthemoon.advancedrocketrycommunity.atmosphere.AtmosphereLimits;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ArmorMaterial;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

public final class SpaceSuitArmorItem extends ArmorItem {
    public SpaceSuitArmorItem(ArmorMaterial material, Type type, Properties properties) {
        super(material, type, properties);
    }

    public static int countEquippedPieces(LivingEntity entity) {
        int count = 0;
        for (EquipmentSlot slot : new EquipmentSlot[]{
                EquipmentSlot.HEAD,
                EquipmentSlot.CHEST,
                EquipmentSlot.LEGS,
                EquipmentSlot.FEET
        }) {
            if (entity.getItemBySlot(slot).getItem() instanceof SpaceSuitArmorItem armor
                    && armor.getEquipmentSlot() == slot) {
                count++;
            }
        }
        return count;
    }

    @Override
    public void appendHoverText(
            ItemStack stack,
            Level level,
            List<Component> tooltip,
            TooltipFlag flag
    ) {
        super.appendHoverText(stack, level, tooltip, flag);
        if (getType() != Type.CHESTPLATE) {
            return;
        }
        SpaceSuitOxygen.ReadResult oxygen = SpaceSuitOxygen.read(stack);
        if (oxygen.status() == SpaceSuitOxygen.DataStatus.VALID) {
            tooltip.add(Component.translatable(
                    "tooltip.advancedrocketrycommunity.suit_oxygen",
                    oxygen.oxygenUnits(),
                    AtmosphereLimits.SUIT_OXYGEN_CAPACITY
            ).withStyle(ChatFormatting.AQUA));
        } else {
            tooltip.add(Component.translatable(
                    oxygen.status() == SpaceSuitOxygen.DataStatus.FUTURE
                            ? "tooltip.advancedrocketrycommunity.suit_oxygen_future"
                            : "tooltip.advancedrocketrycommunity.suit_oxygen_invalid"
            ).withStyle(ChatFormatting.RED));
        }
    }
}
