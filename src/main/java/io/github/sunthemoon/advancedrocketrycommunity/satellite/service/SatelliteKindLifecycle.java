package io.github.sunthemoon.advancedrocketrycommunity.satellite.service;

import io.github.sunthemoon.advancedrocketrycommunity.AdvancedRocketryCommunity;
import io.github.sunthemoon.advancedrocketrycommunity.celestial.model.CelestialBodyDefinition;
import io.github.sunthemoon.advancedrocketrycommunity.celestial.persistence.CelestialSavedData;
import io.github.sunthemoon.advancedrocketrycommunity.celestial.service.CelestialCatalogManager;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.component.SatelliteBlueprints;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.content.SatelliteIdentity;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.mission.SatelliteOperationCode;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.mission.SatelliteOperationResult;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.model.SatelliteBlueprint;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.model.SatelliteKindDefinition;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.model.SatelliteState;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.persistence.SatelliteMissionSavedData;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

/**
 * Launch and removal of the non-data kinds (ADR-049 sections 6–7), kept beside {@link SatelliteManager} so
 * the data-mission authority does not absorb them. Both write paths end in the registry barrier flush.
 */
final class SatelliteKindLifecycle {
    private final SatelliteCatalogManager satelliteCatalogs;
    private final CelestialCatalogManager celestialCatalogs;

    SatelliteKindLifecycle(SatelliteCatalogManager satelliteCatalogs, CelestialCatalogManager celestialCatalogs) {
        this.satelliteCatalogs = Objects.requireNonNull(satelliteCatalogs, "satelliteCatalogs");
        this.celestialCatalogs = Objects.requireNonNull(celestialCatalogs, "celestialCatalogs");
    }

    /**
     * ADR-049 section 6: a non-data satellite launches idle into a chosen orbit body. A replay of an already
     * registered identity is idempotent before any validation, so a returned package is always consumed.
     * The registry barrier flush happens here, before the terminal extracts the package.
     */
    SatelliteOperationResult launchIdle(
            ServerPlayer player,
            SatelliteIdentity identity,
            ResourceLocation orbitBody
    ) {
        MinecraftServer server = player.getServer();
        if (server == null) {
            return SatelliteManager.failure(SatelliteOperationCode.SERVER_ERROR);
        }
        try {
            SatelliteMissionSavedData data = SatelliteMissionSavedData.get(server);
            SatelliteState existing = data.satellite(identity.satelliteId()).orElse(null);
            if (existing != null) {
                boolean same = existing.ownerId().equals(identity.ownerId())
                        && existing.definitionId().equals(identity.definitionId())
                        && existing.kind() == identity.kind();
                return new SatelliteOperationResult(
                        same ? SatelliteOperationCode.IDEMPOTENT : SatelliteOperationCode.IDENTITY_CONFLICT,
                        false, Optional.of(existing), Optional.empty(), 0);
            }
            SatelliteCatalog catalog = satelliteCatalogs.current().orElse(null);
            if (catalog == null) {
                return SatelliteManager.failure(SatelliteOperationCode.CATALOG_UNAVAILABLE);
            }
            SatelliteKindDefinition definition = catalog.kindDefinition(identity.definitionId())
                    .filter(candidate -> candidate.kind() == identity.kind())
                    .orElse(null);
            if (definition == null) {
                return SatelliteManager.failure(SatelliteOperationCode.DEFINITION_NOT_FOUND);
            }
            SatelliteOperationCode orbit = validateOrbitBody(server, definition, orbitBody);
            if (orbit != null) {
                return SatelliteManager.failure(orbit);
            }
            SatelliteBlueprints.Evaluation evaluation = SatelliteBlueprints.evaluateIdentity(
                    identity.components(), identity.kind(), definition.scanEnergy(), catalog.components());
            if (!evaluation.accepted()) {
                return SatelliteManager.failure(evaluation.code());
            }
            if (data.account(identity.ownerId()).lifetimeEarned() < definition.requiredLifetimeResearch()) {
                return SatelliteManager.failure(SatelliteOperationCode.RESEARCH_LOCKED);
            }
            SatelliteBlueprint blueprint = new SatelliteBlueprint(identity.components(), false,
                    evaluation.stats().orElseThrow());
            SatelliteOperationResult result = data.launchIdle(logicalTime -> SatelliteState.launchIdle(
                    identity.satelliteId(),
                    definition.id(),
                    identity.ownerId(),
                    logicalTime,
                    orbitBody,
                    blueprint,
                    definition.initialState(logicalTime)
            ), server.overworld().getGameTime());
            if (result.changed() || data.isDirty()) {
                data.flush(server);
            }
            return result;
        } catch (RuntimeException exception) {
            SatelliteManager.logOperationFailure("idle launch", exception);
            return SatelliteManager.failure(SatelliteOperationCode.UNSUPPORTED_DATA);
        }
    }

    /** A launch target of the definition, known, orbitable and discovered when it requires discovery. */
    private SatelliteOperationCode validateOrbitBody(
            MinecraftServer server,
            SatelliteKindDefinition definition,
            ResourceLocation orbitBody
    ) {
        if (orbitBody == null || !definition.launchTargets().contains(orbitBody)) {
            return SatelliteOperationCode.TARGET_NOT_ALLOWED;
        }
        CelestialBodyDefinition body = celestialCatalogs.current()
                .flatMap(celestial -> celestial.get(orbitBody))
                .orElse(null);
        if (body == null || !body.capabilities().orbitable()) {
            return SatelliteOperationCode.TARGET_NOT_ALLOWED;
        }
        if (body.discoveryRequired() && CelestialSavedData.get(server).get(orbitBody).isEmpty()) {
            return SatelliteOperationCode.TARGET_NOT_ALLOWED;
        }
        return null;
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
            if (result.changed() || data.isDirty()) {
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

    /** Whether a solar satellite's linked receiver is known to be gone; receivers arrive in C7c. */
    private boolean receiverMissing(MinecraftServer server, UUID satelliteId) {
        return false;
    }
}
