package io.github.sunthemoon.advancedrocketrycommunity.rocket.server;

import io.github.sunthemoon.advancedrocketrycommunity.ModIdentity;
import net.minecraft.server.level.ServerLevel;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.entity.EntityLeaveLevelEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Stateless lifecycle bridge. A late physical copy revokes RAM census and entity-slot proof authority. */
@Mod.EventBusSubscriber(modid = ModIdentity.MOD_ID)
public final class SealedEntityLifecycle {
    private SealedEntityLifecycle() { }
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void joined(EntityJoinLevelEvent event) {
        if (event.getLevel() instanceof ServerLevel level) { RocketRuntime.sealedEntityChanged(level, event.getEntity()); }
    }
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void left(EntityLeaveLevelEvent event) {
        if (event.getLevel() instanceof ServerLevel level) { RocketRuntime.sealedEntityChanged(level, event.getEntity()); }
    }
}
