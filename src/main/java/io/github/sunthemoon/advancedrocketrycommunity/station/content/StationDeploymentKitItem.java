package io.github.sunthemoon.advancedrocketrycommunity.station.content;

import io.github.sunthemoon.advancedrocketrycommunity.station.service.StationCreationResult;
import io.github.sunthemoon.advancedrocketrycommunity.station.service.StationRuntime;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/** Consumable server-authoritative request to deploy one remote Space station. */
public final class StationDeploymentKitItem extends Item {
    public StationDeploymentKitItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (level.isClientSide()) {
            return InteractionResultHolder.sidedSuccess(stack, true);
        }
        if (!(player instanceof ServerPlayer serverPlayer)) {
            return InteractionResultHolder.fail(stack);
        }
        StationCreationResult result = StationRuntime.createForPlayer(serverPlayer);
        serverPlayer.displayClientMessage(
                Component.translatable(result.code().translationKey()),
                true
        );
        if (!result.success()) {
            return InteractionResultHolder.fail(stack);
        }
        result.station().ifPresent(station -> serverPlayer.sendSystemMessage(
                Component.translatable(
                        "station.advancedrocketrycommunity.creation.details",
                        station.name(),
                        station.stationId().toString()
                )
        ));
        if (!serverPlayer.getAbilities().instabuild) {
            stack.shrink(1);
        }
        return InteractionResultHolder.consume(stack);
    }
}
