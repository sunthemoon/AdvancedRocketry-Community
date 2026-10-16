package io.github.sunthemoon.advancedrocketrycommunity.rocket.fuel.loader;

import io.github.sunthemoon.advancedrocketrycommunity.ModIdentity;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.entity.RocketEntity;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * A linker used on a rocket binds the selected loader instead of opening the rocket's menu or exchanging fuel. Forge
 * fires this before {@link RocketEntity#interact}, after vanilla's own interaction-range check on the server.
 */
@Mod.EventBusSubscriber(modid = ModIdentity.MOD_ID)
public final class LinkerInteraction {
    private LinkerInteraction() {
    }

    @SubscribeEvent
    public static void entityInteract(PlayerInteractEvent.EntityInteract event) {
        if (!(event.getTarget() instanceof RocketEntity rocket) || !(event.getItemStack().getItem() instanceof LinkerItem)) {
            return;
        }
        event.setCanceled(true);
        if (event.getLevel().isClientSide) {
            event.setCancellationResult(InteractionResult.SUCCESS);
        } else if (event.getEntity() instanceof ServerPlayer player) {
            event.setCancellationResult(LinkerItem.bindRocket(player, event.getHand(), rocket));
        } else {
            event.setCancellationResult(InteractionResult.FAIL);
        }
    }
}
