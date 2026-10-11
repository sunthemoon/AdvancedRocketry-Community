package io.github.sunthemoon.advancedrocketrycommunity.equipment.workstation;

import io.github.sunthemoon.advancedrocketrycommunity.ModIdentity;
import io.github.sunthemoon.advancedrocketrycommunity.persistence.GuardedChunkSaves;
import net.minecraft.nbt.Tag;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.event.level.ChunkDataEvent;
import net.minecraftforge.event.level.ExplosionEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** ADR-066 section 2 and R-026: reuse native chunk admission, never throw from BE serialization. */
@Mod.EventBusSubscriber(modid = ModIdentity.MOD_ID)
public final class SuitWorkstationProtection {
    @SubscribeEvent public static void breaking(BlockEvent.BreakEvent event) {
        if (event.getState().getBlock() instanceof SuitWorkstationBlock
                && SuitWorkstationBlock.protectedData(event.getLevel(), event.getPos())) { event.setCanceled(true); }
    }
    @SubscribeEvent public static void explosion(ExplosionEvent.Detonate event) {
        event.getAffectedBlocks().removeIf(pos -> event.getLevel().hasChunkAt(pos)
                && event.getLevel().getBlockState(pos).getBlock() instanceof SuitWorkstationBlock
                && SuitWorkstationBlock.protectedData(event.getLevel(), pos));
    }
    @SubscribeEvent public static void chunkSave(ChunkDataEvent.Save event) {
        GuardedChunkSaves.beforeSave(event);
        var entities = event.getData().getList("block_entities", Tag.TAG_COMPOUND);
        if (entities.size() > 256 * event.getChunk().getHeight()) {
            GuardedChunkSaves.refuse(event, "Chunk block-entity count exceeds its physical bound");
        }
        for (int i = 0; i < entities.size(); i++) {
            var entity = entities.getCompound(i);
            if (entity.getString("id").equals("advancedrocketrycommunity:suit_workstation")
                    && entity.contains(SuitWorkstationSave.KEY) && !SuitWorkstationSave.bounded(entity.get(SuitWorkstationSave.KEY))) {
                GuardedChunkSaves.refuse(event, "Refusing oversized workstation chunk save; back up and repair first");
            }
        }
    }
    private SuitWorkstationProtection() { }
}
