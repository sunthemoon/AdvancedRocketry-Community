package io.github.sunthemoon.advancedrocketrycommunity.rocket.cargo.device;

import io.github.sunthemoon.advancedrocketrycommunity.ModIdentity;
import net.minecraft.network.chat.Component;
import net.minecraft.nbt.Tag;
import io.github.sunthemoon.advancedrocketrycommunity.persistence.GuardedChunkSaves;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.bay.SatelliteBayRoot;
import net.minecraftforge.event.level.ChunkDataEvent;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.event.level.ExplosionEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * R-035 cargo scope: protected devices refuse player break and leave explosion lists. Only observed native
 * incompatibility denies terrain saves; {@code /setblock}, {@code /fill} and raw replacement remain residuals.
 */
@Mod.EventBusSubscriber(modid = ModIdentity.MOD_ID)
public final class RocketFluidCargoProtection {
    private RocketFluidCargoProtection() {
    }

    @SubscribeEvent
    public static void breaking(BlockEvent.BreakEvent event) {
        if (event.getState().getBlock() instanceof RocketFluidCargoBlock
                && event.getLevel().getBlockEntity(event.getPos()) instanceof RocketFluidCargoBlockEntity loader
                && loader.protectedFromRemoval()) {
            event.setCanceled(true);
            if (event.getPlayer() != null) {
                RocketCargoStatus reason = loader.dataStatus() == RocketFluidCargoRoot.Status.INVALID
                        ? RocketCargoStatus.INVALID_DATA : loader.unsupportedRoot()
                        ? RocketCargoStatus.UNSUPPORTED_DATA : RocketCargoStatus.ESCROW_PENDING;
                event.getPlayer().displayClientMessage(Component.translatable(reason.translationKey()), true);
            }
        }
    }

    @SubscribeEvent
    public static void explosion(ExplosionEvent.Detonate event) {
        event.getAffectedBlocks().removeIf(position -> event.getLevel().hasChunkAt(position)
                && event.getLevel().getBlockState(position).getBlock() instanceof RocketFluidCargoBlock
                && RocketFluidCargoBlock.protectedAt(event.getLevel(), position));
    }
    /** Inspect serialized roots too, including unloaded block entities; do not silently omit an incompatible root. */
    @SubscribeEvent
    public static void chunkSave(ChunkDataEvent.Save event) {
        GuardedChunkSaves.beforeSave(event);
        var entities = event.getData().getList("block_entities", Tag.TAG_COMPOUND);
        if (entities.size() > 256 * event.getChunk().getHeight()) {
            GuardedChunkSaves.refuse(event, "Chunk block-entity count exceeds its physical bound");
        }
        for (int index = 0; index < entities.size(); index++) {
            var entity = entities.getCompound(index);
            String type = entity.getString("id");
            if ((type.equals(ModIdentity.id("rocket_fluid_loader").toString())
                    || type.equals(ModIdentity.id("rocket_fluid_unloader").toString()))
                    && entity.contains(RocketFluidCargoRoot.KEY)
                    && !SatelliteBayRoot.nativelyKeepable(entity.get(RocketFluidCargoRoot.KEY))) {
                GuardedChunkSaves.refuse(event, RocketFluidCargoBlockEntity.SAVE_REFUSAL);
            }
        }
    }
}
