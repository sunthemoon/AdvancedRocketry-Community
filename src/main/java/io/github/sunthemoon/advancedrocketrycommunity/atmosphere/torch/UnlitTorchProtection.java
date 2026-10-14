package io.github.sunthemoon.advancedrocketrycommunity.atmosphere.torch;

import io.github.sunthemoon.advancedrocketrycommunity.ModIdentity;
import io.github.sunthemoon.advancedrocketrycommunity.persistence.GuardedChunkSaves;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.event.level.ChunkDataEvent;
import net.minecraftforge.event.level.ExplosionEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** ADR-066 W33 note, R-027 and R-034: quarantined unlit torches stay; oversized roots refuse the chunk save. */
@Mod.EventBusSubscriber(modid = ModIdentity.MOD_ID)
public final class UnlitTorchProtection {
    private static final String TYPE = ModIdentity.id("unlit_torch").toString();

    private UnlitTorchProtection() {
    }

    @SubscribeEvent
    public static void breaking(BlockEvent.BreakEvent event) {
        if (event.getLevel() instanceof ServerLevel level && CombustionAir.loaded(level, event.getPos())
                && UnlitTorchBlockEntity.protectedAt(level, event.getPos())) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void explosion(ExplosionEvent.Detonate event) {
        if (event.getLevel() instanceof ServerLevel level) {
            event.getAffectedBlocks().removeIf(pos -> CombustionAir.loaded(level, pos)
                    && UnlitTorchBlockEntity.protectedAt(level, pos));
        }
    }

    @SubscribeEvent
    public static void chunkSave(ChunkDataEvent.Save event) {
        GuardedChunkSaves.beforeSave(event);
        var entities = event.getData().getList("block_entities", Tag.TAG_COMPOUND);
        for (int index = 0; index < entities.size(); index++) {
            var entity = entities.getCompound(index);
            if (entity.getString("id").equals(TYPE) && entity.contains(UnlitTorchRoot.KEY)
                    && !UnlitTorchRoot.bounded(entity.get(UnlitTorchRoot.KEY))) {
                GuardedChunkSaves.refuse(event, "Refusing non-lossless unlit torch root; back up and repair offline");
            }
        }
    }
}
