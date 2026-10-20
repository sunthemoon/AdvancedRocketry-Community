package io.github.sunthemoon.advancedrocketrycommunity.satellite.service;

import io.github.sunthemoon.advancedrocketrycommunity.celestial.model.CelestialBodyDefinition;
import io.github.sunthemoon.advancedrocketrycommunity.celestial.persistence.CelestialSavedData;
import io.github.sunthemoon.advancedrocketrycommunity.celestial.service.CelestialCatalogManager;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.component.SatelliteBlueprints;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.content.SatelliteIdentity;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.mission.SatelliteOperationCode;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.mission.SatelliteOperationResult;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.model.*;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.persistence.SatelliteMissionSavedData;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Predicate;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;

/** Internal UUID-owner launch authority for terminal and bay adapters. It never flushes or extracts a package. */
public final class SatelliteLaunchService {
    public enum Branch { REPLAY, FRESH }
    private final SatelliteCatalogManager satellites;
    private final CelestialCatalogManager celestial;

    public SatelliteLaunchService(SatelliteCatalogManager satellites, CelestialCatalogManager celestial) {
        this.satellites = Objects.requireNonNull(satellites); this.celestial = Objects.requireNonNull(celestial);
    }

    /** A request can only originate in validation; registration rechecks it against this service and registry. */
    public static final class Request {
        private final SatelliteLaunchService service;
        private final SatelliteMissionSavedData registry;
        private final UUID owner;
        private final SatelliteIdentity identity;
        private final ResourceLocation body;
        private final Branch branch;
        private final SatelliteDefinition dataDefinition;
        private final SatelliteKindDefinition kindDefinition;
        private final SatelliteBlueprint blueprint;
        private final boolean discoveryRequired;

        private Request(SatelliteLaunchService service, SatelliteMissionSavedData registry, UUID owner,
                SatelliteIdentity identity, ResourceLocation body, Branch branch, SatelliteDefinition dataDefinition,
                SatelliteKindDefinition kindDefinition, SatelliteBlueprint blueprint, boolean discoveryRequired) {
            this.service = service; this.registry = registry; this.owner = owner; this.identity = identity;
            this.body = body; this.branch = branch; this.dataDefinition = dataDefinition;
            this.kindDefinition = kindDefinition; this.blueprint = blueprint; this.discoveryRequired = discoveryRequired;
        }
        public Branch branch() { return branch; }
        public UUID owner() { return owner; }
        public SatelliteIdentity identity() { return identity; }
        private SatelliteState idle(long time) {
            return SatelliteState.launchIdle(identity.satelliteId(), kindDefinition.id(), owner, time, body,
                    blueprint, kindDefinition.initialState(time));
        }
    }

    public record Validation(SatelliteOperationResult result, Optional<Request> request) { }

    public Validation validate(MinecraftServer server, UUID owner, SatelliteIdentity identity, ResourceLocation body) {
        if (server == null || !server.isSameThread()) { return refusal(SatelliteOperationCode.SERVER_ERROR); }
        return validate(SatelliteMissionSavedData.get(server), owner, identity, body,
                target -> CelestialSavedData.get(server).get(target).isPresent());
    }

    public SatelliteOperationResult register(MinecraftServer server, Request request, long gameTime) {
        if (server == null || !server.isSameThread()) { return failure(SatelliteOperationCode.SERVER_ERROR); }
        return register(SatelliteMissionSavedData.get(server), request, gameTime,
                target -> CelestialSavedData.get(server).get(target).isPresent());
    }

    // The same core is exercised without a server by unit tests; it is not a public caller-authority bypass.
    Validation validate(SatelliteMissionSavedData data, UUID owner, SatelliteIdentity identity, ResourceLocation body,
                        Predicate<ResourceLocation> discovered) {
        if (identity == null || owner == null) { return refusal(SatelliteOperationCode.UNSUPPORTED_DATA); }
        if (!owner.equals(identity.ownerId())) { return refusal(SatelliteOperationCode.UNAUTHORIZED); }
        if (!data.operational()) { return refusal(SatelliteOperationCode.REGISTRY_BLOCKED); }
        if (data.deploymentPending(identity.satelliteId())) { return refusal(SatelliteOperationCode.DEPLOYMENT_PENDING); }
        SatelliteState existing = data.satellite(identity.satelliteId()).orElse(null);
        if (existing != null) {
            boolean same = existing.ownerId().equals(owner) && existing.definitionId().equals(identity.definitionId())
                    && existing.kind() == identity.kind();
            var result = new SatelliteOperationResult(same ? SatelliteOperationCode.IDEMPOTENT : SatelliteOperationCode.IDENTITY_CONFLICT,
                    false, Optional.of(existing), Optional.empty(),
                    identity.kind() == SatelliteKind.DATA ? data.account(owner).balance() : 0);
            return new Validation(result, same ? Optional.of(new Request(this, data, owner, identity, body,
                    Branch.REPLAY, null, null, null, false)) : Optional.empty());
        }
        SatelliteCatalog catalog = satellites.current().orElse(null);
        if (catalog == null) { return refusal(SatelliteOperationCode.CATALOG_UNAVAILABLE); }
        Request request;
        SatelliteOperationResult admission;
        if (identity.kind() == SatelliteKind.DATA) {
            SatelliteDefinition definition = catalog.get(identity.definitionId()).orElse(null);
            if (definition == null) { return refusal(SatelliteOperationCode.DEFINITION_NOT_FOUND); }
            if (body == null || !definition.allows(body) || celestial.current().flatMap(c -> c.get(body)).isEmpty()) {
                return refusal(SatelliteOperationCode.TARGET_NOT_ALLOWED);
            }
            // DATA can discover its target: it never requires prior discovery to launch.
            boolean discover = !discovered.test(body);
            request = new Request(this, data, owner, identity, body, Branch.FRESH, definition, null, null, discover);
            admission = data.previewLaunch(identity.satelliteId(), SatelliteManager.initialMissionId(identity.satelliteId()),
                    owner, definition, body, discover);
        } else {
            SatelliteKindDefinition definition = catalog.kindDefinition(identity.definitionId())
                    .filter(candidate -> candidate.kind() == identity.kind()).orElse(null);
            if (definition == null || identity.components().size() <= 1 || catalog.kindDefinitionForPrimary(identity.components().get(1))
                    .filter(candidate -> candidate.id().equals(definition.id())).isEmpty()) {
                return refusal(SatelliteOperationCode.DEFINITION_NOT_FOUND);
            }
            CelestialBodyDefinition target = body == null ? null : celestial.current().flatMap(c -> c.get(body)).orElse(null);
            if (target == null || !definition.launchTargets().contains(body) || !target.capabilities().orbitable()
                    || target.discoveryRequired() && !discovered.test(body)) {
                return refusal(SatelliteOperationCode.TARGET_NOT_ALLOWED);
            }
            var evaluation = SatelliteBlueprints.evaluateIdentity(identity.components(), identity.kind(), definition.scanEnergy(), catalog.components());
            if (!evaluation.accepted()) { return refusal(evaluation.code()); }
            if (data.account(owner).lifetimeEarned() < definition.requiredLifetimeResearch()) {
                return refusal(SatelliteOperationCode.RESEARCH_LOCKED);
            }
            request = new Request(this, data, owner, identity, body, Branch.FRESH, null, definition,
                    new SatelliteBlueprint(identity.components(), false, evaluation.stats().orElseThrow()), false);
            admission = data.previewIdle(request::idle);
        }
        return new Validation(admission, admission.success() ? Optional.of(request) : Optional.empty());
    }

    SatelliteOperationResult register(SatelliteMissionSavedData data, Request request, long gameTime,
                                      Predicate<ResourceLocation> discovered) {
        if (request == null || request.service != this || request.registry != data) { return failure(SatelliteOperationCode.SERVER_ERROR); }
        Validation revalidated = validate(data, request.owner, request.identity, request.body, discovered);
        if (revalidated.request().isEmpty() || revalidated.request().orElseThrow().branch == Branch.REPLAY) {
            return revalidated.result();
        }
        Request fresh = revalidated.request().orElseThrow();
        return fresh.identity.kind() == SatelliteKind.DATA
                ? data.launch(fresh.identity.satelliteId(), SatelliteManager.initialMissionId(fresh.identity.satelliteId()),
                        fresh.owner, fresh.dataDefinition, fresh.body, gameTime, fresh.discoveryRequired)
                : data.launchIdle(fresh::idle, gameTime);
    }

    private static SatelliteOperationResult failure(SatelliteOperationCode code) {
        return new SatelliteOperationResult(code, false, Optional.empty(), Optional.empty(), 0);
    }
    private static Validation refusal(SatelliteOperationCode code) { return new Validation(failure(code), Optional.empty()); }
}
