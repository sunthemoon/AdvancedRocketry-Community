package io.github.sunthemoon.advancedrocketrycommunity.machine.recipe;

import io.github.sunthemoon.advancedrocketrycommunity.AdvancedRocketryCommunity;
import io.github.sunthemoon.advancedrocketrycommunity.persistence.GuardedChunkSaves;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.BlockGetter;
import net.minecraftforge.event.entity.living.LivingDestroyBlockEvent;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.event.level.ChunkDataEvent;
import net.minecraftforge.event.level.ExplosionEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Refuse removal and veto oversized saves without asking LevelChunk to omit a BlockEntity. */
@Mod.EventBusSubscriber(modid = AdvancedRocketryCommunity.MOD_ID)
public final class RecipeSignatureProtection {
    private static final Set<String> CONTROLLER_IDS = Set.of(
            "advancedrocketrycommunity:rolling_machine",
            "advancedrocketrycommunity:precision_assembler",
            "advancedrocketrycommunity:electrolyzer");

    @SubscribeEvent
    public static void breaking(BlockEvent.BreakEvent event) {
        if (blocksRemoval(event.getLevel(), event.getPos())) { event.setCanceled(true); }
    }

    @SubscribeEvent
    public static void entityDestruction(LivingDestroyBlockEvent event) {
        if (blocksRemoval(event.getEntity().level(), event.getPos())) { event.setCanceled(true); }
    }

    @SubscribeEvent
    public static void explosion(ExplosionEvent.Detonate event) {
        event.getAffectedBlocks().removeIf(position -> blocksRemoval(event.getLevel(), position));
    }

    @SubscribeEvent
    public static void chunkSave(ChunkDataEvent.Save event) {
        GuardedChunkSaves.beforeSave(event);
        try {
            requireBoundedChunk(event.getData(), 256 * event.getChunk().getHeight(), () -> { });
        } catch (IllegalStateException refused) {
            // Persist the transient denial before ChunkMap can clear the live BlockEntity list.
            GuardedChunkSaves.refuse(event, refused.getMessage());
        }
    }

    static void requireBoundedChunk(CompoundTag data, int maximumEntities, Runnable retainRetry) {
        var entities = data.getList("block_entities", Tag.TAG_COMPOUND);
        if (entities.size() > maximumEntities) {
            retainRetry.run();
            throw new IllegalStateException("Chunk block-entity count exceeds its physical bound");
        }
        for (int index = 0; index < entities.size(); index++) {
            CompoundTag entity = entities.getCompound(index);
            if (CONTROLLER_IDS.contains(entity.getString("id")) && !RecipeSignatureMigration.bounded(entity)) {
                retainRetry.run();
                throw new IllegalStateException("Refusing chunk save with oversized recipe input; back up and repair first");
            }
        }
    }

    /** Shared by Forge entity hooks and events; never load a missing chunk. */
    public static boolean blocksRemoval(BlockGetter level, BlockPos position) {
        return level instanceof ServerLevel server && server.hasChunkAt(position)
                && server.getBlockEntity(position) instanceof RecipeSignatureProtected guarded
                && guarded.preservesRecipeInput();
    }

    private RecipeSignatureProtection() { }
}
