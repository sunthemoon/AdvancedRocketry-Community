package io.github.sunthemoon.advancedrocketrycommunity.rocket.server;

import io.github.sunthemoon.advancedrocketrycommunity.resourceescrow.RocketEscrowGate;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.entity.RocketEntity;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketTransferRecord;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketTransferRecoveryAction;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.persistence.RocketTransferSavedData;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;

/** R52 (b2): destination adapters for W98's unchanged own-store survivor and exclusion proof. */
final class RocketDestinationRetirement extends RocketRetirementWorld {
    private final RocketTransferSavedData journal;
    private final RocketTransferFences fences;
    private final Set<UUID> live, settled;

    RocketDestinationRetirement(MinecraftServer server, RocketTransferSavedData journal, RocketTransferBayDispatch bays,
                                RocketTransferFences fences, Set<UUID> live, Set<UUID> settled) {
        super(server, bays); this.journal = journal; this.fences = fences; this.live = live; this.settled = settled;
    }

    @Override protected RocketEntity rocket(RocketTransferRecord record, UUID survivor) {
        var level = RocketTransferEntities.level(server, record.destinationSnapshot().sourceDimension());
        return level != null && level.getEntity(survivor) instanceof RocketEntity rocket ? rocket : null;
    }

    @Override public boolean crewReady(RocketTransferRecord record, UUID survivor, boolean offlineAllowed, Set<UUID> delivered) {
        var rocket = rocket(record, survivor);
        // The landed authority's empty manifest supersedes the former flight's reconnect assignments.
        return rocket != null && rocket.getPassengers().isEmpty() && rocket.flightData().orElseThrow().passengers().assignments().isEmpty();
    }

    @Override public boolean bayAllows(RocketTransferSavedData journal, RocketTransferRecord record, RocketTransferBayRecoveryGate.Action action) {
        var boundary = bays.before(server, journal, record, action);
        return boundary != null && boundary.kind() == RocketTransferBayRecoveryGate.BoundaryKind.UNCLAIMED;
    }

    @Override public boolean escrowAllows(RocketTransferSavedData journal, RocketTransferRecord record, RocketTransferSavedData.Reservation own) {
        return !fences.ids().contains(record.transferId()) && !live.contains(record.transferId()) && settled.contains(record.transferId())
                && bayAllows(journal, record, RocketTransferBayRecoveryGate.Action.REMOVE_JOURNAL)
                && super.escrowAllows(journal, record, own) && pristine(record);
    }

    private boolean pristine(RocketTransferRecord record) {
        var rocket = rocket(record, record.destinationEntityId().orElse(null));
        return rocket != null && RocketEscrowGate.retirementUnprotected(rocket);
    }

    @Override public CompoundTag projection(RocketTransferRecord record, UUID survivor) {
        var rocket = rocket(record, survivor); var origin = record.destinationSnapshot().sourceOrigin();
        if (!RocketTransferEntities.loadedEntity(rocket) || !RocketTransferEntities.isReplaceableLandedAuthority(rocket, record)
                || rocket.isPassenger() || rocket.flightData().orElseThrow().activeTransferId().isPresent()
                || !RocketRetirementGuards.onOriginColumn(rocket, record.destinationSnapshot())
                || rocket.getX() != origin.x() + 0.5D || rocket.getY() != origin.y() || rocket.getZ() != origin.z() + 0.5D
                || !crewReady(record, survivor, false, Set.of()) || !pristine(record)) { return null; }
        var tag = new CompoundTag();
        return rocket.save(tag) ? tag : null;
    }

    @Override public String prepare(RocketTransferRecord record, UUID survivor, List<RocketRetirementProof.Slot> census) {
        var sources = RocketTransferEntities.loadedMatches(server, record, false);
        var destinations = RocketTransferEntities.loadedMatches(server, record, true);
        String offColumn = RocketRetirementGuards.offColumn(record, null, sources, destinations);
        if (offColumn != null) { return offColumn; }
        if (destinations.stream().noneMatch(candidate -> candidate.getUUID().equals(survivor))) { return "SURVIVOR_CONFLICT"; }
        if (!bayAllows(journal, record, RocketTransferBayRecoveryGate.Action.ELECT_DUPLICATES)
                || !fences.actionAllowed(server, journal, record, RocketTransferRecoveryAction.REMOVE_SOURCE_KEEP_DESTINATION,
                sources, destinations, live, settled)) { return "DISCARD_HELD"; }
        var discarded = new ArrayList<>(sources);
        destinations.stream().filter(candidate -> !candidate.getUUID().equals(survivor)).forEach(discarded::add);
        // Complete the bounded census before mutating any entity, including a copy loaded after the first attempt.
        for (var candidate : discarded) {
            if (!RocketRetirementProof.addSlot(census, new RocketRetirementProof.Slot(candidate.level().dimension().location(),
                    candidate.chunkPosition()), RocketOrdinaryRetirement.MAX_SLOTS)) { return "CENSUS_LIMIT"; }
        }
        discarded.stream().filter(candidate -> !candidate.getUUID().equals(survivor)).forEach(RocketEntity::discard);
        return null;
    }

    @Override public void keepLoaded(RocketTransferRecord record, List<RocketRetirementProof.Slot> census) {
        var source = RocketTransferEntities.level(server, record.sourceSnapshot().sourceDimension());
        if (source != null) { RocketTransferEntities.keepLoaded(source, record.sourceSnapshot(), record.transferId()); }
        var destination = RocketTransferEntities.level(server, record.destinationSnapshot().sourceDimension());
        if (destination != null) { RocketTransferEntities.refreshOriginTicket(destination, record.destinationSnapshot(), record.transferId()); }
    }

    @Override public void releaseTickets(RocketTransferRecord record) { RocketTransferEntities.releaseFlightTickets(server, record); }
}
