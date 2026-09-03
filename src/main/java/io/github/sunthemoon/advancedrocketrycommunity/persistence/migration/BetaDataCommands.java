package io.github.sunthemoon.advancedrocketrycommunity.persistence.migration;

import io.github.sunthemoon.advancedrocketrycommunity.AdvancedRocketryCommunity;
import io.github.sunthemoon.advancedrocketrycommunity.celestial.persistence.CelestialSavedData;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.persistence.RocketTransferSavedData;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.persistence.RocketTransactionSavedData;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.persistence.SatelliteMissionSavedData;
import io.github.sunthemoon.advancedrocketrycommunity.station.persistence.StationRegistrySavedData;
import net.minecraft.commands.Commands;
import net.minecraft.server.MinecraftServer;
import net.minecraftforge.event.RegisterCommandsEvent;

/** Permission-gated operator report for the managed Beta world roots. */
public final class BetaDataCommands {
    public void register(RegisterCommandsEvent event) {
        event.getDispatcher().register(
                Commands.literal("arce")
                        .then(Commands.literal("beta")
                                .requires(source -> source.hasPermission(2))
                                .then(Commands.literal("data-report")
                                        .executes(context -> report(context.getSource().getServer()))))
        );
    }

    private static int report(MinecraftServer server) {
        CelestialSavedData celestial = CelestialSavedData.get(server);
        RocketTransactionSavedData transactions = RocketTransactionSavedData.get(server);
        RocketTransferSavedData transfers = RocketTransferSavedData.get(server);
        StationRegistrySavedData stations = StationRegistrySavedData.get(server);
        SatelliteMissionSavedData missions = SatelliteMissionSavedData.get(server);
        boolean operational = celestial.isWritableSchema()
                && transactions.operational()
                && transfers.operational()
                && stations.operational()
                && missions.operational();
        AdvancedRocketryCommunity.LOGGER.info(
                "ARCE_BETA_DATA_REPORT root_schema={} operational={} celestial={} rocket_transactions={} rocket_transfers={} stations={} satellite_missions={} bodies={} transactions={} transfers={} station_records={} missions={}",
                SavedDataSchemaMigrator.CURRENT_SCHEMA_VERSION,
                operational,
                celestial.isWritableSchema(),
                transactions.operational(),
                transfers.operational(),
                stations.operational(),
                missions.operational(),
                celestial.entries().size(),
                transactions.entries().size(),
                transfers.entries().size(),
                stations.stations().size(),
                missions.missions().size()
        );
        return operational ? 1 : 0;
    }
}
