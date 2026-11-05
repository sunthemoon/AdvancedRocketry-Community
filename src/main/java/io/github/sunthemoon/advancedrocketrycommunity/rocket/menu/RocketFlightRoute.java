package io.github.sunthemoon.advancedrocketrycommunity.rocket.menu;

import io.github.sunthemoon.advancedrocketrycommunity.station.pad.LandingPadReference;
import net.minecraft.network.chat.Component;

/** Display-only route text. The pad comes first so a short console cannot truncate it behind a station name. */
public final class RocketFlightRoute {
    private RocketFlightRoute() { }

    public static Component label(Component current, Component destination, LandingPadReference pad, String padName) {
        if (pad == null) {
            return Component.translatable("screen.advancedrocketrycommunity.rocket.route", current, destination);
        }
        Component name = padName.isEmpty()
                ? Component.translatable("screen.advancedrocketrycommunity.rocket.pad_missing") : Component.literal(padName);
        return Component.translatable("screen.advancedrocketrycommunity.rocket.route_pad", name, current, destination);
    }
}
