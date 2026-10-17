package io.github.sunthemoon.advancedrocketrycommunity.resourceescrow;

import io.github.sunthemoon.advancedrocketrycommunity.rocket.entity.RocketEntity;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.persistence.RocketTransferSavedData;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BooleanSupplier;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.entity.BlockEntity;

/** Bounded, loaded-only C0 component observation. A missing credited endpoint is never reconstructed. */
final class EscrowReconciliationView {
    static final class Refusal extends IllegalArgumentException {
        final EscrowReason reason;
        Refusal(EscrowReason reason) { super(reason.name()); this.reason = reason; }
    }
    record End(EscrowEndpoint reference, EscrowPrefix.State state, CompoundTag envelope,
               ServerLevel level, RocketEntity rocket, BlockEntity source, BooleanSupplier sameCapture) {
        boolean current() {
            if (!loaded(level, reference)) { return false; }
            if (rocket != null) {
                return !rocket.isRemoved() && level.getEntity(reference.identity()) == rocket
                        && RocketEscrowAudit.placement(reference, rocket) == EscrowReason.NONE
                        && io.github.sunthemoon.advancedrocketrycommunity.rocket.server.RocketRuntime.escrowReconciliationBoundary(rocket)
                        && sameCapture.getAsBoolean();
            }
            return !source.isRemoved() && level.getChunkSource().getChunkNow(reference.x() >> 4, reference.z() >> 4)
                    .getBlockEntity(new BlockPos(reference.x(), reference.y(), reference.z())) == source
                    && envelope.equals(source.saveWithFullMetadata());
        }
        boolean unchanged() {
            return current() && (rocket == null || state.equals(EscrowSettlementObserver
                    .observe(level.getServer(), state.ledger(), List.of(reference)).get(0).state()));
        }
    }
    final EscrowLedger root;
    final List<EscrowOperation> members;
    final Map<EscrowEndpoint.Key, End> ends;
    final BooleanSupplier journalLease;
    final long bytes;
    private final MinecraftServer server;

    private EscrowReconciliationView(MinecraftServer server, EscrowLedger root, List<EscrowOperation> members,
                                     Map<EscrowEndpoint.Key, End> ends, BooleanSupplier lease, long bytes) {
        this.server = server; this.root = root; this.members = members; this.ends = Map.copyOf(ends); journalLease = lease;
        this.bytes = bytes;
    }

    static EscrowReconciliationView capture(MinecraftServer server, EscrowLedger root, List<EscrowOperation> members) {
        requireCaptureBounds(server.isSameThread(), members.size());
        var journal = RocketTransferSavedData.get(server);
        // R17 item 3: C3 to C7 lease each member rocket's checked state; C2 shares this view and its lease.
        journal.retryChecked(server);
        if (!members.stream().allMatch(op -> journal.actionAuthorityAvailable(op.guard().rocket()))) {
            throw new Refusal(EscrowReason.TRANSFER_JOURNAL_UNPROVED);
        }
        var references = new LinkedHashMap<EscrowEndpoint.Key, EscrowEndpoint>();
        for (var op : members) {
            if (op.kind() != EscrowOperation.Kind.FLUID_PROPULSION_FILL || !op.nativeBinding().complete()
                    || op.nativeBinding().kind() != EscrowSignatureKind.FUEL_VECTOR_V1) { throw new Refusal(EscrowReason.INVALID_ENDPOINT); }
            var reason = RocketEscrowAudit.journal(op.guard(), journal.logicalGuard(op.guard().rocket()).orElse(null));
            if (reason != EscrowReason.NONE) { throw new Refusal(reason); }
            if (EscrowRuntime.of(server).flightCheckpoint().locked(op.guard().rocket())
                    || root.armedFlights().stream().anyMatch(arm -> arm.logical().equals(op.guard().rocket()))) { throw new Refusal(EscrowReason.NONE); }
            references.putIfAbsent(op.source().key(), op.source()); references.putIfAbsent(op.destination().key(), op.destination());
        }
        var chains = EscrowPrefix.chains(members); var ends = new LinkedHashMap<EscrowEndpoint.Key, End>();
        long bytes = 0;
        for (var endpoint : references.values()) {
            var level = server.getLevel(ResourceKey.create(Registries.DIMENSION, ResourceLocation.tryParse(endpoint.level())));
            if (level == null || !loaded(level, endpoint)) { throw new Refusal(EscrowReason.WAITING_UNLOADED); }
            RocketEntity rocket = null; BlockEntity source = null;
            if (endpoint.kind() == EscrowEndpoint.Kind.ENTITY) {
                int levels = 0;
                for (var known : server.getAllLevels()) {
                    if (++levels > 64) { throw new Refusal(EscrowReason.BARRIER_BOUNDS); }
                    if (known != level && known.getEntity(endpoint.identity()) != null) { throw new Refusal(EscrowReason.RELOCATED_ENDPOINT); }
                }
                var entity = level.getEntity(endpoint.identity());
                if (entity == null) { throw new Refusal(EscrowReason.MISSING_ENDPOINT); }
                if (!(entity instanceof RocketEntity nativeRocket)) { throw new Refusal(EscrowReason.INVALID_ENDPOINT); }
                rocket = nativeRocket;
                var reason = RocketEscrowAudit.placement(endpoint, rocket);
                if (reason != EscrowReason.NONE) { throw new Refusal(reason); }
                if (!io.github.sunthemoon.advancedrocketrycommunity.rocket.server.RocketRuntime.escrowReconciliationBoundary(rocket)) {
                    throw new Refusal(EscrowReason.NONE);
                }
            } else {
                // The registered loader adapter needs no hooks; every other source is a gated chest fixture.
                boolean loader = EscrowLoaderEndpoint.is(endpoint);
                if (!loader) { EscrowTestEndpoints.requireEnabled(server); }
                source = level.getChunkSource().getChunkNow(endpoint.x() >> 4, endpoint.z() >> 4)
                        .getBlockEntity(new BlockPos(endpoint.x(), endpoint.y(), endpoint.z()));
                if (source == null) { throw new Refusal(EscrowReason.MISSING_ENDPOINT); }
                if (!loader && !endpoint.type().equals("minecraft:chest")) { throw new Refusal(EscrowReason.INVALID_ENDPOINT); }
            }
            // Reuse the fixed registered adapters' identity, duplicate, occupancy and stationary checks.
            var observation = EscrowSettlementObserver.observe(server, root.id(), List.of(endpoint)).get(0);
            if (observation.missing()) { throw new Refusal(EscrowReason.MISSING_ENDPOINT); }
            var envelope = rocket != null ? EscrowNativeEnvelope.rocket(rocket) : source.saveWithFullMetadata();
            EscrowTags.bound(envelope, EscrowNativeEnvelope.MAX_BYTES, 24, 131_072);
            bytes = Math.addExact(bytes, io.github.sunthemoon.advancedrocketrycommunity.rocket.model.RocketNbtSize.uncompressedBytes(envelope));
            if (bytes > EscrowLimits.CAPTURE_BYTES) { throw new Refusal(EscrowReason.BARRIER_BOUNDS); }
            requirePrefix(root, endpoint, chains.get(endpoint.key()), observation.state(), envelope);
            if (EscrowSettlementTombstones.endpointLocked(root, endpoint.key()) || rocket != null
                    && EscrowSettlementTombstones.stateLocked(root, endpoint.logicalRocket(), endpoint.identity(), rocket.escrowSection(),
                    observation.state().signature(), observation.quantity())) { throw new Refusal(EscrowReason.FLOOR_CONFLICT); }
            if (rocket != null) {
                var head = root.rocketAuthorities().stream().filter(value -> value.logical().equals(endpoint.logicalRocket())).findFirst().orElse(null);
                if (head != null && (head.kind() != EscrowSignatureKind.FUEL_VECTOR_V1 || !head.owner().equals(rocket.ownerId().orElseThrow())
                        || !head.invariant().equals(EscrowInvariant.rocket(rocket)))) { throw new Refusal(EscrowReason.FLOOR_CONFLICT); }
                for (var floor : root.floors()) {
                    var key = EscrowFloors.key(floor);
                    if (key == null || members.stream().noneMatch(op -> endpoint.key().equals(op.destination().key())
                            && key.equals(EscrowFloors.key(op.guard())))) { continue; }
                    if (!EscrowReconciliationValidation.floorMatches(floor, endpoint, observation.state(), EscrowInvariant.rocket(rocket))) {
                        throw new Refusal(EscrowReason.FLOOR_CONFLICT);
                    }
                }
            }
            ends.put(endpoint.key(), new End(endpoint, observation.state(), envelope, level, rocket, source,
                    rocket == null ? () -> true : sameNativeCapture(rocket)));
        }
        return new EscrowReconciliationView(server, root, members, ends, EscrowFlightJournal.members(journal, members), bytes);
    }

    static void requireCaptureBounds(boolean ownerThread, int members) {
        EscrowTags.require(ownerThread, "Invalid native component capture thread");
        if (members > EscrowLimits.PER_ROCKET) { throw new Refusal(EscrowReason.BARRIER_BOUNDS); }
    }

    static void requireStoreCapacity(boolean stopping, boolean known, int size) {
        EscrowTags.require(!stopping, "Escrow runtime is stopping");
        if (!known && size >= 64) { throw new Refusal(EscrowReason.BARRIER_BOUNDS); }
    }

    /** A removable stale prefix is a cleanup wait, never authority to apply or a manual diagnosis. */
    static void requirePrefix(EscrowLedger root, EscrowEndpoint endpoint, List<EscrowPrefix.Link> chain,
                              EscrowPrefix.State state, CompoundTag envelope) {
        boolean rocket = endpoint.kind() == EscrowEndpoint.Kind.ENTITY;
        var classified = rocket ? EscrowReconciliationValidation.rocket(root.id(), chain, envelope)
                : EscrowPrefix.classify(root.id(), chain, state);
        if (classified != EscrowPrefix.Classification.OTHER) { return; }
        var retired = EscrowReconciliationPruning.retired(root, endpoint, state, java.util.Set.of());
        if (!retired.isEmpty()) {
            var markers = EscrowSettlementPruning.prune(state.markers(), retired, java.util.Set.of());
            var pruned = new EscrowPrefix.State(state.ledger(), state.epoch(), state.signature(), markers);
            var projected = envelope.copy();
            if (rocket) { projected.put("wrapper", new RocketEscrowSection(state.ledger(), state.epoch(), markers).encode()); }
            var after = rocket ? EscrowReconciliationValidation.rocket(root.id(), chain, projected)
                    : EscrowPrefix.classify(root.id(), chain, pruned);
            if (after != EscrowPrefix.Classification.OTHER) { throw new Refusal(EscrowReason.NONE); }
        }
        throw new Refusal(EscrowReason.OTHER_STATE);
    }

    /** Immutable native values plus the small mutable frame avoid re-encoding snapshots on every visit. */
    private static BooleanSupplier sameNativeCapture(RocketEntity rocket) {
        var snapshot = rocket.snapshot().orElseThrow(); var flight = rocket.flightData().orElseThrow();
        var wrapper = rocket.escrowSection(); var frame = EscrowNativeEnvelope.frame(rocket);
        return () -> rocket.operational() && rocket.snapshot().orElseThrow() == snapshot
                && rocket.flightData().orElseThrow() == flight && rocket.escrowSection() == wrapper
                && frame.equals(EscrowNativeEnvelope.frame(rocket));
    }

    boolean current(EscrowCoordinator coordinator) {
        return server.isSameThread() && !EscrowRuntime.of(server).stopping() && coordinator.operational()
                && coordinator.ledger() == root && journalLease.getAsBoolean()
                && members.stream().noneMatch(op -> coordinator.currentReason(op).manual()
                || EscrowRuntime.of(server).flightCheckpoint().locked(op.guard().rocket())
                || coordinator.admitting(op.source().key()) || coordinator.admitting(op.destination().key()))
                && ends.values().stream().allMatch(End::current);
    }

    /** Final action/freeze checks are deliberately not served from a scheduling cache. */
    boolean unchanged(EscrowCoordinator coordinator) {
        return current(coordinator) && ends.values().stream().allMatch(End::unchanged);
    }

    static boolean loaded(ServerLevel level, EscrowEndpoint endpoint) {
        var chunk = endpoint.kind() == EscrowEndpoint.Kind.ENTITY ? new ChunkPos(endpoint.x(), endpoint.z())
                : new ChunkPos(new BlockPos(endpoint.x(), endpoint.y(), endpoint.z()));
        return level.getChunkSource().getChunkNow(chunk.x, chunk.z) != null && level.areEntitiesLoaded(chunk.toLong())
                && level.isPositionEntityTicking(new BlockPos(chunk.getMinBlockX(), level.getMinBuildHeight(), chunk.getMinBlockZ()));
    }
}
