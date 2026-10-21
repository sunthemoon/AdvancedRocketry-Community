package io.github.sunthemoon.advancedrocketrycommunity.rocket.cargo.device;

import io.github.sunthemoon.advancedrocketrycommunity.ModIdentity;
import io.github.sunthemoon.advancedrocketrycommunity.persistence.GuardedChunkSaves;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.bay.SatelliteBayRoot;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.event.level.ChunkDataEvent;
import net.minecraftforge.event.level.ExplosionEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * R-035 cargo scope for the item devices, as for the fluid devices: protected devices refuse player break and leave
 * explosion lists. Only observed native incompatibility denies terrain saves; {@code /setblock}, {@code /fill} and raw
 * replacement remain residuals.
 */
@Mod.EventBusSubscriber(modid = ModIdentity.MOD_ID)
public final class RocketItemCargoProtection {
    private RocketItemCargoProtection() {
    }

    @SubscribeEvent
    public static void breaking(BlockEvent.BreakEvent event) {
        if (event.getState().getBlock() instanceof RocketItemCargoBlock
                && event.getLevel().getBlockEntity(event.getPos()) instanceof RocketItemCargoBlockEntity device
                && device.protectedFromRemoval()) {
            event.setCanceled(true);
            if (event.getPlayer() != null) {
                RocketCargoStatus reason = device.dataStatus() == RocketItemCargoRoot.Status.INVALID
                        ? RocketCargoStatus.INVALID_DATA : device.unsupportedRoot()
                        ? RocketCargoStatus.UNSUPPORTED_DATA : RocketCargoStatus.ESCROW_PENDING;
                event.getPlayer().displayClientMessage(Component.translatable(reason.translationKey()), true);
            }
        }
    }

    @SubscribeEvent
    public static void explosion(ExplosionEvent.Detonate event) {
        event.getAffectedBlocks().removeIf(position -> event.getLevel().hasChunkAt(position)
                && event.getLevel().getBlockState(position).getBlock() instanceof RocketItemCargoBlock
                && RocketItemCargoBlock.protectedAt(event.getLevel(), position));
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
            if ((type.equals(ModIdentity.id("rocket_item_loader").toString())
                    || type.equals(ModIdentity.id("rocket_item_unloader").toString()))
                    && entity.contains(RocketItemCargoRoot.KEY)
                    && !SatelliteBayRoot.nativelyKeepable(entity.get(RocketItemCargoRoot.KEY))) {
                GuardedChunkSaves.refuse(event, RocketItemCargoBlockEntity.SAVE_REFUSAL);
            }
        }
    }
}
