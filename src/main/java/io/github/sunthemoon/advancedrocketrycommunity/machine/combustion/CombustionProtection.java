package io.github.sunthemoon.advancedrocketrycommunity.machine.combustion;

import io.github.sunthemoon.advancedrocketrycommunity.AdvancedRocketryCommunity;
import io.github.sunthemoon.advancedrocketrycommunity.persistence.GuardedChunkSaves;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.event.level.ExplosionEvent;
import net.minecraftforge.event.level.ChunkDataEvent;
import net.minecraft.nbt.Tag;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Refuse ordinary removal before vanilla computes drops, including creative breaking and explosions. */
@Mod.EventBusSubscriber(modid = AdvancedRocketryCommunity.MOD_ID)
public final class CombustionProtection {
    @SubscribeEvent
    public static void breaking(BlockEvent.BreakEvent event) {
        if (event.getState().getBlock() instanceof CombustionGeneratorBlock
                && CombustionGeneratorBlock.protectedData(event.getLevel(), event.getPos())) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void explosion(ExplosionEvent.Detonate event) {
        event.getAffectedBlocks().removeIf(position -> event.getLevel().hasChunkAt(position)
                && event.getLevel().getBlockState(position).getBlock() instanceof CombustionGeneratorBlock
                && CombustionGeneratorBlock.protectedData(event.getLevel(), position));
    }

    @SubscribeEvent
    public static void chunkSave(ChunkDataEvent.Save event) {
        GuardedChunkSaves.beforeSave(event);
        var entities = event.getData().getList("block_entities", Tag.TAG_COMPOUND);
        // A chunk has 256 columns and at most Level's build-height range of blocks. No world lookup or chunk load.
        int maximum = 256 * event.getChunk().getHeight();
        if (entities.size() > maximum) {
            GuardedChunkSaves.refuse(event, "Chunk block-entity count exceeds its physical bound");
        }
        for (int index = 0; index < entities.size(); index++) {
            var entity = entities.getCompound(index);
            if (entity.getString("id").equals("advancedrocketrycommunity:combustion_generator")
                    && entity.contains(CombustionSave.ROOT) && !CombustionSave.bounded(entity.get(CombustionSave.ROOT))) {
                // ChunkMap clears the dirty flag before serialization/event dispatch. Preserve a retry after repair.
                GuardedChunkSaves.refuse(event, "Refusing chunk save with oversized combustion input; back up and repair first");
            }
        }
    }

    private CombustionProtection() { }
}
