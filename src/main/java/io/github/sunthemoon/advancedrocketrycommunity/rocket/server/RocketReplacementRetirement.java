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
    private final BooleanSupplier authorized, targetAvailable;
    private final Set<UUID> delivered = new java.util.HashSet<>();

    RocketReplacementRetirement(MinecraftServer server, RocketTransferSavedData journal, RocketTransferBayDispatch bays,
            RocketTransferFences fences, Set<UUID> live, Set<UUID> settled, RocketEntity destination,
            RocketTransferRecord previous, RocketTransferRecord successor, BooleanSupplier authorized, BooleanSupplier targetAvailable) {
        super(server, journal, bays, fences, live, settled);
        this.destination = destination; this.previous = previous; this.successor = successor; this.authorized = authorized;
        this.targetAvailable = targetAvailable;
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
        if (accepted == null) { return false; }
        if (survivor(previous, destination.getUUID(), RocketRetirementProof.Slot.origin(acceptedSnapshot))
                == RocketOrdinaryRetirement.Presence.UNLOADED) { return true; }
        var current = rocket(previous, destination.getUUID());
        var origin = acceptedSnapshot.sourceOrigin();
        // Cheap pending-request observation; terrain, planning and native projection are rechecked only at proof boundaries.
        return RocketTransferEntities.loadedEntity(current) && current.operational()
                && current.ownerId().filter(previous.ownerId()::equals).isPresent()
                && current.snapshot().filter(snapshot -> snapshot.snapshotId().equals(acceptedSnapshot.snapshotId())
                        && snapshot.contentHash().equals(acceptedSnapshot.contentHash())).isPresent()
                && current.flightData().filter(acceptedFlight::equals).isPresent()
                && current.getX() == origin.x() + 0.5D && current.getY() == origin.y() && current.getZ() == origin.z() + 0.5D
                && riders.equals(current.getPassengers().stream().map(net.minecraft.world.entity.Entity::getUUID).toList());
    }

    @Override protected boolean pristine(RocketTransferRecord record) {
        // A cold accepted entity is only a ticketing input. Live escrow history is still checked; proof needs a loaded image.
        var current = rocket(record, destination.getUUID());
        return io.github.sunthemoon.advancedrocketrycommunity.resourceescrow.RocketEscrowGate
                .retirementUnprotected(current == null ? destination : current);
    }

    @Override public boolean ticketReady(RocketTransferRecord record, UUID survivor) {
        return accepted != null && requestCurrent()
                && (survivor(record, survivor, RocketRetirementProof.Slot.origin(acceptedSnapshot))
                        == RocketOrdinaryRetirement.Presence.UNLOADED || projection(record, survivor) != null)
                && io.github.sunthemoon.advancedrocketrycommunity.rocket.transfer.RocketDiscoveryColumns
                        .of(successor.destinationSnapshot()).admissible();
    }

    @Override public void keepLoaded(RocketTransferRecord record, java.util.List<RocketRetirementProof.Slot> census) {
        super.keepLoaded(record, census);
        // The selected ordinary destination must also survive the final read-only pad check. Use its existing bounded
        // ticket set and candidate ID, not a new ticket type, margin, expiry or per-tick renewal.
        var target = RocketTransferEntities.level(server, successor.destinationSnapshot().sourceDimension());
        if (target != null) { RocketTransferEntities.keepDestinationLoaded(target, successor.destinationSnapshot(), successor.transferId()); }
    }

    @Override public void releaseTickets(RocketTransferRecord record) {
        super.releaseTickets(record);
        RocketTransferEntities.releaseFlightTickets(server, successor);
    }

    @Override public String recheck(RocketTransferRecord record, UUID survivor) {
        if (!targetAvailable.getAsBoolean()) { return "NEW_DESTINATION_UNAVAILABLE"; }
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
        if (!requestCurrent() || !authorized.getAsBoolean() || !targetAvailable.getAsBoolean() || !RocketRetirementProof.matches(frozen, accepted)
                || journal.find(record.transferId()).orElse(null) != previous
                || !journal.actionable(record.logicalRocketId(), own)) { return false; }
        journal.replace(record.transferId(), successor);
        return true;
    }
}
