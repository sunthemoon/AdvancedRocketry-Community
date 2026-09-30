package io.github.sunthemoon.advancedrocketrycommunity.station.elevator;

import io.github.sunthemoon.advancedrocketrycommunity.celestial.model.CelestialBodyDefinition;
import io.github.sunthemoon.advancedrocketrycommunity.celestial.service.CelestialCatalog;
import io.github.sunthemoon.advancedrocketrycommunity.celestial.service.PlanetarySurfaceResolver;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Predicate;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;

/**
 * ADR-045 elevator endpoint validation as a pure function over a snapshot: no server, Level, chunk or
 * SavedData access. The rules are checked in order and the first failure is reported.
 */
public final class ElevatorEndpointValidator {
    /** Rule 5 coordinate bound, also enforced when the command parses its arguments. */
    public static final int MAX_COORDINATE = 30_000_000;

    private ElevatorEndpointValidator() {
    }

    /** The committed station record as the check sees it; the pad column is never client-supplied. */
    public record StationView(UUID stationId, UUID ownerId, ResourceLocation orbitBody, int padX, int padZ) {
        public StationView {
            Objects.requireNonNull(stationId, "stationId");
            Objects.requireNonNull(ownerId, "ownerId");
            Objects.requireNonNull(orbitBody, "orbitBody");
        }
    }

    public record Request(UUID stationId, ResourceLocation bodyId, int x, int z) {
        public Request {
            Objects.requireNonNull(stationId, "stationId");
            Objects.requireNonNull(bodyId, "bodyId");
        }
    }

    /** Reads a Level's world border without loading any chunk. */
    @FunctionalInterface
    public interface BorderCheck {
        boolean inside(ResourceKey<Level> level, int x, int z);
    }

    /**
     * One captured snapshot: registry availability, the station, the requester's authority, the
     * catalog captured once (rules 3 and 4 read the same capture), Level presence and the border.
     */
    public record Snapshot(boolean registryAvailable, Optional<StationView> station, UUID requesterId,
                           boolean operator, Optional<CelestialCatalog> catalog,
                           Predicate<ResourceKey<Level>> levelPresent, BorderCheck border) {
        public Snapshot {
            Objects.requireNonNull(station, "station");
            Objects.requireNonNull(requesterId, "requesterId");
            Objects.requireNonNull(catalog, "catalog");
            Objects.requireNonNull(levelPresent, "levelPresent");
            Objects.requireNonNull(border, "border");
        }
    }

    public record Result(ElevatorEndpointCode code, Optional<StationView> station, Optional<ResourceKey<Level>> level) {
        public boolean valid() {
            return code == ElevatorEndpointCode.VALID;
        }

        static Result failed(ElevatorEndpointCode code, Optional<StationView> station) {
            return new Result(code, station, Optional.empty());
        }
    }

    public static Result validate(Request request, Snapshot snapshot) {
        Objects.requireNonNull(request, "request");
        Objects.requireNonNull(snapshot, "snapshot");
        // Rule 1: an operational, unquarantined registry holding the station.
        if (!snapshot.registryAvailable()) {
            return Result.failed(ElevatorEndpointCode.REGISTRY_UNAVAILABLE, Optional.empty());
        }
        Optional<StationView> found = snapshot.station().filter(view -> view.stationId().equals(request.stationId()));
        if (found.isEmpty()) {
            return Result.failed(ElevatorEndpointCode.STATION_MISSING, Optional.empty());
        }
        StationView station = found.orElseThrow();
        // Rule 2: authority, before anything else about the station is disclosed.
        if (!snapshot.operator() && !station.ownerId().equals(snapshot.requesterId())) {
            return Result.failed(ElevatorEndpointCode.UNAUTHORIZED, Optional.empty());
        }
        // Rule 3: the station's current orbit body.
        if (!request.bodyId().equals(station.orbitBody())) {
            return Result.failed(ElevatorEndpointCode.NOT_CURRENT_ORBIT, found);
        }
        // Rule 4: a landable body whose present Level resolves to exactly this body.
        if (snapshot.catalog().isEmpty()) {
            return Result.failed(ElevatorEndpointCode.CATALOG_UNAVAILABLE, found);
        }
        CelestialCatalog catalog = snapshot.catalog().orElseThrow();
        Optional<CelestialBodyDefinition> body = catalog.get(request.bodyId())
                .filter(CelestialBodyDefinition::supportsSurfaceArrival);
        if (body.isEmpty()) {
            return Result.failed(ElevatorEndpointCode.NO_SURFACE, found);
        }
        ResourceKey<Level> level = body.orElseThrow().levelKey().orElseThrow();
        if (!snapshot.levelPresent().test(level)) {
            return Result.failed(ElevatorEndpointCode.LEVEL_ABSENT, found);
        }
        if (!PlanetarySurfaceResolver.find(catalog, level, true)
                .map(CelestialBodyDefinition::id).filter(request.bodyId()::equals).isPresent()) {
            return Result.failed(ElevatorEndpointCode.LEVEL_SHARED, found);
        }
        // Rule 5: the column is inside the coordinate bound and that Level's world border.
        if (Math.abs((long) request.x()) > MAX_COORDINATE || Math.abs((long) request.z()) > MAX_COORDINATE) {
            return Result.failed(ElevatorEndpointCode.OUT_OF_RANGE, found);
        }
        if (!snapshot.border().inside(level, request.x(), request.z())) {
            return Result.failed(ElevatorEndpointCode.OUTSIDE_WORLD_BORDER, found);
        }
        return new Result(ElevatorEndpointCode.VALID, found, Optional.of(level));
    }
}
