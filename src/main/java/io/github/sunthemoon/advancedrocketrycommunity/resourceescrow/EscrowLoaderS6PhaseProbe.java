package io.github.sunthemoon.advancedrocketrycommunity.resourceescrow;

import io.github.sunthemoon.advancedrocketrycommunity.AdvancedRocketryCommunity;
import io.github.sunthemoon.advancedrocketrycommunity.persistence.migration.CheckedSavedDataFile;
import io.github.sunthemoon.advancedrocketrycommunity.persistence.migration.ManagedSavedDataType;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.entity.RocketEntity;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.persistence.RocketTransferSavedData;
import java.util.Map;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.storage.LevelResource;

/** Read-only F4/F5 observations, plus R202's exactly selected destination store. */
final class EscrowLoaderS6PhaseProbe {
    private static final Map<String, String> HOOKS = Map.of(
            "LF_S6_F4_ACK", "F4_DESTINATION_ACK",
            "LF_S6_F4_PUBLICATION_BEFORE", "F4_PUBLICATION_BEFORE",
            "LF_S6_F4_PUBLICATION_AFTER", "F4_PUBLICATION_AFTER",
            "LF_S6_F4_STORED", "FIXTURE_PHASE_READBACK",
            "LF_S6_F5_PASSENGERS_ACK", "F5_PASSENGERS_TRANSFERRED_ACK",
            "LF_S6_F5_SOURCE_ACK", "F5_SOURCE_REMOVED_ACK",
            "LF_S6_F5_SOURCE_REMOVAL_BEFORE", "F5_SOURCE_REMOVAL_BEFORE",
            "LF_S6_F5_SOURCE_REMOVAL_AFTER", "F5_SOURCE_REMOVAL_AFTER",
            "LF_S6_F5_COMMITTED_ACK", "F5_COMMITTED_ACK");
    private EscrowLoaderS6PhaseProbe() { }
    static boolean selected(String cut) { return cut != null && HOOKS.containsKey(cut); }
    static boolean boundary(String cut, String point) {
        return selected(cut) && !cut.equals("LF_S6_F4_STORED") && HOOKS.get(cut).equals(point);
    }

    static void source(String cut, RocketEntity source) {
        var flight = source.flightData().orElseThrow();
        AdvancedRocketryCommunity.LOGGER.info("ARCE_LOADER_S6_FLIGHT_PUBLICATION_SOURCE cut={} phase_time={} digest={}",
                cut, flight.stateStartedGameTime(), EscrowGovernedEnvelope.digest(EscrowGovernedEnvelope.capture(source)));
    }

    static void checked(MinecraftServer server, String cut) {
        var file = server.getWorldPath(LevelResource.ROOT).resolve("data")
                .resolve(ManagedSavedDataType.ROCKET_TRANSFERS.fileName());
        var payload = CheckedSavedDataFile.readPayload(file, ManagedSavedDataType.ROCKET_TRANSFERS).orElseThrow();
        var rows = payload.getList("transfers", 10);
        EscrowTags.require(rows.size() == 1, "S6 phase observation requires one checked T");
        var row = rows.getCompound(0);
        AdvancedRocketryCommunity.LOGGER.info("ARCE_LOADER_S6_FLIGHT_PHASE cut={} phase={} digest={}",
                cut, row.getString("phase"), EscrowNativeEnvelope.digest(row));
    }

    static boolean store(MinecraftServer server, String cut) {
        if (!"LF_S6_F4_STORED".equals(cut)) { return false; }
        var rows = RocketTransferSavedData.get(server).entries();
        if (rows.size() != 1) { return false; }
        var row = rows.get(0);
        var target = row.destinationEntityId().map(server.overworld()::getEntity).orElse(null);
        if (!(target instanceof RocketEntity arrival)
                || !EscrowDestinationProbe.fixedDestination(row, EscrowNativeEnvelope.rocket(arrival))) { return false; }
        return EscrowLoaderS6StoredProbe.begin(server, cut, arrival);
    }
}
