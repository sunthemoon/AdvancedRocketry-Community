package io.github.sunthemoon.advancedrocketrycommunity.satellite.service;

import io.github.sunthemoon.advancedrocketrycommunity.AdvancedRocketryCommunity;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.content.SatelliteIdentity;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.mission.SatelliteOperationCode;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.mission.SatelliteOperationResult;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.persistence.SatelliteMissionSavedData;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

/**
 * Removal of the non-data kinds (ADR-049 section 7), with the terminal's registry barrier.
 */
final class SatelliteKindLifecycle {
    private final SolarLinks links;

    SatelliteKindLifecycle(SolarLinks links) {
        this.links = Objects.requireNonNull(links, "links");
    }

    /**
     * ADR-049 section 7: removes an idle satellite with a registry barrier flush before the terminal blanks
     * the chip. A crash between the two leaves an inert chip, never a duplicate.
     */
    SatelliteOperationResult decommission(ServerPlayer player, SatelliteIdentity identity, boolean operator) {
        if (!operator && !identity.ownerId().equals(player.getUUID())) {
            return SatelliteManager.failure(SatelliteOperationCode.UNAUTHORIZED);
        }
        MinecraftServer server = player.getServer();
        if (server == null) {
            return SatelliteManager.failure(SatelliteOperationCode.SERVER_ERROR);
        }
        try {
            SatelliteMissionSavedData data = SatelliteMissionSavedData.get(server);
            SatelliteOperationResult result = data.decommission(
                    identity.satelliteId(), player.getUUID(), operator, receiverMissing(server, identity.satelliteId()));
            // C7-L5: only a removal needs the barrier; a refusal writes nothing.
            if (result.changed()) {
                data.flush(server);
            }
            if (result.changed()) {
                AdvancedRocketryCommunity.LOGGER.info(
                        "ARCE_SATELLITE_DECOMMISSION satellite={} owner={} by={}",
                        identity.satelliteId(), identity.ownerId(), player.getUUID());
            }
            return result;
        } catch (RuntimeException exception) {
            SatelliteManager.logOperationFailure("decommission", exception);
            return SatelliteManager.failure(SatelliteOperationCode.UNSUPPORTED_DATA);
        }
    }

    /** Whether a solar satellite's linked receiver is confirmed missing (ADR-049 section 9). */
    private boolean receiverMissing(MinecraftServer server, UUID satelliteId) {
        return SatelliteMissionSavedData.get(server).satellite(satelliteId)
                .map(satellite -> links.receiverMissing(server, satellite))
                .orElse(false);
    }
}
