package io.github.sunthemoon.advancedrocketrycommunity.rocket.rejoin;

import static io.github.sunthemoon.advancedrocketrycommunity.rocket.rejoin.RocketRejoinRoot.*;

import io.github.sunthemoon.advancedrocketrycommunity.persistence.CanonicalNbtDigest;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.*;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.persistence.RocketFlightNbtCodec;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.model.RocketPosition;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.model.RocketStructureSnapshot;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.persistence.RocketSnapshotNbtCodec;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.transaction.RocketRegion;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.function.Predicate;

/** Frozen admission witnesses; the live manifest is retained only for B0, never written back by E1. */
public record RocketReleasePlan(Batch batch, List<Entry> entries, RocketPassengerManifest manifest) {
    public RocketReleasePlan { entries = List.copyOf(entries); }

    public static RocketReleasePlan create(RocketRejoinRoot root, UUID release, RocketTransferRecord transfer,
            Hold hold, UUID authority, RocketStructureSnapshot snapshot, RocketFlightData flight,
            Predicate<UUID> online, Set<UUID> seatedElsewhere) {
        require(transfer.nativeTransfer().isEmpty(), "Governed release refused");
        require(flight.logicalRocketId().equals(transfer.logicalRocketId()) && flight.activeTransferId().isEmpty()
                && flight.currentDimension().equals(snapshot.sourceDimension())
                && flight.currentOrigin().equals(snapshot.sourceOrigin()), "Release authority location or identity");
        require(hold == Hold.H1
                ? transfer.phase() == RocketTransferPhase.PREPARED
                    && (flight.state() == RocketFlightState.ASSEMBLED || flight.state() == RocketFlightState.FUELED)
                    && sameSnapshot(snapshot, transfer.sourceSnapshot())
                : transfer.phase() == RocketTransferPhase.COMMITTED && flight.state().acceptsFuel()
                    && transfer.destinationEntityId().filter(authority::equals).isPresent()
                    && sameSnapshot(snapshot, transfer.destinationSnapshot()), "Not a stationary legacy hold");
        require(root.batches().stream().noneMatch(b -> b.rocket().equals(transfer.logicalRocketId())
                && root.entries().stream().anyMatch(e -> e.release().equals(b.release()) && e.seat() == Seat.PENDING)),
                "Release already pending for rocket");
        var me = flight.passengers();
        var witness = hold == Hold.H1 ? transfer.sourceFlightData().passengers() : me;
        var absent = witness.assignments().stream().filter(s -> !online.test(s.passengerId())).toList();
        require(!absent.isEmpty(), "No absent passengers");
        var entries = new ArrayList<Entry>();
        for (var seat : absent) {
            UUID player = seat.passengerId(); var current = me.assignment(player);
            require(current.isEmpty() || !seatedElsewhere.contains(player), "Conflicting passenger assignment");
            require(root.entries().stream().noneMatch(e -> e.player().equals(player) && e.placement() == Placement.PENDING),
                    "Conflicting pending placement");
            entries.add(new Entry(release, player, current.orElse(seat).seatIndex(),
                    current.isPresent() ? Placement.PENDING : Placement.NOT_REQUIRED, Seat.PENDING, null));
        }
        var released = absent.stream().map(RocketPassengerSeat::passengerId).toList();
        var scrubbed = transfer.releasePassengers(Set.copyOf(released));
        var region = RocketRegion.fromSnapshot(snapshot);
        var slots = new LinkedHashSet<Slot>();
        slots.add(slot(snapshot)); slots.add(slot(transfer.sourceSnapshot())); slots.add(slot(transfer.destinationSnapshot()));
        var batch = new Batch(release, root.nextSequence(), transfer.transferId(), transfer.logicalRocketId(), authority, hold,
                snapshot.sourceDimension().toString(), position(snapshot.sourceOrigin()), position(region.minimum()),
                position(region.maximum()), resources(snapshot, flight), version(transfer), version(scrubbed), released, List.copyOf(slots));
        var plan = new RocketReleasePlan(batch, entries, me);
        // Capacity and the actual encoded envelope are part of admission, before any fence or native write.
        RocketRejoinCodec.encode(plan.admit(root));
        return plan;
    }

    public RocketRejoinRoot admit(RocketRejoinRoot root) { return root.admit(batch, entries); }

    public static RocketPassengerManifest filter(RocketPassengerManifest current, Collection<UUID> released) {
        for (UUID player : released) { current = current.remove(player); }
        return current;
    }

    public static Version version(RocketTransferRecord transfer) {
        return new Version(transfer.checksum(), transfer.phase().name(), transfer.destinationEntityId().orElse(null));
    }

    public static Resources resources(RocketStructureSnapshot snapshot, RocketFlightData flight) {
        return new Resources(snapshot.contentHash(), CanonicalNbtDigest.of(RocketFlightNbtCodec.encode(flight).getCompound("fuel"),
                RocketFlightLimits.MAX_FLIGHT_DATA_NBT_BYTES, 32));
    }

    private static Slot slot(RocketStructureSnapshot snapshot) {
        return new Slot(snapshot.sourceDimension().toString(), snapshot.sourceOrigin().x() >> 4, snapshot.sourceOrigin().z() >> 4);
    }
    private static boolean sameSnapshot(RocketStructureSnapshot actual, RocketStructureSnapshot recorded) {
        return actual == recorded || RocketSnapshotNbtCodec.encode(actual).equals(RocketSnapshotNbtCodec.encode(recorded));
    }
    private static Position position(RocketPosition p) { return new Position(p.x(), p.y(), p.z()); }
}
