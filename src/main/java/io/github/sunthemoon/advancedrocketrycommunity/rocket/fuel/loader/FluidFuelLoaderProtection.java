package io.github.sunthemoon.advancedrocketrycommunity.rocket.fuel.loader;

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
 * As R-038 for the satellite bay: a protected loader refuses player break before loot and leaves the explosion's
 * block list. Only observed native incompatibility denies terrain saves; {@code /setblock}, {@code /fill} and raw
 * replacement stay detectable residuals. W176 follows the existing W109 rule and guard capacity.
 */
@Mod.EventBusSubscriber(modid = ModIdentity.MOD_ID)
public final class FluidFuelLoaderProtection {
    private FluidFuelLoaderProtection() {
    }

    @SubscribeEvent
    public static void breaking(BlockEvent.BreakEvent event) {
        if (event.getState().getBlock() instanceof FluidFuelLoaderBlock
                && event.getLevel().getBlockEntity(event.getPos()) instanceof FluidFuelLoaderBlockEntity loader
                && loader.protectedFromRemoval()) {
            event.setCanceled(true);
            if (event.getPlayer() != null) {
                FluidFuelLoaderStatus reason = loader.dataStatus() == LoaderRoot.Status.INVALID
                        ? FluidFuelLoaderStatus.INVALID_DATA : loader.unsupportedRoot()
                        ? FluidFuelLoaderStatus.UNSUPPORTED_DATA : FluidFuelLoaderStatus.ESCROW_PENDING;
                event.getPlayer().displayClientMessage(Component.translatable(reason.translationKey()), true);
            }
        }
    }

    @SubscribeEvent
    public static void explosion(ExplosionEvent.Detonate event) {
        event.getAffectedBlocks().removeIf(position -> event.getLevel().hasChunkAt(position)
                && event.getLevel().getBlockState(position).getBlock() instanceof FluidFuelLoaderBlock
                && FluidFuelLoaderBlock.protectedAt(event.getLevel(), position));
    }

    /** Inspect serialized roots, including unloaded block entities, independently of the live observation. */
    @SubscribeEvent
    public static void chunkSave(ChunkDataEvent.Save event) {
        GuardedChunkSaves.beforeSave(event);
        var entities = event.getData().getList("block_entities", Tag.TAG_COMPOUND);
        if (entities.size() > 256 * event.getChunk().getHeight()) {
            GuardedChunkSaves.refuse(event, "Chunk block-entity count exceeds its physical bound");
        }
        for (int index = 0; index < entities.size(); index++) {
            var entity = entities.getCompound(index);
            if (entity.getString("id").equals(ModIdentity.id("fluid_fuel_loader").toString())
                    && entity.contains(LoaderRoot.KEY)
                    && !SatelliteBayRoot.nativelyKeepable(entity.get(LoaderRoot.KEY))) {
                GuardedChunkSaves.refuse(event, FluidFuelLoaderBlockEntity.SAVE_REFUSAL);
            }
        }
    }
}
