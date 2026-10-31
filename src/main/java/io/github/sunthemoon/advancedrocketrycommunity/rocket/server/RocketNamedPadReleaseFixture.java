package io.github.sunthemoon.advancedrocketrycommunity.rocket.server;

import io.github.sunthemoon.advancedrocketrycommunity.resourceescrow.EscrowTestEndpoints;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.entity.RocketEntity;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketFlightData;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketFlightRequestResult;
import net.minecraft.server.MinecraftServer;

/** W221 bridge to the installed product entries, never an alternative selector or return implementation. */
public final class RocketNamedPadReleaseFixture {
    private RocketNamedPadReleaseFixture() { }
    private static void require(MinecraftServer server) {
        EscrowTestEndpoints.requireEnabled(server);
        if (!Boolean.getBoolean(EscrowTestEndpoints.HOOKS)
                || !io.github.sunthemoon.advancedrocketrycommunity.resourceescrow.EscrowLoaderS6PadProbe.selected(
                        true, System.getProperty("advancedrocketrycommunity.escrowCase", ""))) {
            throw new IllegalStateException("Not a named-pad release fixture");
        }
    }
    public static RocketFlightRequestResult launch(RocketEntity source, RocketFlightData countdown, Runnable discarded) {
        var server = source.level().getServer(); require(server);
        return RocketRuntime.installedTransfers().prepareLaunch(source, countdown, new RocketRequestCompletion(() -> { }, discarded));
    }
    public static RocketFlightRequestResult cancel(RocketEntity source) {
        var server = source.level().getServer(); require(server);
        return RocketRuntime.installedTransfers().cancelCountdown(source);
    }
}
