package io.github.sunthemoon.advancedrocketrycommunity.machine.solar;

import io.github.sunthemoon.advancedrocketrycommunity.ModIdentity;
import io.github.sunthemoon.advancedrocketrycommunity.persistence.GuardedChunkSaves;
import net.minecraft.nbt.Tag;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.event.level.ChunkDataEvent;
import net.minecraftforge.event.level.ExplosionEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Reuses the disclosed sticky chunk/Level refusal policy; no exception is thrown by BE serialization. */
@Mod.EventBusSubscriber(modid = ModIdentity.MOD_ID)
public final class SolarProtection {
    @SubscribeEvent public static void breaking(BlockEvent.BreakEvent event) {
        if (event.getState().getBlock() instanceof SolarGeneratorBlock
                && SolarGeneratorBlock.protectedData(event.getLevel(), event.getPos())) { event.setCanceled(true); }
    }
    @SubscribeEvent public static void explosion(ExplosionEvent.Detonate event) {
        event.getAffectedBlocks().removeIf(position -> event.getLevel().hasChunkAt(position)
                && event.getLevel().getBlockState(position).getBlock() instanceof SolarGeneratorBlock
                && SolarGeneratorBlock.protectedData(event.getLevel(), position));
    }
    @SubscribeEvent public static void chunkSave(ChunkDataEvent.Save event) {
        GuardedChunkSaves.beforeSave(event);
        var entities = event.getData().getList("block_entities", Tag.TAG_COMPOUND);
        int maximum = 256 * event.getChunk().getHeight();
        if (entities.size() > maximum) {
            GuardedChunkSaves.refuse(event, "Chunk block-entity count exceeds its physical bound");
        }
        for (int index = 0; index < entities.size(); index++) {
            var entity = entities.getCompound(index);
            if (entity.getString("id").equals("advancedrocketrycommunity:solar_generator")
                    && entity.contains(SolarSave.ROOT) && !SolarSave.bounded(entity.get(SolarSave.ROOT))) {
                GuardedChunkSaves.refuse(event, "Refusing chunk save with non-admissible solar input; back up and repair first");
            }
        }
    }
    private SolarProtection() { }
}
