package io.github.sunthemoon.advancedrocketrycommunity.rocket.sealed;

import io.github.sunthemoon.advancedrocketrycommunity.ModIdentity;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.event.level.ExplosionEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Creative breaking and explosion lists need explicit refusal in addition to the block's native properties. */
@Mod.EventBusSubscriber(modid = ModIdentity.MOD_ID)
public final class SealedCarrierProtection {
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void breaking(BlockEvent.BreakEvent event) {
        if (event.getState().getBlock() instanceof SealedRocketBlock) { event.setCanceled(true); }
    }
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void explosion(ExplosionEvent.Detonate event) {
        event.getAffectedBlocks().removeIf(pos -> event.getLevel().hasChunkAt(pos)
                && event.getLevel().getBlockState(pos).getBlock() instanceof SealedRocketBlock);
    }
    private SealedCarrierProtection() { }
}
