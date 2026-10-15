package io.github.sunthemoon.advancedrocketrycommunity.rocket.fuel.loader;

import io.github.sunthemoon.advancedrocketrycommunity.ModIdentity;
import net.minecraft.network.chat.Component;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.event.level.ExplosionEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * As R-038 for the satellite bay: a protected loader refuses player break before loot and leaves the explosion's
 * block list. No save is refused here; {@code /setblock}, {@code /fill} and raw replacement stay detectable residuals.
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
}
