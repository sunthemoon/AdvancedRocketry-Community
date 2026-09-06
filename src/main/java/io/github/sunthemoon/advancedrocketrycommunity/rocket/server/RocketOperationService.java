package io.github.sunthemoon.advancedrocketrycommunity.rocket.server;

import io.github.sunthemoon.advancedrocketrycommunity.rocket.entity.RocketEntity;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketDestination;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketFlightAction;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketFlightRequestResult;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketFlightData;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketTransferRecord;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.menu.RocketFlightQuotes;
import io.github.sunthemoon.advancedrocketrycommunity.travel.model.TravelTarget;
import io.github.sunthemoon.advancedrocketrycommunity.travel.migration.LegacyTravelTargetAdapter;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.MinecraftServer;

public interface RocketOperationService {
    default void onInstalled() {
    }

    void requestAssembler(ServerPlayer player, BlockPos assemblerPosition, boolean assemble);

    void requestDisassembly(ServerPlayer player, RocketEntity rocket);

    void openFlightMenu(ServerPlayer player, RocketEntity rocket);

    default Optional<TravelTarget> resolveCurrentTarget(ServerLevel level, BlockPos position) {
        return Optional.empty();
    }

    default Optional<RocketFlightData> migrateLegacyFlightData(
            ServerLevel level,
            RocketFlightData legacy
    ) {
        return Optional.empty();
    }

    default Optional<RocketTransferRecord> migrateCommittedLegacyTransfer(
            MinecraftServer server,
            RocketTransferRecord legacy
    ) {
        return Optional.empty();
    }

    default RocketFlightQuotes flightQuotes(ServerPlayer player, RocketEntity rocket) {
        return RocketFlightQuotes.empty();
    }

    void requestFlightIntent(
            ServerPlayer player,
            int rocketEntityId,
            RocketFlightAction action,
            TravelTarget target,
            UUID requestId
    );

    default void requestFlightIntent(
            ServerPlayer player,
            int rocketEntityId,
            RocketFlightAction action,
            RocketDestination destination,
            UUID destinationStationId,
            UUID requestId
    ) {
        requestFlightIntent(
                player,
                rocketEntityId,
                action,
                LegacyTravelTargetAdapter.fromLegacy(destination, destinationStationId),
                requestId
        );
    }

    RocketFlightRequestResult requestAdminFlight(
            RocketEntity rocket,
            TravelTarget destination,
            UUID requestId
    );

    default RocketFlightRequestResult requestAdminFlight(
            RocketEntity rocket,
            RocketDestination destination,
            UUID requestId
    ) {
        return requestAdminFlight(rocket, LegacyTravelTargetAdapter.fromLegacy(destination, null), requestId);
    }

    default RocketFlightRequestResult requestAdminStationFlight(
            RocketEntity rocket,
            UUID stationId,
            UUID requestId
    ) {
        return requestAdminFlight(rocket, new TravelTarget.Station(stationId), requestId);
    }
}
