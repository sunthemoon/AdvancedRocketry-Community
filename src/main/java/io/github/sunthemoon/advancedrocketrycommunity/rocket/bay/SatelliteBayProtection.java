package io.github.sunthemoon.advancedrocketrycommunity.rocket.bay;

import io.github.sunthemoon.advancedrocketrycommunity.ModIdentity;
import io.github.sunthemoon.advancedrocketrycommunity.persistence.GuardedChunkSaves;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.event.level.ChunkDataEvent;
import net.minecraftforge.event.level.ExplosionEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * R-038: guarded or claimed bays refuse player break and leave the explosion's block list. A root that native
 * serialization cannot keep refuses its whole terrain chunk through the existing {@code GuardedChunkSaves} denial;
 * a serializable quarantined root is written unchanged and refuses nothing.
 */
@Mod.EventBusSubscriber(modid = ModIdentity.MOD_ID)
public final class SatelliteBayProtection {
    private static final String TYPE = ModIdentity.id("rocket_satellite_bay").toString();

    private SatelliteBayProtection() {
    }

    @SubscribeEvent
    public static void breaking(BlockEvent.BreakEvent event) {
        if (event.getState().getBlock() instanceof SatelliteBayBlock
                && event.getLevel().getBlockEntity(event.getPos()) instanceof SatelliteBayBlockEntity bay
                && bay.protectedFromRemoval()) {
            event.setCanceled(true);
            if (event.getPlayer() != null) {
                event.getPlayer().displayClientMessage(Component.translatable(bay.guard().translationKey()), true);
            }
        }
    }

    @SubscribeEvent
    public static void explosion(ExplosionEvent.Detonate event) {
        event.getAffectedBlocks().removeIf(position -> event.getLevel().hasChunkAt(position)
                && event.getLevel().getBlockState(position).getBlock() instanceof SatelliteBayBlock
                && SatelliteBayBlock.protectedAt(event.getLevel(), position));
    }

    @SubscribeEvent
    public static void chunkSave(ChunkDataEvent.Save event) {
        GuardedChunkSaves.beforeSave(event);
        var entities = event.getData().getList("block_entities", Tag.TAG_COMPOUND);
        if (entities.size() > 256 * event.getChunk().getHeight()) {
            GuardedChunkSaves.refuse(event, "Chunk block-entity count exceeds its physical bound");
        }
        for (int index = 0; index < entities.size(); index++) {
            var entity = entities.getCompound(index);
            if (entity.getString("id").equals(TYPE) && entity.contains(SatelliteBayRoot.KEY)
                    && !SatelliteBayRoot.nativelyKeepable(entity.get(SatelliteBayRoot.KEY))) {
                GuardedChunkSaves.refuse(event, SatelliteBayBlockEntity.SAVE_REFUSAL);
            }
        }
    }
}
