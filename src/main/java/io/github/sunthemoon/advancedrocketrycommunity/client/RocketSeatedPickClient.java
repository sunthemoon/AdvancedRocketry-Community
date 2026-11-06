package io.github.sunthemoon.advancedrocketrycommunity.client;

import io.github.sunthemoon.advancedrocketrycommunity.ModIdentity;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.entity.RocketSeatedPick;
import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** W311: a seated rider's use key reaches its own rocket even where vanilla's entity-section search misses it. */
@Mod.EventBusSubscriber(modid = ModIdentity.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public final class RocketSeatedPickClient {
    private RocketSeatedPickClient() { }

    /** Runs before vanilla reads the hit result, so vanilla's own interaction and its packet follow; no packet is added. */
    @SubscribeEvent
    public static void use(InputEvent.InteractionKeyMappingTriggered event) {
        if (!event.isUseItem()) { return; }
        var minecraft = Minecraft.getInstance();
        var player = minecraft.player;
        if (player == null) { return; }
        RocketSeatedPick.target(player, minecraft.hitResult)
                .ifPresent(rocket -> minecraft.hitResult = new EntityHitResult(rocket, player.getEyePosition()));
    }
}
