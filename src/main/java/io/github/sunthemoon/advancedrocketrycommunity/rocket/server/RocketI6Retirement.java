package io.github.sunthemoon.advancedrocketrycommunity.rocket.server;

import io.github.sunthemoon.advancedrocketrycommunity.rocket.entity.RocketEntity;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketFlightData;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketFlightLimits;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketTransferRecord;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.persistence.RocketTransferSavedData;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.model.RocketStructureSnapshot;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.persistence.RocketTransactionSavedData;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.transaction.RocketRegion;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.transaction.RocketTransactionRecord;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.function.BooleanSupplier;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;

/** I6's current destination and old-origin exclusion; never inherits the source-return unproved fallback. */
final class RocketI6Retirement extends RocketDestinationRetirement {
    private final RocketTransferRecord predecessor;
    private final UUID survivor;
    private final RocketFlightData flight;
    private final RocketStructureSnapshot snapshot;
    private final Set<UUID> delivered = new HashSet<>();
    private BooleanSupplier authorized = () -> true;
    private Terminal terminal;
    private RocketLandedDisassemblies.Port continuation;
    private boolean preparation, prepared;
    private BooleanSupplier preflight = () -> true;
    private boolean preflightRefused;

    interface Terminal {
        boolean submit(RocketTransferSavedData journal, RocketTransferRecord record,
                       RocketTransferSavedData.Reservation reservation);
    }

    void prepareOnly(BooleanSupplier authorized) { this.authorized = authorized; preparation = true; }
    void preflight(BooleanSupplier check) { preflight = java.util.Objects.requireNonNull(check); }
    void handoff(BooleanSupplier authorized, Terminal terminal, RocketLandedDisassemblies.Port continuation) {
        this.authorized = authorized; this.terminal = terminal; this.continuation = continuation;
    }
    @Override public boolean preparationOnly() { return preparation; }
    @Override public boolean preparationReady() {
        prepared = RocketTransferEntities.recoveryEndsAlreadyLoaded(server, predecessor); return prepared;
    }
    @Override public boolean needsTicketStep() { return terminal == null; }
    @Override public void keepLoaded(RocketTransferRecord record, List<RocketRetirementProof.Slot> census) {
        if (!preparation) { super.keepLoaded(record, census); return; }
        // The coordinator has checked authority/storage and admitted this tick's one cold preparation.
        // Ticket only: native chunk work progresses asynchronously, and preparationReady still checks every column.
        var runtime = io.github.sunthemoon.advancedrocketrycommunity.resourceescrow.EscrowRuntime.of(server);
        runtime.checkpoint("R52_TICKETING");
        RocketTransferEntities.refreshMargins(record.transferId(), record.sourceSnapshot(),
                record.destinationSnapshot(), RocketTransferEntities.levelTickets(server));
        runtime.checkpoint("R52_TICKETED");
    }
    @Override public void releaseTickets(RocketTransferRecord record) {
        // Completed preparation leaves its existing finite ticket to expire; it never renews while awaiting a click.
        if (!(preparation && prepared)) { super.releaseTickets(record); }
    }
    @Override public boolean terminal(RocketTransferSavedData journal, RocketTransferRecord record, CompoundTag frozen,
                                      RocketTransferSavedData.Reservation own) {
        return terminal == null ? super.terminal(journal, record, frozen, own) : terminal.submit(journal, record, own);
    }
    @Override public boolean afterAcknowledged(RocketTransferSavedData journal, RocketTransferSavedData.Reservation own,
                                               BooleanSupplier effect, Runnable discarded) {
        return continuation == null ? super.afterAcknowledged(journal, own, effect, discarded)
                : continuation.queueRemoval(own, effect, discarded);
    }

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
        return !preflightRefused && authorized.getAsBoolean() && RocketTransferEntities.loadedEntity(rocket) && rocket.operational()
                && rocket.ownerId().filter(predecessor.ownerId()::equals).isPresent()
                && rocket.snapshot().filter(snapshot::equals).isPresent()
                && rocket.flightData().filter(flight::equals).isPresent()
                && !rocket.isPassenger() && rocket.getPassengers().isEmpty()
                && rocket.getX() == origin.x() + 0.5D && rocket.getY() == origin.y() && rocket.getZ() == origin.z() + 0.5D;
    }

    @Override public CompoundTag projection(RocketTransferRecord record, UUID survivor) {
        if (!requestCurrent() || RocketReleaseAccess.fenced(server, record.logicalRocketId())) { return null; }
        var data = RocketTransactionSavedData.get(server);
        if (!data.operational() || data.entries().stream().anyMatch(entry -> transactionConflicts(
                entry.record(), record, snapshot, survivor))) { return null; }
        return super.projection(record, survivor);
    }

    static boolean transactionConflicts(RocketTransactionRecord transaction, RocketTransferRecord predecessor,
            RocketStructureSnapshot current, UUID survivor) {
        return transaction.region().overlaps(RocketRegion.fromSnapshot(current))
                || transaction.region().overlaps(RocketRegion.fromSnapshot(predecessor.sourceSnapshot()))
                || transaction.region().overlaps(RocketRegion.fromSnapshot(predecessor.destinationSnapshot()))
                || transaction.snapshotId().equals(current.snapshotId())
                || transaction.snapshotId().equals(predecessor.sourceSnapshot().snapshotId())
                || transaction.snapshotId().equals(predecessor.destinationSnapshot().snapshotId())
                || transaction.rocketEntityIdOptional().filter(id -> id.equals(survivor)
                        || id.equals(predecessor.sourceEntityId()) || predecessor.destinationEntityId().filter(id::equals).isPresent()).isPresent();
    }

    @Override public String prepare(RocketTransferRecord record, UUID survivor, List<RocketRetirementProof.Slot> census) {
        // The coordinator admits at most one destructive I6 preflight per tick. Recheck before any discard authority.
        if (!requestCurrent()) { return "REQUEST_CHANGED"; }
        if (!preflight.getAsBoolean()) { preflightRefused = true; return "PREFLIGHT_REFUSED"; }
        // Recovery of U must not regain an obsolete end after its last transfer authority is forgotten.
        if (projection(record, survivor) == null) { return "SURVIVOR_CONFLICT"; }
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
