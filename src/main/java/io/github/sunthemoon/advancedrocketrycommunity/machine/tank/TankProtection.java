package io.github.sunthemoon.advancedrocketrycommunity.machine.tank;

import io.github.sunthemoon.advancedrocketrycommunity.ModIdentity;
import io.github.sunthemoon.advancedrocketrycommunity.persistence.GuardedChunkSaves;
import net.minecraft.nbt.Tag;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.event.level.ChunkDataEvent;
import net.minecraftforge.event.level.ExplosionEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Ordinary removal preserves unsupported input; oversized input vetoes the complete native chunk write. */
@Mod.EventBusSubscriber(modid = ModIdentity.MOD_ID)
public final class TankProtection {
    @SubscribeEvent public static void breaking(BlockEvent.BreakEvent event) {
        if (event.getState().getBlock() instanceof PressurizedTankBlock
                && PressurizedTankBlock.protectedData(event.getLevel(), event.getPos())) { event.setCanceled(true); }
    }
    @SubscribeEvent public static void explosion(ExplosionEvent.Detonate event) {
        event.getAffectedBlocks().removeIf(position -> event.getLevel().hasChunkAt(position)
                && event.getLevel().getBlockState(position).getBlock() instanceof PressurizedTankBlock
                && PressurizedTankBlock.protectedData(event.getLevel(), position));
    }
    @SubscribeEvent public static void chunkSave(ChunkDataEvent.Save event) {
        GuardedChunkSaves.beforeSave(event);
        var entities = event.getData().getList("block_entities", Tag.TAG_COMPOUND);
        if (entities.size() > 256 * event.getChunk().getHeight()) {
            GuardedChunkSaves.refuse(event, "Chunk block-entity count exceeds its physical bound");
        }
        for (int index = 0; index < entities.size(); index++) {
            var entity = entities.getCompound(index);
            if (entity.getString("id").equals("advancedrocketrycommunity:pressurized_tank")
                    && entity.contains(TankSave.ROOT) && !TankSave.bounded(entity.get(TankSave.ROOT))) {
                GuardedChunkSaves.refuse(event, "Refusing oversized tank chunk save; back up and repair first");
            }
        }
    }
    private TankProtection() { }
}
