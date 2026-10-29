package io.github.sunthemoon.advancedrocketrycommunity.rocket.server;

import io.github.sunthemoon.advancedrocketrycommunity.celestial.CelestialIds;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.bay.SatelliteBayPackage;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.bay.SatelliteBayRoot;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.entity.RocketEntity;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketFlightLimits;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketFlightState;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketTransferRecord;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.forge.SatelliteBayRocketAdapter;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.model.RocketBlock;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.model.RocketPosition;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.model.RocketStructureSnapshot;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.content.SatelliteIdentity;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.service.SatelliteRuntime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;

/**
 * D0's physical checks (W27A-DESIGN sections 4.1 and 7; W128's one-page design): the bays of T's source snapshot, the
 * live source at altitude, the orbit body (F5), both ends' bay census (W185-R25) and each bay's package and owners. Reads
 * only; it loads no chunk and writes nothing. Server thread only.
 */
final class BayDeploymentChecks {
    /** At most 128 block-entity visits per snapshot, and 64 snapshots cached by ID and content hash. */
    static final int VISITS = 128, CACHE = 64;

    /** One bay of the snapshot that holds a package; {@code decoded} is never null, {@code identity} may be. */
    record Bay(RocketPosition position, UUID bayOwner, CompoundTag pkg, SatelliteBayPackage.Decoded decoded) {
        Optional<SatelliteIdentity> identity() { return decoded.identity(); }
        @Nullable UUID satellite() { return decoded.identity().map(SatelliteIdentity::satelliteId).orElse(null); }
    }
    private record Key(UUID snapshot, String hash) { }
    private final LinkedHashMap<Key, List<Bay>> cache = new LinkedHashMap<>(16, 0.75f, true) {
        @Override protected boolean removeEldestEntry(Map.Entry<Key, List<Bay>> eldest) { return size() > CACHE; }
    };

    /** The bays holding a package, in position order; cached per snapshot ID and hash. */
    List<Bay> bays(RocketStructureSnapshot snapshot) {
        return cache.computeIfAbsent(new Key(snapshot.snapshotId(), snapshot.contentHash()), key -> read(snapshot));
    }

    Optional<Bay> bay(RocketStructureSnapshot snapshot, RocketPosition position) {
        return bays(snapshot).stream().filter(bay -> bay.position().equals(position)).findFirst();
    }

    void clear() { cache.clear(); }

    private static List<Bay> read(RocketStructureSnapshot snapshot) {
        var bays = new ArrayList<Bay>();
        int visits = 0;
        for (RocketBlock block : snapshot.blocks()) {
            var payload = block.blockEntityPayload().orElse(null);
            if (payload == null) { continue; }
            if (++visits > VISITS) { break; }
            if (!SatelliteBayRocketAdapter.ID.equals(payload.adapterId()) || payload.data().isEmpty()) { continue; }
            // The adapter refuses to capture a quarantined root, so an invalid one never flies; it is skipped, not claimed.
            var decoded = SatelliteBayRoot.decode(payload.data());
            if (!decoded.valid()) { continue; }
            var contents = decoded.contents().orElseThrow();
            var pkg = contents.packageTag().orElse(null);
            if (pkg == null) { continue; }
            bays.add(new Bay(block.position(), contents.owner(), pkg, SatelliteBayPackage.decode(pkg)));
        }
        bays.sort(Comparator.comparing(Bay::position));
        return List.copyOf(bays);
    }

    /** The in-session live source: loaded, current for T, in TRANSIT for T at its exact altitude position. */
    @Nullable
    static RocketEntity liveSource(MinecraftServer server, RocketTransferRecord record, UUID source) {
        for (RocketEntity candidate : RocketTransferEntities.loadedMatches(server, record, false)) {
            if (!candidate.getUUID().equals(source) || !RocketTransferEntities.currentEntity(candidate, record, false)) { continue; }
            var flight = candidate.flightData().orElse(null);
            var origin = record.sourceSnapshot().sourceOrigin();
            if (flight != null && flight.state() == RocketFlightState.TRANSIT
                    && flight.activeTransferId().filter(record.transferId()::equals).isPresent()
                    && candidate.getX() == origin.x() + 0.5D && candidate.getZ() == origin.z() + 0.5D
                    && candidate.getY() == origin.y() + RocketFlightLimits.FLIGHT_ALTITUDE_BLOCKS) {
                return candidate;
            }
        }
        return null;
    }

    /** F5 for every kind: the plan's captured source body must be a known orbitable body; legacy {@code space} never is. */
    static Optional<String> orbitRefusal(@Nullable ResourceLocation body) {
        var catalog = SatelliteRuntime.celestialCatalog();
        if (catalog.isEmpty()) { return Optional.of("CATALOG_UNAVAILABLE"); }
        if (body == null || CelestialIds.SPACE_ID.equals(body)) { return Optional.of("TARGET_NOT_ALLOWED"); }
        var definition = catalog.get().get(body);
        return definition.isPresent() && definition.get().capabilities().orbitable() ? Optional.empty() : Optional.of("TARGET_NOT_ALLOWED");
    }

    /**
     * W185-R25: D5 and D6 hold a claim at END_UNAVAILABLE, after consumption and without limit, when either end has no bay
     * census, such as an end that W170-R65's low strip takes past 16 columns (R150). D0 refuses every bay of such a flight
     * before any INTENT, with an existing reason, so no package is consumed for a claim that could not retire.
     */
    static Optional<String> censusRefusal(RocketTransferRecord record) {
        return censusRefusal(record.sourceSnapshot(), record.destinationSnapshot());
    }

    static Optional<String> censusRefusal(RocketStructureSnapshot source, RocketStructureSnapshot destination) {
        return BayAbsenceCensus.slots(source, destination) == null ? Optional.of("DEPLOYMENT_UNAVAILABLE") : Optional.empty();
    }

    /**
     * The per-bay checks before the receipt: package availability and bay/package/T owner agreement. An existing X is no
     * longer refused (W170 removes R49 Q4's interim refusal): D2 replays it as IDEMPOTENT and the bay is consumed.
     */
    static Optional<String> bayRefusal(Bay bay, RocketTransferRecord record) {
        if (!bay.decoded().available()) { return Optional.of(bay.decoded().availability().name()); }
        SatelliteIdentity identity = bay.identity().orElse(null);
        if (identity == null) { return Optional.of("INVALID_PACKAGE"); }
        if (!bay.bayOwner().equals(identity.ownerId()) || !identity.ownerId().equals(record.ownerId())) {
            return Optional.of("BAY_OWNER_MISMATCH");
        }
        return Optional.empty();
    }
}
