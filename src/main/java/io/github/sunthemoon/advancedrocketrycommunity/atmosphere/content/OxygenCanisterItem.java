package io.github.sunthemoon.advancedrocketrycommunity.atmosphere.content;

import io.github.sunthemoon.advancedrocketrycommunity.atmosphere.life.OxygenTransferResult;
import io.github.sunthemoon.advancedrocketrycommunity.atmosphere.server.SuitEquipmentRuntime;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
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
    public net.minecraftforge.common.capabilities.ICapabilityProvider initCapabilities(
            ItemStack stack, net.minecraft.nbt.CompoundTag nbt) {
        return io.github.sunthemoon.advancedrocketrycommunity.fluid.GasCanisterCapabilities.create(stack);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack held = player.getItemInHand(hand);
        ItemStack chest = player.getItemBySlot(EquipmentSlot.CHEST);
        if (!held.is(this) || !SuitEquipmentRuntime.isChest(chest)) {
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

        if (!(player instanceof ServerPlayer serverPlayer)) {
            return InteractionResultHolder.fail(held);
        }
        OxygenTransferResult transfer = SuitEquipmentRuntime.fillOneCanister(serverPlayer, hand);
        if (!transfer.accepted()) {
            player.displayClientMessage(Component.translatable(
                    "message.advancedrocketrycommunity.oxygen.no_capacity"
            ), true);
            return InteractionResultHolder.fail(held);
        }
        player.displayClientMessage(Component.translatable(
                "message.advancedrocketrycommunity.oxygen.suit_refilled",
                transfer.oxygenUnits()
        ), true);
        return InteractionResultHolder.consume(held);
    }
}
