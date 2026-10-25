package io.github.sunthemoon.advancedrocketrycommunity.rocket.server;

import static io.github.sunthemoon.advancedrocketrycommunity.rocket.rejoin.RocketRejoinRoot.*;

import io.github.sunthemoon.advancedrocketrycommunity.AdvancedRocketryCommunity;
import io.github.sunthemoon.advancedrocketrycommunity.resourceescrow.*;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.entity.RocketEntity;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.*;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.persistence.RocketTransferSavedData;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.rejoin.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.function.Predicate;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.TicketType;
import net.minecraft.world.level.ChunkPos;

/** W80's server adapter. All capture authority is the existing shared ordinary barrier, never an invented endpoint. */
final class RocketReleaseWorld implements RocketReleaseCoordinator.Port {
    private static final TicketType<UUID> TICKET = TicketType.create("arce_rocket_release", Comparator.<UUID>naturalOrder(),
            RocketFlightLimits.FLIGHT_TICKET_TIMEOUT_TICKS);
    private final MinecraftServer server;
    private final RocketTransferSavedData journal;
    private final RocketTransferRecoveryService recovery;
    private final Set<UUID> live, settled;
    private final Set<UUID> scrubbed = new HashSet<>();
    private final Map<UUID, Set<UUID>> delivered = new HashMap<>();

    RocketReleaseWorld(MinecraftServer server, RocketTransferSavedData journal, RocketTransferRecoveryService recovery,
                       Set<UUID> live, Set<UUID> settled) {
        this.server = server; this.journal = journal; this.recovery = recovery; this.live = live; this.settled = settled;
    }

    RocketReleasePlan plan(RocketRejoinRoot root, UUID transfer) {
        var t = journal.find(transfer).orElse(null);
        if (t == null || t.nativeTransfer().isPresent() || !journal.actionable(t.logicalRocketId())) { return null; }
        Hold hold = t.phase() == RocketTransferPhase.PREPARED ? Hold.H1 : t.phase() == RocketTransferPhase.COMMITTED ? Hold.H2 : null;
        if (hold == null) { return null; }
        var copies = new ArrayList<RocketEntity>(); var elsewhere = new HashSet<UUID>();
        // Admission is read-only. Look at loaded seats everywhere, not only the two transfer ends; never activate a chunk.
        if (!loaded(rocket -> {
            if (!rocket.operational()) { return false; }
            if (names(rocket, t.logicalRocketId())) { copies.add(rocket); return copies.size() <= 1; }
            return true;
        }) || copies.size() != 1) { return null; }
        var authority = copies.get(0); var flight = authority.flightData().orElseThrow();
        if (!RocketTransferEntities.loadedEntity(authority) || authority.isPassenger()
                || authority.ownerId().filter(t.ownerId()::equals).isEmpty() || !RocketEscrowGate.idle(authority)) { return null; }
        if (hold == Hold.H1 ? !RocketTransferRecoveryService.recoveredStationarySource(t, authority,
                    settled.contains(transfer), live.contains(transfer)) || !flight.fuel().equals(t.sourceFlightData().fuel())
                : !live.contains(transfer) || !RocketTransferEntities.isReplaceableLandedAuthority(authority, t)) { return null; }
        var witness = hold == Hold.H1 ? t.sourceFlightData().passengers() : flight.passengers();
        var candidates = witness.assignments().stream().map(RocketPassengerSeat::passengerId).collect(java.util.stream.Collectors.toSet());
        if (!loaded(rocket -> {
            if (rocket != authority && rocket.operational()) {
                rocket.flightData().orElseThrow().passengers().assignments().stream().map(RocketPassengerSeat::passengerId)
                        .filter(candidates::contains).forEach(elsewhere::add);
            }
            return true;
        })) { return null; }
        for (var other : journal.entries()) {
            if (!other.transferId().equals(transfer)) {
                other.sourceFlightData().passengers().assignments().stream().map(RocketPassengerSeat::passengerId)
                        .filter(candidates::contains).forEach(elsewhere::add);
            }
        }
        try {
            var plan = RocketReleasePlan.create(root, UUID.randomUUID(), t, hold, authority.getUUID(),
                    authority.snapshot().orElseThrow(), flight, p -> server.getPlayerList().getPlayer(p) != null, elsewhere);
            return boundary(plan.batch()) ? plan : null;
        } catch (IllegalArgumentException refused) { return null; }
    }

    @Override public long now() { return server.getTickCount(); }
    @Override public boolean boundary(Batch batch) {
        if (RocketEscrowGate.escrowLogicalLocked(server, batch.rocket()) || recovery.bays().ids().contains(batch.transfer())) { return false; }
        var record = journal.find(batch.transfer()).orElse(null);
        if (record != null) {
            if (record.nativeTransfer().isPresent()) { return false; }
            var bay = recovery.bays().before(server, journal, record, RocketTransferBayRecoveryGate.Action.REMOVE_JOURNAL);
            if (bay == null || bay.kind() != RocketTransferBayRecoveryGate.BoundaryKind.UNCLAIMED) { return false; }
        }
        for (var level : server.getAllLevels()) {
            if (level.getEntity(batch.authority()) instanceof RocketEntity rocket) {
                var flight = rocket.flightData().orElse(null);
                if (!rocket.operational() || !RocketEscrowGate.idle(rocket) || rocket.isPassenger() || flight == null
                        || !level.dimension().location().toString().equals(batch.dimension())
                        || rocket.getX() != batch.pad().x() + .5D || rocket.getY() != batch.pad().y() || rocket.getZ() != batch.pad().z() + .5D
                        || !flight.state().acceptsFuel() || flight.activeTransferId().isPresent()
                        || !names(rocket, batch.rocket()) || !RocketReleasePlan.resources(rocket.snapshot().orElseThrow(), flight).equals(batch.resources())) {
                    return false;
                }
            }
        }
        return true;
    }

    @Override public boolean applySeats(Batch batch) {
        if (scrubbed.contains(batch.release())) { return true; }
        var copies = new ArrayList<RocketEntity>();
        if (!loaded(rocket -> {
            if (names(rocket, batch.rocket())) { copies.add(rocket); }
            return copies.size() <= RocketFlightLimits.MAX_TRANSFER_ENTITY_MATCHES;
        })) { return false; }
        for (var rocket : copies) {
            if (!rocket.operational() || !RocketEscrowGate.idle(rocket)) { return false; }
            var flight = rocket.flightData().orElseThrow();
            var filtered = RocketReleasePlan.filter(flight.passengers(), batch.released());
            if (!filtered.equals(flight.passengers())) { rocket.updateFlightData(flight.withPassengers(filtered)); }
        }
        scrubbed.add(batch.release()); return true;
    }

    @Override public boolean crewReady(Batch batch) {
        var t = journal.find(batch.transfer()).orElse(null);
        if (t == null) { return true; }
        if (batch.hold() != Hold.H1) { return false; }
        var remaining = t.releasePassengers(Set.copyOf(batch.released()));
        var memory = delivered.computeIfAbsent(batch.release(), id -> new HashSet<>());
        memory.addAll(recovery.retirements().delivered(t.transferId(), batch.authority()));
        var level = level(new Slot(batch.dimension(), batch.pad().x() >> 4, batch.pad().z() >> 4));
        var survivor = level != null && level.getEntity(batch.authority()) instanceof RocketEntity rocket ? rocket : null;
        return survivor != null ? RocketRetirementGuards.crewReady(server, remaining, survivor, false, memory)
                : RocketRetirementProof.crewReady(remaining.sourceFlightData().passengers().assignments().stream()
                        .map(RocketPassengerSeat::passengerId).toList(), id -> RocketRetirementProof.Seat.OFFLINE, memory, false);
    }

    @Override public RocketReleaseCoordinator.SlotState slot(Slot slot) {
        var level = level(slot);
        if (level == null) { return RocketReleaseCoordinator.SlotState.MISSING; }
        if (level.noSave() || !EscrowRuntime.of(server).store(level).entityCaptureSupported()) { return RocketReleaseCoordinator.SlotState.BLOCKED; }
        return level.areEntitiesLoaded(chunk(slot).toLong()) ? RocketReleaseCoordinator.SlotState.READY : RocketReleaseCoordinator.SlotState.UNLOADED;
    }
    @Override public void ticket(UUID release, Slot slot) {
        var level = Objects.requireNonNull(level(slot));
        level.getChunkSource().addRegionTicket(TICKET, chunk(slot), 2, release);
        level.getChunk(slot.x(), slot.z());
    }
    @Override public void unticket(UUID release, Slot slot) {
        var level = level(slot); if (level != null) { level.getChunkSource().removeRegionTicket(TICKET, chunk(slot), 2, release); }
    }
    @Override public boolean capture(Batch batch, Slot slot, RocketPassengerManifest b0, Consumer<RocketReleaseCoordinator.Proof> complete) {
        if (slot(slot) != RocketReleaseCoordinator.SlotState.READY) { return false; }
        var runtime = EscrowRuntime.of(server); var store = runtime.store(level(slot)); var count = new AtomicInteger(-1);
        if (!runtime.barrier().availableOrdinary(store, slot.level(), chunk(slot), now())) { return false; }
        return runtime.barrier().beginOrdinary(store, slot.level(), chunk(slot), root -> {
            int found = RocketReleaseProof.count(root, batch, slot, b0); count.set(found); return found >= 0;
        }, now(), label -> {
            if (label.equals("BARRIER_STORE_BEFORE")) { RocketReleaseNativeCuts.checkpoint(b0 != null ? "C0" : "B1_CAPTURE"); }
        },
                result -> complete.accept(readback(result, count.get())));
    }
    static RocketReleaseCoordinator.Proof readback(EscrowBarrier.Result result, int copies) {
        if (!result.confirmed() || copies < 0 || copies > 1) {
            if (result.proof() != null) { result.proof().release(); } return null;
        }
        return new RocketReleaseCoordinator.Proof() {
            public boolean valid() { return result.proof().valid(); }
            public int copies() { return copies; }
            public void close() { result.proof().release(); }
        };
    }
    @Override public void flushJournal() { journal.retryChecked(server); }
    @Override public void completed(Batch batch) {
        delivered.remove(batch.release()); scrubbed.remove(batch.release());
        if (journal.find(batch.transfer()).isEmpty()) { live.remove(batch.transfer()); settled.remove(batch.transfer()); }
    }
    @Override public void notice(Batch batch, String state) {
        if (state.equals("B0")) { RocketReleaseNativeCuts.arm(server); }
        if (Set.of("B0", "S1", "E1", "B1", "J1", "S2").contains(state)) {
            AdvancedRocketryCommunity.LOGGER.info("ARCE_REJOIN_RELEASE release={} transfer={} logical={} state={}", batch.release(), batch.transfer(), batch.rocket(), state);
        } else {
            AdvancedRocketryCommunity.LOGGER.warn("ARCE_REJOIN_RELEASE_HELD release={} transfer={} logical={} state={}", batch.release(), batch.transfer(), batch.rocket(), state);
        }
        RocketReleaseNativeProbe.notice(server, batch, state);
    }
    private ServerLevel level(Slot slot) { return RocketTransferEntities.level(server, ResourceLocation.tryParse(slot.level())); }
    private static ChunkPos chunk(Slot slot) { return new ChunkPos(slot.x(), slot.z()); }
    private static boolean names(RocketEntity rocket, UUID logical) {
        return rocket.assemblyTransactionId().filter(logical::equals).isPresent()
                || rocket.flightData().map(f -> f.logicalRocketId().equals(logical)).orElse(false);
    }
    /** Same native entity-node ceiling as a slot proof. Overflow is a refusal, never a truncated uniqueness claim. */
    private boolean loaded(Predicate<RocketEntity> visit) {
        int examined = 0;
        for (var level : server.getAllLevels()) {
            for (var entity : level.getAllEntities()) {
                if (++examined > RocketRetirementProof.MAX_ENTITY_TAGS) { return false; }
                if (entity instanceof RocketEntity rocket && !rocket.isRemoved() && !visit.test(rocket)) { return false; }
            }
        }
        return true;
    }
}
