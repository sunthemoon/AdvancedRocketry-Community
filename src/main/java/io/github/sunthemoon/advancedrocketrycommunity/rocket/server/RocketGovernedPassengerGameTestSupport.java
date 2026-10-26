package io.github.sunthemoon.advancedrocketrycommunity.rocket.server;

import io.github.sunthemoon.advancedrocketrycommunity.resourceescrow.EscrowGovernedPhases;
import io.github.sunthemoon.advancedrocketrycommunity.resourceescrow.EscrowRuntime;
import io.github.sunthemoon.advancedrocketrycommunity.resourceescrow.EscrowTestEndpoints;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.entity.RocketEntity;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketTransferRecord;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.persistence.RocketTransferSavedData;
import net.minecraft.server.MinecraftServer;

/** Runs the installed checked F5 executor without also starting the dispatcher's F6 capture. */
public final class RocketGovernedPassengerGameTestSupport {
    private RocketGovernedPassengerGameTestSupport() { }

    public static EscrowGovernedPhases.Result reconnect(MinecraftServer server, RocketTransferSavedData journal,
                                                        RocketTransferRecord record, RocketEntity arrival) {
        EscrowTestEndpoints.requireEnabled(server);
        var runtime = EscrowRuntime.of(server);
        var bays = RocketSourceRetirementFixtures.recovery(RocketRuntime.installedTransfers()).bays();
        return runtime.flightPhases().tick(server, journal, record, null, arrival,
                () -> RocketGovernedLaunchBoundary.allows(server, journal, record, null, arrival, bays)
                        && runtime.flightPhases().allowsNative(null, arrival));
    }
}
