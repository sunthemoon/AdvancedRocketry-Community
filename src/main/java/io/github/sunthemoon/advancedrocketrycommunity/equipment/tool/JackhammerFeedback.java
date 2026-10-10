package io.github.sunthemoon.advancedrocketrycommunity.equipment.tool;

import io.github.sunthemoon.advancedrocketrycommunity.ModIdentity;
import io.github.sunthemoon.advancedrocketrycommunity.config.CommonConfig;
import net.minecraft.network.chat.Component;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.LogicalSide;
import net.minecraftforge.fml.common.Mod;

/** Early informational feedback; final native item hooks still enforce disabled use. */
@Mod.EventBusSubscriber(modid = ModIdentity.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class JackhammerFeedback {
    private JackhammerFeedback() { }

    @SubscribeEvent(priority = EventPriority.LOWEST, receiveCanceled = true)
    public static void onMiningStart(PlayerInteractEvent.LeftClickBlock event) {
        if (event.getSide() == LogicalSide.SERVER
                && event.getAction() == PlayerInteractEvent.LeftClickBlock.Action.START
                && event.getItemStack().getItem() instanceof JackhammerItem
                && !CommonConfig.classicEquipmentEnabled()) {
            event.getEntity().displayClientMessage(
                    Component.translatable("message.advancedrocketrycommunity.jackhammer.disabled"), true);
        }
    }
}
