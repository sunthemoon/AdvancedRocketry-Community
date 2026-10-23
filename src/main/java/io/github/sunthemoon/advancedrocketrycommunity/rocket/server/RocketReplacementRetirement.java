package io.github.sunthemoon.advancedrocketrycommunity.rocket.server;

import io.github.sunthemoon.advancedrocketrycommunity.rocket.entity.RocketEntity;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketTransferRecord;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.persistence.RocketTransferSavedData;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.persistence.RocketTransactionSavedData;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.transaction.RocketRegion;
import java.util.Set;
import java.util.UUID;
import java.util.function.BooleanSupplier;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;

/** W150, R87: an ordinary successor keeps both predecessor ends until its single checked replacement. */
final class RocketReplacementRetirement extends RocketDestinationRetirement {
    private final RocketEntity destination;
    private final RocketTransferRecord previous, successor;
    private final CompoundTag accepted;
    private final io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketFlightData acceptedFlight;
    private final io.github.sunthemoon.advancedrocketrycommunity.rocket.model.RocketStructureSnapshot acceptedSnapshot;
    private final java.util.List<UUID> riders;
    private final BooleanSupplier authorized;
    private final Set<UUID> delivered = new java.util.HashSet<>();

    RocketReplacementRetirement(MinecraftServer server, RocketTransferSavedData journal, RocketTransferBayDispatch bays,
            RocketTransferFences fences, Set<UUID> live, Set<UUID> settled, RocketEntity destination,
            RocketTransferRecord previous, RocketTransferRecord successor, BooleanSupplier authorized) {
        super(server, journal, bays, fences, live, settled);
        this.destination = destination; this.previous = previous; this.successor = successor; this.authorized = authorized;
        acceptedFlight = destination.flightData().orElseThrow(); acceptedSnapshot = destination.snapshot().orElseThrow();
        riders = destination.getPassengers().stream().map(net.minecraft.world.entity.Entity::getUUID).toList();
        accepted = projection(previous, destination.getUUID());
    }

    /** R17 placement memory is per demand; a later voluntary dismount does not undo an observed old delivery. */
    @Override public boolean crewReady(RocketTransferRecord record, UUID survivor, boolean offlineAllowed, Set<UUID> ignored) {
        var rocket = rocket(record, survivor);
        return rocket != null && RocketRetirementGuards.crewReady(server, record, rocket, false, delivered);
    }

    @Override public CompoundTag projection(RocketTransferRecord record, UUID survivor) {
        var rocket = rocket(record, survivor); var origin = record.destinationSnapshot().sourceOrigin();
        if (!RocketTransferEntities.loadedEntity(rocket) || !RocketTransferEntities.isReplaceableLandedAuthority(rocket, record)
                || rocket.isPassenger() || rocket.flightData().orElseThrow().activeTransferId().isPresent()
                || !RocketRetirementGuards.onOriginColumn(rocket, record.destinationSnapshot())
                || rocket.getX() != origin.x() + 0.5D || rocket.getY() != origin.y() || rocket.getZ() != origin.z() + 0.5D
                || !pristine(record)) { return null; }
        var transactions = RocketTransactionSavedData.get(server);
        var footprint = RocketRegion.fromSnapshot(record.destinationSnapshot());
        if (!transactions.operational() || transactions.entries().stream().anyMatch(entry -> entry.record().region().overlaps(footprint))) {
            return null;
        }
        var tag = new CompoundTag();
        return rocket.save(tag) && RocketRetirementProof.matches(tag, tag) ? tag : null;
    }

    @Override public boolean requestCurrent() {
        var origin = acceptedSnapshot.sourceOrigin();
        // Cheap pending-request observation; terrain, planning and native projection are rechecked only at proof boundaries.
        return accepted != null && RocketTransferEntities.loadedEntity(destination) && destination.operational()
                && destination.ownerId().filter(previous.ownerId()::equals).isPresent()
                && destination.snapshot().orElse(null) == acceptedSnapshot && destination.flightData().filter(acceptedFlight::equals).isPresent()
                && destination.getX() == origin.x() + 0.5D && destination.getY() == origin.y() && destination.getZ() == origin.z() + 0.5D
                && riders.equals(destination.getPassengers().stream().map(net.minecraft.world.entity.Entity::getUUID).toList());
    }

    @Override public String recheck(RocketTransferRecord record, UUID survivor) {
        var sources = RocketTransferEntities.loadedMatches(server, record, false);
        var destinations = RocketTransferEntities.loadedMatches(server, record, true);
        String offColumn = RocketRetirementGuards.offColumn(record, null, sources, destinations);
        if (offColumn != null) { return offColumn; }
        // Any new candidate, including a saturated discovery, requires another complete capture; never discard at this boundary.
        return sources.stream().anyMatch(candidate -> !candidate.getUUID().equals(survivor))
                || destinations.size() != 1 || !destinations.get(0).getUUID().equals(survivor) ? "CENSUS_CHANGED" : null;
    }

    @Override public RocketRegion reservation() { return RocketRegion.fromSnapshot(successor.destinationSnapshot()); }

    @Override public boolean terminal(RocketTransferSavedData journal, RocketTransferRecord record, CompoundTag frozen,
            RocketTransferSavedData.Reservation own) {
        if (!requestCurrent() || !authorized.getAsBoolean() || !RocketRetirementProof.matches(frozen, accepted)
                || journal.find(record.transferId()).orElse(null) != previous
                || !journal.actionable(record.logicalRocketId(), own)) { return false; }
        journal.replace(record.transferId(), successor);
        return true;
    }
}
