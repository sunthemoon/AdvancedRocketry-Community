package io.github.sunthemoon.advancedrocketrycommunity.fluid;

import io.github.sunthemoon.advancedrocketrycommunity.atmosphere.server.SuitEquipmentRuntime;
import io.github.sunthemoon.advancedrocketrycommunity.registry.ModItems;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/** Item-use intent only; actual gas, destination and resources are server-owned. */
public final class TankCanisterUse {
    public static InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack held = player.getItemInHand(hand);
        if (!held.is(ModItems.OXYGEN_CANISTER.get()) && !held.is(ModItems.HYDROGEN_CANISTER.get())) { return InteractionResultHolder.pass(held); }
        if (level.isClientSide) { return InteractionResultHolder.success(held); }
        boolean accepted = player instanceof ServerPlayer server && SuitEquipmentRuntime.fillTankCanister(server, hand);
        player.displayClientMessage(Component.translatable("message.advancedrocketrycommunity.gas_charge_pad."
                + (accepted ? "refilled" : "refused")), true);
        return accepted ? InteractionResultHolder.consume(player.getItemInHand(hand)) : InteractionResultHolder.fail(held);
    }
    private TankCanisterUse() { }
}
