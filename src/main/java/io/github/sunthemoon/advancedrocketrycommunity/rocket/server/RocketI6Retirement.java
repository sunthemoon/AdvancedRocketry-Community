package io.github.sunthemoon.advancedrocketrycommunity.rocket.server;

import io.github.sunthemoon.advancedrocketrycommunity.rocket.entity.RocketEntity;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketFlightData;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketFlightLimits;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketTransferRecord;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.persistence.RocketTransferSavedData;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.model.RocketStructureSnapshot;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.persistence.RocketTransactionSavedData;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.transaction.RocketRegion;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;

/** I6's current destination and old-origin exclusion; never inherits the source-return unproved fallback. */
final class RocketI6Retirement extends RocketDestinationRetirement {
    private final RocketTransferRecord predecessor;
    private final UUID survivor;
    private final RocketFlightData flight;
    private final RocketStructureSnapshot snapshot;
    private final Set<UUID> delivered = new HashSet<>();

    RocketI6Retirement(MinecraftServer server, RocketTransferSavedData journal, RocketTransferBayDispatch bays,
            RocketTransferFences fences, Set<UUID> live, Set<UUID> settled, RocketTransferRecord predecessor, RocketEntity rocket) {
        super(server, journal, bays, fences, live, settled);
        this.predecessor = predecessor; survivor = rocket.getUUID();
        flight = rocket.flightData().orElseThrow(); snapshot = rocket.snapshot().orElseThrow();
    }

    @Override public boolean crewReady(RocketTransferRecord record, UUID survivor, boolean offline, Set<UUID> ignored) {
        var rocket = rocket(record, survivor);
        return super.crewReady(record, survivor, false, Set.of())
                && RocketRetirementGuards.crewReady(server, record, rocket, false, delivered);
    }

    @Override public boolean requestCurrent() {
        var rocket = rocket(predecessor, survivor); var origin = snapshot.sourceOrigin();
        return RocketTransferEntities.loadedEntity(rocket) && rocket.operational()
                && rocket.ownerId().filter(predecessor.ownerId()::equals).isPresent()
                && rocket.snapshot().filter(snapshot::equals).isPresent()
                && rocket.flightData().filter(flight::equals).isPresent()
                && !rocket.isPassenger() && rocket.getPassengers().isEmpty()
                && rocket.getX() == origin.x() + 0.5D && rocket.getY() == origin.y() && rocket.getZ() == origin.z() + 0.5D;
    }

    @Override public CompoundTag projection(RocketTransferRecord record, UUID survivor) {
        if (!requestCurrent() || RocketReleaseAccess.fenced(server, record.logicalRocketId())) { return null; }
        var data = RocketTransactionSavedData.get(server); var footprint = RocketRegion.fromSnapshot(snapshot);
        if (!data.operational() || data.entries().stream().anyMatch(entry -> entry.record().region().overlaps(footprint)
                || entry.record().snapshotId().equals(snapshot.snapshotId())
                || entry.record().rocketEntityIdOptional().filter(survivor::equals).isPresent())) { return null; }
        return super.projection(record, survivor);
    }

    @Override public String prepare(RocketTransferRecord record, UUID survivor, List<RocketRetirementProof.Slot> census) {
        // A saturated bounded discovery is not a complete census and grants no discard authority.
        var sources = RocketTransferEntities.loadedMatches(server, record, false);
        var destinations = RocketTransferEntities.loadedMatches(server, record, true);
        if (sources.size() >= RocketFlightLimits.MAX_TRANSFER_ENTITY_MATCHES
                || destinations.size() >= RocketFlightLimits.MAX_TRANSFER_ENTITY_MATCHES) { return "CENSUS_LIMIT"; }
        return super.prepare(record, survivor, census);
    }

    @Override public String recheck(RocketTransferRecord record, UUID survivor) {
        var sources = RocketTransferEntities.loadedMatches(server, record, false);
        var destinations = RocketTransferEntities.loadedMatches(server, record, true);
        String offColumn = RocketRetirementGuards.offColumn(record, null, sources, destinations);
        if (offColumn != null) { return offColumn; }
        return sources.stream().anyMatch(candidate -> !candidate.getUUID().equals(survivor))
                || destinations.size() != 1 || !destinations.get(0).getUUID().equals(survivor) ? "CENSUS_CHANGED" : null;
    }

    @Override public RocketRegion reservation() { return RocketRegion.fromSnapshot(snapshot); }
}
