package io.github.sunthemoon.advancedrocketrycommunity.resourceescrow;

import io.github.sunthemoon.advancedrocketrycommunity.ModIdentity;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.entity.RocketEntity;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ChunkPos;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.entity.EntityLeaveLevelEvent;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.event.level.ChunkEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Lifecycle changes revoke retained slot leases, including a returning identity outside its old slot. */
@Mod.EventBusSubscriber(modid = ModIdentity.MOD_ID)
public final class EscrowSettlementLifecycle {
    private EscrowSettlementLifecycle() { }
    @SubscribeEvent(priority = EventPriority.HIGHEST) public static void joined(EntityJoinLevelEvent event) {
        if (event.getLevel() instanceof ServerLevel level) { identity(level, event.getEntity()); }
    }
    @SubscribeEvent(priority = EventPriority.HIGHEST) public static void left(EntityLeaveLevelEvent event) {
        if (event.getLevel() instanceof ServerLevel level) { identity(level, event.getEntity()); }
    }
    private static void identity(ServerLevel level, Entity entity) {
        if (!level.getServer().isSameThread()) { return; }
        var data = ResourceEscrowSavedData.existing(level.getServer()); if (data == null) { return; }
        var logical = entity instanceof RocketEntity rocket ? rocket.assemblyTransactionId().orElse(null) : null;
        for (var op : data.ledger().records()) {
            for (var endpoint : java.util.List.of(op.source(), op.destination())) {
                if (endpoint.kind() == EscrowEndpoint.Kind.ENTITY && (endpoint.identity().equals(entity.getUUID())
                        || logical != null && logical.equals(endpoint.logicalRocket()))) {
                    EscrowRuntime.of(level.getServer()).monitor().observed(endpoint.level(), true, new ChunkPos(endpoint.x(), endpoint.z()));
                }
            }
        }
    }
    @SubscribeEvent(priority = EventPriority.HIGHEST) public static void unloaded(ChunkEvent.Unload event) {
        if (event.getLevel() instanceof ServerLevel level && level.getServer().isSameThread()) {
            var monitor = EscrowRuntime.of(level.getServer()).monitor(); var position = event.getChunk().getPos();
            monitor.observed(level.dimension().location().toString(), false, position);
            monitor.observed(level.dimension().location().toString(), true, position);
        }
    }
    @SubscribeEvent(priority = EventPriority.HIGHEST) public static void blockChanged(BlockEvent event) {
        if ((event instanceof BlockEvent.BreakEvent || event instanceof BlockEvent.EntityPlaceEvent)
                && event.getLevel() instanceof ServerLevel level && level.getServer().isSameThread()) {
            EscrowRuntime.of(level.getServer()).monitor().observed(level.dimension().location().toString(), false, new ChunkPos(event.getPos()));
        }
    }
}
