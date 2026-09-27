package io.github.sunthemoon.advancedrocketrycommunity.client;

import io.github.sunthemoon.advancedrocketrycommunity.rocket.menu.RocketFlightMenu;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.network.RocketFlightPlanPacket;
import net.minecraft.client.Minecraft;

/** Main-client-thread adapter; late updates never create or reopen a menu. */
public final class RocketFlightPlanHandler {
    private RocketFlightPlanHandler() {
    }

    public static void handle(RocketFlightPlanPacket packet) {
        var player = Minecraft.getInstance().player;
        if (player != null && player.containerMenu instanceof RocketFlightMenu menu
                && packet.targets(menu.containerId, menu.rocketEntityId())) {
            menu.acceptPlanSnapshot(packet.plan(), packet.navigation());
        }
    }
}
