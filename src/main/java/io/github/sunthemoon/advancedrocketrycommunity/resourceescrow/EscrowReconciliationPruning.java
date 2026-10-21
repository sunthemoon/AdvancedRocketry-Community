package io.github.sunthemoon.advancedrocketrycommunity.resourceescrow;

import io.github.sunthemoon.advancedrocketrycommunity.rocket.entity.RocketEntity;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.fuel.loader.FluidFuelLoaderBlockEntity;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.fuel.loader.LoaderEscrowSection;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.fuel.loader.LoaderRoot;
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

    /** The loader's one-use C8/O4 marker permit, bound to its exact envelope; the tank, identity and epoch never change. */
    public static final class SourcePermit {
        private final Thread owner = Thread.currentThread();
        private final FluidFuelLoaderBlockEntity target;
        private final net.minecraft.nbt.CompoundTag before;
        private final EscrowPrefix.State after;
        private final BooleanSupplier authority;
        private boolean consumed;
        SourcePermit(FluidFuelLoaderBlockEntity target, EscrowPrefix.State after, BooleanSupplier authority) {
            this.target = target; before = target.saveWithFullMetadata(); this.after = after; this.authority = authority;
        }
        public LoaderEscrowSection consume(FluidFuelLoaderBlockEntity loader) {
            EscrowTags.require(Thread.currentThread() == owner && !consumed && target == loader, "Invalid pruning permit");
            consumed = true;
            EscrowTags.require(authority.getAsBoolean() && before.equals(loader.saveWithFullMetadata()), "Pruning authority changed");
            return EscrowLoaderEndpoint.pruned(loader.root().orElseThrow(), after);
        }
    }

    /** The caller already observed the actual native identity; a retired marker never supplies it. */
    static Set<UUID> retired(EscrowLedger root, EscrowEndpoint endpoint, EscrowPrefix.State state, Set<UUID> unresolved) {
        if (!root.id().equals(state.ledger())) { throw new EscrowReconciliationView.Refusal(EscrowReason.LEDGER_REPLACED); }
        var receiptIds = EscrowSettlementTombstones.retiredIds(root.tombstones());
        var retired = new HashSet<UUID>(); var seen = new HashSet<UUID>(); boolean retained = false;
        long sequence = 0, epoch = state.epoch() - state.markers().size();
        for (var marker : state.markers()) {
            String role = EscrowAdapterIds.nativeRocket(endpoint) && EscrowSignatureKind.endpoint(endpoint) == EscrowSignatureKind.ROCKET_COMPOSITE_V1
                    ? marker.role() : (EscrowFluidCargoEndpoint.is(endpoint) && endpoint.type().endsWith(":rocket_fluid_unloader")
                    || EscrowItemCargoEndpoint.is(endpoint) && endpoint.type().endsWith(":rocket_item_unloader"))
                    ? "DESTINATION" : endpoint.kind() == EscrowEndpoint.Kind.ENTITY ? "DESTINATION" : "SOURCE";
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
            boolean composite = rocket.escrowSection().markers().stream().anyMatch(marker -> marker.role().equals("SOURCE"))
                    || coordinator.ledger().floors().stream().anyMatch(floor -> rocket.assemblyTransactionId().orElseThrow().equals(floor.getUUID("rocket_id"))
                    && EscrowSignatureKind.ROCKET_COMPOSITE_V1.name().equals(floor.getString("signature_kind")));
            var reference = new EscrowEndpoint(EscrowEndpoint.Kind.ENTITY, level.dimension().location().toString(), rocket.getUUID(),
                    rocket.assemblyTransactionId().orElseThrow(), EscrowAdapterIds.ROCKET, rocket.chunkPosition().x, 0, rocket.chunkPosition().z,
                    composite ? EscrowEndpoint.Unit.TANK : EscrowEndpoint.Unit.PRIMARY,
                    rocket.escrowSection().epoch(), fuel.resourceSignature(), fuel.resourceSignature());
            apply(level.getServer(), coordinator, reference, null, null);
        } catch (IllegalArgumentException | IllegalStateException | java.util.NoSuchElementException invalid) {
            // The existing marked-wrapper/resource gates remain closed. Live records are diagnosed by reconciliation.
        }
    }

    /**
     * Restart and next-observation cleanup for a loader whose section still holds a marker, from its paced attempt
     * (at most once per device per 20 ticks). Only an independently validated retired marker is removed.
     */
    public static void observe(FluidFuelLoaderBlockEntity loader) {
        var held = loader.root().orElse(null);
        if (held == null || !held.escrow().locked() || loader.isRemoved() || loader.dataStatus() != LoaderRoot.Status.VALID
                || !(loader.getLevel() instanceof ServerLevel level) || !level.getServer().isSameThread()) { return; }
        var data = ResourceEscrowSavedData.existing(level.getServer());
        if (data == null || !data.operational()) { return; }
        var position = loader.getBlockPos();
        var reference = new EscrowEndpoint(EscrowEndpoint.Kind.BLOCK_ENTITY, level.dimension().location().toString(), held.device(), null,
                EscrowAdapterIds.LOADER, position.getX(), position.getY(), position.getZ(), EscrowEndpoint.Unit.TANK,
                held.escrow().epoch(), held.signature(), held.signature());
        try { apply(level.getServer(), data.bind(level.getServer()), reference, null, null); }
        catch (IllegalArgumentException | IllegalStateException | java.util.NoSuchElementException invalid) {
            // The device stays locked; live records are diagnosed by reconciliation, never here.
        }
    }

    /** Restart cleanup for either cargo device role, under its existing paced attempt. */
    public static void observe(io.github.sunthemoon.advancedrocketrycommunity.rocket.cargo.device.RocketFluidCargoBlockEntity device) {
        var root = device.root().orElse(null);
        if (root == null || !root.marked() || !device.live() || device.unsupportedRoot()) { return; }
        var server = device.getLevel().getServer(); var data = ResourceEscrowSavedData.existing(server);
        if (data == null || !data.operational()) { return; }
        var pos = device.getBlockPos();
        var reference = new EscrowEndpoint(EscrowEndpoint.Kind.BLOCK_ENTITY, device.getLevel().dimension().location().toString(), root.device(), null,
                "advancedrocketrycommunity:" + root.role().id(), pos.getX(), pos.getY(), pos.getZ(), EscrowEndpoint.Unit.TANK,
                root.escrow().epoch(), root.signature(), root.signature());
        try { apply(server, data.bind(server), reference, null, null); }
        catch (IllegalArgumentException | IllegalStateException | java.util.NoSuchElementException invalid) {
            // A live member is diagnosed by reconciliation; unsupported or unproved markers remain held.
        }
    }

    public static void observe(io.github.sunthemoon.advancedrocketrycommunity.rocket.cargo.device.RocketItemCargoBlockEntity device) {
        var root = device.root().orElse(null);
        if (root == null || !root.marked() || !device.live() || device.unsupportedRoot()) { return; }
        var server = device.getLevel().getServer(); var data = ResourceEscrowSavedData.existing(server);
        if (data == null || !data.operational()) { return; }
        var pos = device.getBlockPos();
        var reference = new EscrowEndpoint(EscrowEndpoint.Kind.BLOCK_ENTITY, device.getLevel().dimension().location().toString(), root.device(), null,
                "advancedrocketrycommunity:" + io.github.sunthemoon.advancedrocketrycommunity.rocket.cargo.device.RocketItemCargoBlock.id(root.role()), pos.getX(), pos.getY(), pos.getZ(), EscrowEndpoint.Unit.CONTAINER,
                root.escrow().epoch(), root.signature(), root.signature());
        try { apply(server, data.bind(server), reference, null, null); }
        catch (IllegalArgumentException | IllegalStateException | java.util.NoSuchElementException invalid) {
            // A live member is diagnosed by reconciliation; unsupported or unproved markers remain held.
        }
    }

    /** The scheduler can see a just-added rocket before its first tick observation. */
    static void destination(MinecraftServer server, EscrowCoordinator coordinator, EscrowEndpoint endpoint) {
        var level = server.getLevel(ResourceKey.create(Registries.DIMENSION, ResourceLocation.tryParse(endpoint.level())));
        if (level == null || !EscrowReconciliationView.loaded(level, endpoint)
                || !(level.getEntity(endpoint.identity()) instanceof RocketEntity rocket)
                || !rocket.isAddedToWorld() || !rocket.operational()) { return; }
        coordinator.observePruningCandidate(rocket.escrowSection(), () -> observeCandidate(rocket, level, coordinator));
    }

    /**
     * Prune at the next C0/C2 observation of a source. Gated fixture sources have no tick adapter; the loader also
     * prunes from its own paced attempt ({@link #observe(FluidFuelLoaderBlockEntity)}).
     */
    static void source(MinecraftServer server, EscrowCoordinator coordinator, EscrowEndpoint endpoint) {
        if (!EscrowLoaderEndpoint.is(endpoint) && !EscrowFluidCargoEndpoint.is(endpoint) && !EscrowItemCargoEndpoint.is(endpoint)) { EscrowTestEndpoints.requireEnabled(server); }
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
        var ordinary = EscrowReconciliationObservation.observe(server, root.id(), endpoint);
        var observed = ordinary.settlement();
        if (observed.missing() || !observed.bound() || observed.state().markers().isEmpty()
                || expected != null && !expected.equals(observed) || EscrowSettlementTombstones.endpointLocked(root, endpoint.key())) { return; }
        var ids = retired(root, endpoint, ordinary.state(), coordinator.unresolvedIds());
        if (exact != null) { ids = ids.stream().filter(exact::contains).collect(java.util.stream.Collectors.toUnmodifiableSet()); }
        if (ids.isEmpty()) { return; }
        var markers = EscrowSettlementPruning.prune(observed.state().markers(), ids, coordinator.unresolvedIds());
        var state = new EscrowPrefix.State(root.id(), ordinary.state().epoch(), ordinary.state().signature(), markers);
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
                    && rocketConflict(op, endpoint.identity(), coordinator.currentReason(op)))
                    || EscrowSettlementTombstones.stateLocked(root, endpoint.logicalRocket(), endpoint.identity(),
                    rocket.escrowSection(), observed.state().signature(), observed.quantity())) { return; }
            for (var floor : root.floors()) {
                if (endpoint.logicalRocket().equals(floor.getUUID("rocket_id"))
                        && !EscrowReconciliationValidation.floorMatches(floor, endpoint, state, observed.invariantDigest())) { return; }
            }
            var head = root.rocketAuthorities().stream().filter(value -> value.logical().equals(endpoint.logicalRocket())).findFirst().orElse(null);
            if (head != null && (!head.owner().equals(rocket.ownerId().orElseThrow())
                    || head.kind() != observed.signatureKind() || !head.invariant().equals(observed.invariantDigest()))) { return; }
            if (chain != null) {
                var envelope = EscrowNativeEnvelope.rocket(rocket); envelope.put("wrapper", new RocketEscrowSection(root.id(), state.epoch(), markers).encode());
                if (EscrowReconciliationValidation.rocket(root.id(), chain, envelope, rocket.snapshot().orElseThrow()) == EscrowPrefix.Classification.OTHER) { return; }
            }
            rocket.pruneReconciledEscrow(new Permit(rocket, new RocketEscrowSection(root.id(), state.epoch(), markers),
                    () -> coordinator.ledger() == root && coordinator.operational() && (expected != null || coordinator.writerIdle())
                    && !coordinator.pruningBusy(endpoint.key()) && rocket.flightData().orElseThrow() == flight));
        } else if (EscrowLoaderEndpoint.is(endpoint)) {
            if (!(level.getChunkSource().getChunkNow(endpoint.x() >> 4, endpoint.z() >> 4)
                    .getBlockEntity(EscrowLoaderEndpoint.position(endpoint)) instanceof FluidFuelLoaderBlockEntity loader)) { return; }
            loader.pruneReconciledEscrow(new SourcePermit(loader, state, () -> coordinator.ledger() == root && coordinator.operational()
                    && (expected != null || coordinator.writerIdle()) && !coordinator.pruningBusy(endpoint.key())));
        } else if (EscrowFluidCargoEndpoint.is(endpoint)) {
            if (!(level.getChunkSource().getChunkNow(endpoint.x() >> 4, endpoint.z() >> 4)
                    .getBlockEntity(EscrowFluidCargoEndpoint.position(endpoint)) instanceof
                    io.github.sunthemoon.advancedrocketrycommunity.rocket.cargo.device.RocketFluidCargoBlockEntity device)) { return; }
            device.pruneReconciledEscrow(new EscrowFluidCargoPublication.PruningPermit(device, state,
                    () -> coordinator.ledger() == root && coordinator.operational()
                    && (expected != null || coordinator.writerIdle()) && !coordinator.pruningBusy(endpoint.key())));
        } else if (EscrowItemCargoEndpoint.is(endpoint)) {
            if (!(level.getChunkSource().getChunkNow(endpoint.x() >> 4, endpoint.z() >> 4)
                    .getBlockEntity(EscrowItemCargoEndpoint.position(endpoint)) instanceof
                    io.github.sunthemoon.advancedrocketrycommunity.rocket.cargo.device.RocketItemCargoBlockEntity device)) { return; }
            device.pruneReconciledEscrow(new EscrowItemCargoPublication.PruningPermit(device, state,
                    () -> coordinator.ledger() == root && coordinator.operational()
                    && (expected != null || coordinator.writerIdle()) && !coordinator.pruningBusy(endpoint.key())));
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

    static boolean rocketConflict(EscrowOperation operation, UUID identity, EscrowReason reason) {
        return !operation.rocket().identity().equals(identity) || reason.manual();
    }
}
