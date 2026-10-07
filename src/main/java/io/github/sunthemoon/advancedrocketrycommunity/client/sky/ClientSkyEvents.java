package io.github.sunthemoon.advancedrocketrycommunity.client.sky;

import io.github.sunthemoon.advancedrocketrycommunity.AdvancedRocketryCommunity;
import io.github.sunthemoon.advancedrocketrycommunity.celestial.visual.SkyMath;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.FogRenderer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.ViewportEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = AdvancedRocketryCommunity.MOD_ID,
        bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public final class ClientSkyEvents {
    private ClientSkyEvents() { }

    @SubscribeEvent public static void tick(TickEvent.ClientTickEvent event) {
        if (event.phase == TickEvent.Phase.END) { PlanetarySkyClient.tick(); }
    }

    @SubscribeEvent public static void logout(ClientPlayerNetworkEvent.LoggingOut event) {
        PlanetarySkyClient.logout();
    }

    @SubscribeEvent public static void levelChanged(ClientPlayerNetworkEvent.Clone event) {
        PlanetarySkyClient.levelChanged();
    }

    @SubscribeEvent(priority = EventPriority.LOW) public static void fogColor(ViewportEvent.ComputeFogColor event) {
        var level = Minecraft.getInstance().level;
        var selection = PlanetarySkyClient.visualSelection(level);
        if (selection == null || !PlanetarySkyClient.clearView(event.getCamera())
                || event.getCamera().getPosition().y < level.getMinBuildHeight() + 8) {
            return;
        }
        int rgb = selection.profile().fogColor();
        double brightness = 0.12 + 0.88 * SkyMath.daylight(level.getSunAngle((float) event.getPartialTick()));
        event.setRed((float) (((rgb >> 16) & 255) / 255D * brightness));
        event.setGreen((float) (((rgb >> 8) & 255) / 255D * brightness));
        event.setBlue((float) ((rgb & 255) / 255D * brightness));
    }

    @SubscribeEvent(priority = EventPriority.LOW) public static void fogDistance(ViewportEvent.RenderFog event) {
        if (event.getMode() != FogRenderer.FogMode.FOG_TERRAIN || !PlanetarySkyClient.clearView(event.getCamera())) {
            return;
        }
        var selection = PlanetarySkyClient.visualSelection(Minecraft.getInstance().level);
        if (selection == null) { return; }
        var fog = SkyMath.fog(selection.profile(), event.getNearPlaneDistance(), event.getFarPlaneDistance());
        if (Float.compare(fog.near(), event.getNearPlaneDistance()) != 0
                || Float.compare(fog.far(), event.getFarPlaneDistance()) != 0) {
            event.setNearPlaneDistance(fog.near());
            event.setFarPlaneDistance(fog.far());
            event.setCanceled(true);
        }
    }
}
