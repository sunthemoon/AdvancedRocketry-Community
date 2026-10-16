package io.github.sunthemoon.advancedrocketrycommunity.resourceescrow;

import io.github.sunthemoon.advancedrocketrycommunity.rocket.entity.RocketEntity;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.function.BooleanSupplier;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;

/** C8 removes ordinary retired markers only. Resources, identities and epochs never change here. */
public final class EscrowReconciliationPruning {
    private EscrowReconciliationPruning() { }

    /** Negative scheduling cache only. A possible orphan still needs every native/final check below. */
    static final class Candidates {
        private EscrowLedger cached;
        private Set<UUID> retained = Set.of();

        boolean possible(EscrowLedger root, RocketEscrowSection section) {
            if (section.markers().isEmpty()) { return false; }
            if (cached != root) {
                var ids = new HashSet<>(EscrowSettlementTombstones.retiredIds(root.tombstones()));
                root.records().forEach(op -> ids.add(op.id()));
                retained = Set.copyOf(ids); cached = root;
            }
            // Store/load/native changes cannot retire a live operation or a settlement receipt.
            // Do not cache a positive result or a failed observation: those depend on live state.
            return section.markers().stream().anyMatch(marker -> !retained.contains(marker.operation()));
        }
        void observe(EscrowLedger root, RocketEscrowSection section, Runnable observation) {
            if (possible(root, section)) { observation.run(); }
        }
    }

    public static final class Permit {
        private final Thread owner = Thread.currentThread();
        private final RocketEntity target;
        private final RocketEscrowSection before, after;
        private final BooleanSupplier authority;
        private boolean consumed;
        private Permit(RocketEntity target, RocketEscrowSection after, BooleanSupplier authority) {
            this.target = target; before = target.escrowSection(); this.after = after; this.authority = authority;
        }
        public RocketEscrowSection consume(RocketEntity rocket) {
            EscrowTags.require(Thread.currentThread() == owner && !consumed && target == rocket, "Invalid pruning permit");
            consumed = true;
            EscrowTags.require(authority.getAsBoolean() && before.equals(rocket.escrowSection()), "Pruning authority changed");
            return after;
        }
    }

    /** The caller already observed the actual native identity; a retired marker never supplies it. */
    static Set<UUID> retired(EscrowLedger root, EscrowEndpoint endpoint, EscrowPrefix.State state, Set<UUID> unresolved) {
        if (!root.id().equals(state.ledger())) { throw new EscrowReconciliationView.Refusal(EscrowReason.LEDGER_REPLACED); }
        var receiptIds = EscrowSettlementTombstones.retiredIds(root.tombstones());
        var retired = new HashSet<UUID>(); var seen = new HashSet<UUID>(); boolean retained = false;
        long sequence = 0, epoch = state.epoch() - state.markers().size();
        var role = endpoint.kind() == EscrowEndpoint.Kind.ENTITY ? "DESTINATION" : "SOURCE";
        for (var marker : state.markers()) {
            if (!seen.add(marker.operation()) || !role.equals(marker.role()) || marker.sequence() <= sequence || marker.epochAfter() != epoch + 1) {
                throw new EscrowReconciliationView.Refusal(EscrowReason.FOREIGN_MARKER);
            }
            sequence = marker.sequence(); epoch = marker.epochAfter();
            if (sequence > root.issuedThrough()) { throw new EscrowReconciliationView.Refusal(EscrowReason.LEDGER_BEHIND); }
            var record = root.records().stream().filter(op -> op.id().equals(marker.operation()) || op.sequence() == marker.sequence()).findFirst();
            if (record.isPresent()) {
                var op = record.orElseThrow(); var reference = role.equals("SOURCE") ? op.source() : op.destination();
                if (!reference.key().equals(endpoint.key()) || !new EscrowPrefix.Link(op, reference, role).marker().equals(marker)) {
                    throw new EscrowReconciliationView.Refusal(EscrowReason.FOREIGN_MARKER);
                }
                retained = true;
            } else if (unresolved.contains(marker.operation()) || receiptIds.contains(marker.operation())) {
                // Settlement markers have their own exact release/identity rules, including O3 before O4.
                retained = true;
            } else {
                if (retained) { throw new EscrowReconciliationView.Refusal(EscrowReason.FOREIGN_MARKER); }
                retired.add(marker.operation());
            }
        }
        return Set.copyOf(retired);
    }

    static void acknowledged(MinecraftServer server, EscrowCoordinator coordinator, EscrowReconciliationCheckpoint request,
                             EscrowCoordinator.RetirementAcknowledgement ack) {
        var root = coordinator.ledger();
        EscrowTags.require(ack.committed() && ack.ledger().equals(root.id()) && ack.members().equals(request.members)
                && root.writerGeneration() == ack.generation() && ack.generation() > request.root.writerGeneration()
                && root.records().stream().noneMatch(op -> ack.members().contains(op.id())), "C8 needs this request's exact L2 acknowledgement");
        var runtime = EscrowRuntime.of(server); runtime.checkpoint("C8_PRUNE_BEFORE");
        for (var observation : request.ends.values()) {
            try { apply(server, coordinator, observation.endpoint(), ack.members(), observation); }
            catch (IllegalArgumentException | IllegalStateException | java.util.NoSuchElementException unavailable) {
                // Missing/unloaded/changed native objects are not overwritten. Checked restart/next observation
                // can remove only independently validated orphan markers; it cannot reconstruct an endpoint.
            }
        }
        runtime.checkpoint("C8_PRUNE_AFTER");
    }

    /** Restart/next-observation cleanup requires the checked baseline, not merely decoded ledger data. */
    public static void observe(RocketEntity rocket) {
        if (rocket.escrowSection().markers().isEmpty() || !rocket.isAddedToWorld() || !rocket.operational()
                || !(rocket.level() instanceof ServerLevel level) || !level.getServer().isSameThread()) { return; }
        var data = ResourceEscrowSavedData.existing(level.getServer());
        if (data == null || !data.operational()) { return; }
        var coordinator = data.bind(level.getServer());
        coordinator.observePruningCandidate(rocket.escrowSection(), () -> observeCandidate(rocket, level, coordinator));
    }

    private static void observeCandidate(RocketEntity rocket, ServerLevel level, EscrowCoordinator coordinator) {
        try {
            var fuel = rocket.flightData().orElseThrow().fuel();
            var reference = new EscrowEndpoint(EscrowEndpoint.Kind.ENTITY, level.dimension().location().toString(), rocket.getUUID(),
                    rocket.assemblyTransactionId().orElseThrow(), EscrowAdapterIds.ROCKET, rocket.chunkPosition().x, 0, rocket.chunkPosition().z,
                    EscrowEndpoint.Unit.PRIMARY, rocket.escrowSection().epoch(), fuel.resourceSignature(), fuel.resourceSignature());
            apply(level.getServer(), coordinator, reference, null, null);
        } catch (IllegalArgumentException | IllegalStateException | java.util.NoSuchElementException invalid) {
            // The existing marked-wrapper/resource gates remain closed. Live records are diagnosed by reconciliation.
        }
    }

    /** Gated sources have no production tick adapter; prune at the next registered C0/C2 observation. */
    static void source(MinecraftServer server, EscrowCoordinator coordinator, EscrowEndpoint endpoint) {
        EscrowTestEndpoints.requireEnabled(server);
        try { apply(server, coordinator, endpoint, null, null); }
        catch (EscrowReconciliationView.Refusal invalid) { throw invalid; }
        catch (IllegalArgumentException | IllegalStateException | java.util.NoSuchElementException unavailable) {
            // Cleanup is optional here; the C0/C2 view supplies the existing missing/unloaded diagnosis.
        }
    }

    private static void apply(MinecraftServer server, EscrowCoordinator coordinator, EscrowEndpoint endpoint, Set<UUID> exact,
                              EscrowSettlementIntent.Observation expected) {
        var root = coordinator.ledger();
        if (!server.isSameThread() || EscrowRuntime.of(server).stopping() || !coordinator.operational()
                || expected == null && !coordinator.writerIdle() || coordinator.pruningBusy(endpoint.key())) { return; }
        var observed = EscrowSettlementObserver.observe(server, root.id(), List.of(endpoint)).get(0);
        if (observed.missing() || !observed.bound() || observed.state().markers().isEmpty()
                || expected != null && !expected.equals(observed) || EscrowSettlementTombstones.endpointLocked(root, endpoint.key())) { return; }
        var ids = retired(root, endpoint, observed.state(), coordinator.unresolvedIds());
        if (exact != null) { ids = ids.stream().filter(exact::contains).collect(java.util.stream.Collectors.toUnmodifiableSet()); }
        if (ids.isEmpty()) { return; }
        var markers = EscrowSettlementPruning.prune(observed.state().markers(), ids, coordinator.unresolvedIds());
        var state = new EscrowPrefix.State(root.id(), observed.state().epoch(), observed.state().signature(), markers);
        var chain = EscrowPrefix.chains(root.records()).get(endpoint.key());
        if (chain != null && chain.stream().anyMatch(link -> coordinator.currentReason(link.operation()).manual())) { return; }
        if (chain != null && EscrowPrefix.classify(root.id(), chain, state) == EscrowPrefix.Classification.OTHER) { return; }
        if (chain == null && !markers.isEmpty()) { return; }
        var level = server.getLevel(ResourceKey.create(Registries.DIMENSION, ResourceLocation.tryParse(endpoint.level())));
        if (endpoint.kind() == EscrowEndpoint.Kind.ENTITY) {
            if (!(level.getEntity(endpoint.identity()) instanceof RocketEntity rocket)) { return; }
            var flight = rocket.flightData().orElseThrow();
            if (!endpoint.logicalRocket().equals(flight.logicalRocketId())
                    || root.armedFlights().stream().anyMatch(arm -> arm.logical().equals(endpoint.logicalRocket()))
                    || EscrowRuntime.of(server).flightCheckpoint().locked(endpoint.logicalRocket())
                    || root.records().stream().anyMatch(op -> op.guard().rocket().equals(endpoint.logicalRocket())
                    && (!op.destination().identity().equals(endpoint.identity()) || coordinator.currentReason(op).manual()))
                    || EscrowSettlementTombstones.stateLocked(root, endpoint.logicalRocket(), endpoint.identity(),
                    rocket.escrowSection(), state.signature(), observed.quantity())) { return; }
            for (var floor : root.floors()) {
                if (endpoint.logicalRocket().equals(floor.getUUID("rocket_id"))
                        && !EscrowReconciliationValidation.floorMatches(floor, endpoint, state, observed.invariantDigest())) { return; }
            }
            var head = root.rocketAuthorities().stream().filter(value -> value.logical().equals(endpoint.logicalRocket())).findFirst().orElse(null);
            if (head != null && (!head.owner().equals(rocket.ownerId().orElseThrow())
                    || head.kind() != observed.signatureKind() || !head.invariant().equals(observed.invariantDigest()))) { return; }
            if (chain != null) {
                var envelope = EscrowNativeEnvelope.rocket(rocket); envelope.put("wrapper", new RocketEscrowSection(root.id(), state.epoch(), markers).encode());
                if (EscrowReconciliationValidation.rocket(root.id(), chain, envelope) == EscrowPrefix.Classification.OTHER) { return; }
            }
            rocket.pruneReconciledEscrow(new Permit(rocket, new RocketEscrowSection(root.id(), state.epoch(), markers),
                    () -> coordinator.ledger() == root && coordinator.operational() && (expected != null || coordinator.writerIdle())
                    && !coordinator.pruningBusy(endpoint.key()) && rocket.flightData().orElseThrow() == flight));
        } else {
            EscrowTestEndpoints.requireEnabled(server);
            var source = level.getChunkSource().getChunkNow(endpoint.x() >> 4, endpoint.z() >> 4)
                    .getBlockEntity(new BlockPos(endpoint.x(), endpoint.y(), endpoint.z()));
            EscrowTestEndpoints.putState(source.getPersistentData(), state, false); source.setChanged();
        }
        EscrowRuntime.of(server).monitor().observed(endpoint.level(), endpoint.kind() == EscrowEndpoint.Kind.ENTITY,
                endpoint.kind() == EscrowEndpoint.Kind.ENTITY ? new ChunkPos(endpoint.x(), endpoint.z())
                        : new ChunkPos(new BlockPos(endpoint.x(), endpoint.y(), endpoint.z())));
    }
}
