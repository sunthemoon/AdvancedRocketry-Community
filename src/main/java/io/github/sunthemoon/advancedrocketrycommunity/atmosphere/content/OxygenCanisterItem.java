package io.github.sunthemoon.advancedrocketrycommunity.atmosphere.content;

import io.github.sunthemoon.advancedrocketrycommunity.atmosphere.life.OxygenTransferResult;
import io.github.sunthemoon.advancedrocketrycommunity.registry.ModItems;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/** Existing Electrolyzer oxygen output, now usable as an atomic suit refill. */
public final class OxygenCanisterItem extends Item {
    public OxygenCanisterItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack held = player.getItemInHand(hand);
        ItemStack chest = player.getItemBySlot(EquipmentSlot.CHEST);
        if (!(chest.getItem() instanceof SpaceSuitArmorItem armor)
                || armor.getType() != net.minecraft.world.item.ArmorItem.Type.CHESTPLATE) {
            if (!level.isClientSide) {
                player.displayClientMessage(Component.translatable(
                        "message.advancedrocketrycommunity.oxygen.requires_chestplate"
                ), true);
            }
            return InteractionResultHolder.fail(held);
        }
        if (level.isClientSide) {
            return InteractionResultHolder.success(held);
        }

        OxygenTransferResult transfer = SpaceSuitOxygen.fillOneCanister(chest);
        if (!transfer.accepted()) {
            player.displayClientMessage(Component.translatable(
                    "message.advancedrocketrycommunity.oxygen.no_capacity"
            ), true);
            return InteractionResultHolder.fail(held);
        }
        if (!player.getAbilities().instabuild) {
            held.shrink(1);
            ItemStack empty = new ItemStack(ModItems.EMPTY_CANISTER.get());
            if (!player.getInventory().add(empty)) {
                player.drop(empty, false);
            }
        }
        player.displayClientMessage(Component.translatable(
                "message.advancedrocketrycommunity.oxygen.suit_refilled",
                transfer.oxygenUnits()
        ), true);
        return InteractionResultHolder.consume(held);
    }
}
