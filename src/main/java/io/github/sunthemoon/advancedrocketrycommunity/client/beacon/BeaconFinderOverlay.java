package io.github.sunthemoon.advancedrocketrycommunity.client.beacon;

import io.github.sunthemoon.advancedrocketrycommunity.ModIdentity;
import io.github.sunthemoon.advancedrocketrycommunity.classiccontrol.network.BeaconMarkerCache;
import io.github.sunthemoon.advancedrocketrycommunity.classiccontrol.network.BeaconMarkerSnapshotPacket;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterGuiOverlaysEvent;
import net.minecraftforge.client.gui.overlay.ForgeGui;
import net.minecraftforge.client.gui.overlay.IGuiOverlay;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * The finder's waypoints (ADR-065 section 7): a heading strip at the top of the screen with one mark per cached
 * marker, bright for a live beacon and grey for a last-known one, and the name and distance of the marker nearest the
 * view direction. It shows only what the server last sent; an empty snapshot shows nothing.
 */
public final class BeaconFinderOverlay {
    public static final IGuiOverlay OVERLAY = BeaconFinderOverlay::render;
    private static final String HUD = "hud." + ModIdentity.MOD_ID + ".beacon_finder.";
    private static final int HALF_WIDTH = 91;
    private static final int TOP = 3;
    private static final int STRIP = 0x80FFFFFF;
    private static final int LIVE = 0xFF55E0FF;
    private static final int LAST_KNOWN = 0xFF9A9A9A;
    private static final int BEHIND = 0x80FFFFFF;

    private BeaconFinderOverlay() {
    }

    private static void render(ForgeGui gui, GuiGraphics graphics, float partialTick, int width, int height) {
        Minecraft minecraft = gui.getMinecraft();
        LocalPlayer player = minecraft.player;
        if (player == null || minecraft.options.hideGui) {
            return;
        }
        BeaconMarkerSnapshotPacket snapshot = BeaconMarkerCache.snapshot();
        if (snapshot.markers().isEmpty()) {
            return;
        }
        int centre = width / 2;
        graphics.fill(centre - HALF_WIDTH, TOP + 2, centre + HALF_WIDTH + 1, TOP + 3, STRIP);
        graphics.fill(centre, TOP - 1, centre + 1, TOP + 6, STRIP);
        Vec3 eye = player.getEyePosition(partialTick);
        float yaw = player.getViewYRot(partialTick);
        BeaconMarkerSnapshotPacket.Marker focused = null;
        double focusedAngle = Double.MAX_VALUE;
        double focusedDistance = 0.0;
        for (BeaconMarkerSnapshotPacket.Marker marker : snapshot.markers()) {
            double dx = marker.x() + 0.5 - eye.x;
            double dy = marker.y() + 0.5 - eye.y;
            double dz = marker.z() + 0.5 - eye.z;
            double relative = BeaconWaypointMath.relativeYaw(dx, dz, yaw);
            int x = centre + BeaconWaypointMath.stripOffset(relative, HALF_WIDTH);
            int colour = Math.abs(relative) > BeaconWaypointMath.STRIP_DEGREES ? BEHIND
                    : marker.live() ? LIVE : LAST_KNOWN;
            graphics.fill(x - 1, TOP, x + 2, TOP + 5, colour);
            if (Math.abs(relative) < focusedAngle) {
                focused = marker;
                focusedAngle = Math.abs(relative);
                focusedDistance = Math.sqrt(dx * dx + dy * dy + dz * dz);
            }
        }
        if (focused != null) {
            graphics.drawCenteredString(minecraft.font, label(focused, focusedDistance), centre, TOP + 8,
                    focused.live() ? LIVE : LAST_KNOWN);
        }
    }

    private static Component label(BeaconMarkerSnapshotPacket.Marker marker, double metres) {
        Component name = marker.name().isEmpty() ? Component.translatable(HUD + "unnamed")
                : Component.literal(marker.name());
        BeaconWaypointMath.Distance distance = BeaconWaypointMath.distance(metres);
        Component amount = Component.translatable(HUD + (distance.kilometres() ? "kilometres" : "metres"),
                distance.value());
        Component label = Component.translatable(HUD + "marker", name, amount);
        return marker.live() ? label : Component.translatable(HUD + "last_known", label);
    }

    @Mod.EventBusSubscriber(modid = ModIdentity.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
    public static final class Setup {
        private Setup() {
        }

        @SubscribeEvent
        public static void overlays(RegisterGuiOverlaysEvent event) {
            event.registerAboveAll("beacon_finder", OVERLAY);
        }
    }
}
