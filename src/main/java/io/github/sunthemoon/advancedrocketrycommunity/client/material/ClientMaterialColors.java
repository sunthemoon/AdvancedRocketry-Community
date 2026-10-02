package io.github.sunthemoon.advancedrocketrycommunity.client.material;

import io.github.sunthemoon.advancedrocketrycommunity.AdvancedRocketryCommunity;
import io.github.sunthemoon.advancedrocketrycommunity.material.MaterialCatalog;
import io.github.sunthemoon.advancedrocketrycommunity.material.MaterialCatalog.Entry;
import io.github.sunthemoon.advancedrocketrycommunity.material.MaterialContent;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterColorHandlersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Tints the greyscale material templates with the legacy material colours (ADR-063 section 1). Only tint index 0 is
 * coloured; ore models keep their stone base untinted.
 */
@Mod.EventBusSubscriber(modid = AdvancedRocketryCommunity.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD,
        value = Dist.CLIENT)
public final class ClientMaterialColors {
    private ClientMaterialColors() {
    }

    @SubscribeEvent
    public static void items(RegisterColorHandlersEvent.Item event) {
        for (Entry entry : MaterialCatalog.entries()) {
            int colour = 0xFF000000 | entry.material().colour();
            event.register((stack, tintIndex) -> tintIndex == 0 ? colour : 0xFFFFFFFF,
                    MaterialContent.item(entry.id()));
        }
    }

    @SubscribeEvent
    public static void blocks(RegisterColorHandlersEvent.Block event) {
        for (Entry entry : MaterialCatalog.entries()) {
            if (entry.isBlock()) {
                int colour = 0xFF000000 | entry.material().colour();
                event.register((state, level, position, tintIndex) -> tintIndex == 0 ? colour : 0xFFFFFFFF,
                        MaterialContent.block(entry.id()));
            }
        }
    }
}
