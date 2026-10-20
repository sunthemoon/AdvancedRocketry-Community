package io.github.sunthemoon.advancedrocketrycommunity.resourceescrow;

import io.github.sunthemoon.advancedrocketrycommunity.rocket.entity.RocketEntity;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.persistence.RocketFlightNbtCodec;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.persistence.RocketTransferSavedData;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.fuel.RocketFluidFuelRuntime;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.fuel.loader.BankSelection;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.fuel.loader.FluidFuelLoaderBlockEntity;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.fuel.loader.FluidFuelLoaderStatus;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.fuel.loader.LoaderRoot;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.model.RocketNativeResources;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;

/**
 * Leaf B3-E's loader escrow adapter behind a gated switch (W11B3-DESIGN sections 2 to 4). The loaders' fair queue is
 * the only production caller: one visit captures both complete native envelopes in one owner-thread action, then
 * either the existing ledger's {@link EscrowCoordinator#prepareFill} or, in a NO_FILE world, the first-fill creation
 * order. Production admission stays disabled ({@link EscrowCoordinator#PRODUCTION_ADMISSION}); only release-test
 * hooks open the switch, per device in a GameTest or by property on a gated release dedicated server.
 */
public final class EscrowLoaderAdmission {
    /** The native release-test switch; it has no effect without the release-test hooks on a gated dedicated server. */
    public static final String SWITCH = "advancedrocketrycommunity.escrowLoaderAdmission";

    /** One fair-queue visit: the player status, and whether it ran an endpoint capture (the per-tick budget). */
    public record Visit(FluidFuelLoaderStatus status, boolean captured) { }
    private record Capture(EscrowNativeBinding.Admission admission, FluidFuelLoaderStatus refusal) {
        static Capture refused(FluidFuelLoaderStatus status) { return new Capture(null, status); }
    }

    private EscrowLoaderAdmission() { }

    /**
     * The gated switch. Production admission stays disabled; a GameTestServer opens it for a device that a test hook
     * named, and a gated release dedicated server (hooks, no RCON, only test-owned players) opens it by {@link #SWITCH}.
     */
    public static boolean enabled(MinecraftServer server, UUID device) {
        return switchOpen(EscrowCoordinator.PRODUCTION_ADMISSION, server.isSameThread(), () -> EscrowTestEndpoints.enabled(server),
                Boolean.getBoolean(EscrowTestEndpoints.HOOKS), Boolean.getBoolean(SWITCH),
                () -> EscrowRuntime.of(server).loaderAdmissionAllowed(device));
    }

    /**
     * The switch's decision (W95-R4), pure so that every combination is pinned: on the owner thread, the release-test
     * gate, and then either both properties or a device that a GameTest hook named. Neither property opens anything
     * without the gate, and the gate alone opens nothing. The gate is read only on the owner thread, and the device set
     * only behind the gate.
     */
    static boolean switchOpen(boolean production, boolean ownerThread, BooleanSupplier gate, boolean hooks, boolean property,
                              BooleanSupplier device) {
        if (production) { return true; }
        return ownerThread && gate.getAsBoolean() && (hooks && property || device.getAsBoolean());
    }

    /** prepareFill's typed results as player status; every member but QUEUED is a refusal that changed nothing. */
    static FluidFuelLoaderStatus status(EscrowCoordinator.Fill fill) {
        return switch (fill) {
            case QUEUED -> FluidFuelLoaderStatus.PREPARING;
            case UNAVAILABLE, IDENTITY_CONFLICT -> FluidFuelLoaderStatus.LEDGER_UNAVAILABLE;
            case INVALID -> FluidFuelLoaderStatus.INVALID_DATA;
            case STALE, ENDPOINT_BUSY -> FluidFuelLoaderStatus.ESCROW_PENDING;
            case ROCKET_LIMIT, ESCROW_FULL, SERVER_FULL, ROCKET_FULL -> FluidFuelLoaderStatus.ESCROW_FULL;
        };
    }
    /** Cargo scheduling refusals are player-facing ROCKET_BUSY without changing the legacy fill dictionary. */
    static FluidFuelLoaderStatus status(EscrowCoordinator.Transfer transfer) {
        return switch (transfer) {
            case ROCKET_BUSY, OTHER_TRANSFER_PENDING, RECEIPT_BACKED -> FluidFuelLoaderStatus.ROCKET_BUSY;
            default -> status(transfer.fill());
        };
    }

    /** Mirror C-K's exclusivity without loading or creating a ledger. */
    public static boolean cargoPending(RocketEntity rocket) {
        if (!(rocket.level() instanceof ServerLevel level) || !level.getServer().isSameThread()) { return false; }
        var data = ResourceEscrowSavedData.existing(level.getServer());
        return data != null && data.operational() && data.bind(level.getServer()).cargoPending(rocket.assemblyTransactionId().orElseThrow());
    }

    /**
     * Design section 2, step 1, for a same-direction chain: the rocket's only escrow holds are ordinary loader fills
     * into this endpoint, its wrapper is exactly their applied prefix, and no flight, settlement, floor or receipt
     * names it. This replaces the idle test only; a frozen proof still refuses in prepareFill.
     */
    public static boolean chainable(RocketEntity rocket) {
        if (!(rocket.level() instanceof ServerLevel level) || !level.getServer().isSameThread() || !rocket.operational()) { return false; }
        var server = level.getServer(); var logical = rocket.assemblyTransactionId().orElseThrow();
        if (EscrowRuntime.of(server).flightCheckpoint().locked(logical)) { return false; }
        var data = ResourceEscrowSavedData.existing(server);
        if (data == null || !data.operational()) { return false; }
        var ledger = data.ledger(); var coordinator = data.bind(server); var section = rocket.escrowSection();
        if (section.ledger() != null && !section.ledger().equals(ledger.id())
                || ledger.armedFlights().stream().anyMatch(arm -> arm.logical().equals(logical))
                || ledger.rocketAuthorities().stream().anyMatch(head -> head.logical().equals(logical))
                || EscrowSettlementTombstones.history(ledger, logical)
                || ledger.floors().stream().anyMatch(floor -> logical.equals(floor.getUUID("rocket_id")))) { return false; }
        var key = rocketKey(level, rocket);
        var chain = coordinator.unresolvedChain(key);
        return !chain.isEmpty() && coordinator.ordinaryFillsOnly(logical, key) && !coordinator.frozen(key)
                && appliedPrefix(chain, section.markers());
    }

    /**
     * One fair-queue visit's steps 2 and 3, after the caller's eligibility: one capture, then the reservation and queue
     * entry as one owner-thread action. A refusal issues no sequence, changes no tank or bank and leaks no slot.
     */
    public static Visit admit(FluidFuelLoaderBlockEntity loader, RocketEntity rocket) {
        var level = (ServerLevel) loader.getLevel(); var server = level.getServer();
        EscrowTags.require(server.isSameThread() && loader.root().isPresent(), "Fill admission requires a loaded device on the server thread");
        var root = loader.root().orElseThrow();
        if (!enabled(server, root.device())) { return new Visit(FluidFuelLoaderStatus.ADMISSION_DISABLED, false); }
        var runtime = EscrowRuntime.of(server);
        if (runtime.stopping()) { return new Visit(FluidFuelLoaderStatus.LEDGER_UNAVAILABLE, false); }
        if (cargoPending(rocket)) { return new Visit(FluidFuelLoaderStatus.ROCKET_BUSY, false); }
        var journal = RocketTransferSavedData.get(server).acknowledgedGuard(rocket.assemblyTransactionId().orElseThrow()).orElse(null);
        if (journal == null) { return new Visit(FluidFuelLoaderStatus.TRANSFER_JOURNAL_UNPROVED, false); }
        var data = ResourceEscrowSavedData.existing(server);
        var coordinator = data == null ? null : data.bind(server);
        if (coordinator == null ? runtime.propulsion(server).state() != EscrowAdmissionAuthority.Delegate.State.NO_FILE
                : !coordinator.operational()) { return new Visit(FluidFuelLoaderStatus.LEDGER_UNAVAILABLE, false); }
        // A first fill checks against a virtual empty root carrying the ID that the new ledger will carry.
        UUID ledger = coordinator == null ? UUID.randomUUID() : coordinator.ledger().id();
        Capture capture;
        try { capture = capture(level, loader, root, rocket, coordinator, ledger, coordinator == null ? 1 : coordinator.nextSequence(), journal); }
        catch (IllegalArgumentException | IllegalStateException | java.util.NoSuchElementException | ArithmeticException changed) {
            return new Visit(FluidFuelLoaderStatus.ESCROW_PENDING, true);
        }
        if (capture.refusal() != null) { return new Visit(capture.refusal(), true); }
        var op = capture.admission().operation();
        var fills = runtime.fills(server);
        fills.preparing(op.id());
        Consumer<Boolean> completion = committed -> fills.resolved(op.id());
        var result = coordinator == null ? EscrowCoordinator.Transfer.of(create(server, runtime, ledger, capture.admission(), completion))
                : coordinator.prepareTransfer(capture.admission(), completion);
        if (result == EscrowCoordinator.Transfer.QUEUED) { loader.escrowAdmitted(); } else { fills.resolved(op.id()); }
        return new Visit(status(result), true);
    }

    /**
     * Gated GameTest seam: the same capture and prepareFill against a detached coordinator, so a deliberately missing
     * endpoint leaves its sticky REPAIR record outside the server's ledger. Never a production path.
     */
    static EscrowCoordinator.Fill admitDetachedForTests(FluidFuelLoaderBlockEntity loader, RocketEntity rocket,
                                                        EscrowCoordinator coordinator, Consumer<Boolean> completion) {
        var level = (ServerLevel) loader.getLevel(); EscrowTestEndpoints.requireEnabled(level.getServer());
        var journal = RocketTransferSavedData.get(level.getServer()).acknowledgedGuard(rocket.assemblyTransactionId().orElseThrow()).orElseThrow();
        var capture = capture(level, loader, loader.root().orElseThrow(), rocket, coordinator, coordinator.ledger().id(),
                coordinator.nextSequence(), journal);
        EscrowTags.require(capture.refusal() == null, "Detached fixture capture refused: " + capture.refusal());
        return coordinator.prepareFill(capture.admission(), completion);
    }

    /**
     * Design section 2's first fill. Step 1: the pure check against the virtual empty root, then the shared
     * reservation while the delegate is NO_FILE; a refusal at either point creates nothing. Step 2, with no refusal
     * path and nothing that can fail between the reservation and its queue entry: store the ledger, attach its
     * coordinator (LOADED) and queue the reserved operation. Steps 3 and 4 are the coordinator's creation baseline.
     */
    private static EscrowCoordinator.Fill create(MinecraftServer server, EscrowRuntime runtime, UUID ledger,
                                                 EscrowNativeBinding.Admission admission, Consumer<Boolean> completion) {
        return EscrowLedgerCreation.create(server, runtime, ledger, admission, completion).fill();
    }

    /**
     * Design section 2, step 2: select the unique bank against the rocket projected through its unresolved prefix,
     * then capture the complete envelopes, identities, epochs, hashes, owner, snapshot and journal guard at once. The
     * counted markers come from this same capture. Same-direction chaining uses the coordinator's prefix and never
     * bypasses an ordinary lock or a frozen frontier.
     */
    private static Capture capture(ServerLevel level, FluidFuelLoaderBlockEntity loader, LoaderRoot root, RocketEntity rocket,
                                   EscrowCoordinator coordinator, UUID ledger, long sequence, RocketTransferSavedData.JournalGuard journal) {
        String levelId = level.dimension().location().toString();
        var section = root.escrow(); var wrapper = rocket.escrowSection();
        if (section.locked() || section.ledger() != null && !section.ledger().equals(ledger)
                || wrapper.ledger() != null && !wrapper.ledger().equals(ledger)) { return Capture.refused(FluidFuelLoaderStatus.ESCROW_PENDING); }
        var key = rocketKey(level, rocket);
        var chain = coordinator == null ? List.<EscrowOperation>of() : coordinator.unresolvedChain(key);
        var flight = rocket.flightData().orElseThrow();
        if (chain.isEmpty() ? !wrapper.markers().isEmpty() : !appliedPrefix(chain, wrapper.markers())
                || EscrowPrefix.classify(ledger, links(chain), new EscrowPrefix.State(ledger, wrapper.epoch(),
                flight.fuel().resourceSignature(), wrapper.markers())) == EscrowPrefix.Classification.OTHER) {
            // A retired marker waits for pruning; any other wrapper is not this chain's applied prefix.
            return Capture.refused(FluidFuelLoaderStatus.ESCROW_PENDING);
        }
        var projected = EscrowNativeEnvelope.rocket(rocket);
        for (int i = wrapper.markers().size(); i < chain.size(); i++) {
            var member = chain.get(i);
            projected = EscrowReconciliationChange.rocket(ledger, member, projected);
            if (!fuel(projected).resourceSignature().equals(member.destination().afterSignature())) {
                return Capture.refused(FluidFuelLoaderStatus.ESCROW_PENDING);
            }
        }
        var before = fuel(projected); var beforeWrapper = RocketEscrowSection.decode(projected.getCompound("wrapper"));
        var plan = BankSelection.plan(before.propulsion().orElse(null), RocketFluidFuelRuntime.capture(), root.tank());
        if (plan.status() != FluidFuelLoaderStatus.READY) { return Capture.refused(plan.status()); }
        var step = plan.plan(); var tank = root.tank();
        boolean oxidizer = step.bank() == BankSelection.Bank.OXIDIZER;
        var moved = tank.withAmount(step.moved());
        if (!moved.escrowCarriable()) { return Capture.refused(FluidFuelLoaderStatus.INCOMPATIBLE_FLUID); }
        var bank = oxidizer ? before.propulsion().orElseThrow().oxidizer() : before.propulsion().orElseThrow().primary();
        var after = before.withFluidBank(oxidizer, tank.fluid(), tank.tag(), Math.addExact(bank.amount(), step.moved()));
        var position = loader.getBlockPos(); var chunk = rocket.chunkPosition();
        var source = new EscrowEndpoint(EscrowEndpoint.Kind.BLOCK_ENTITY, levelId, root.device(), null, EscrowAdapterIds.LOADER,
                position.getX(), position.getY(), position.getZ(), EscrowEndpoint.Unit.TANK, section.epoch(), root.signature(),
                tank.withAmount(tank.amount() - step.moved()).signature(root.device()));
        var destination = new EscrowEndpoint(EscrowEndpoint.Kind.ENTITY, levelId, rocket.getUUID(), key.logicalRocket(), EscrowAdapterIds.ROCKET,
                chunk.x, 0, chunk.z, oxidizer ? EscrowEndpoint.Unit.OXIDIZER : EscrowEndpoint.Unit.PRIMARY,
                beforeWrapper.epoch(), before.resourceSignature(), after.resourceSignature());
        var guard = new EscrowGuard(key.logicalRocket(), journal.transfer(), Math.max(1, journal.sequence()),
                journal.transfer() == null ? journal.rootDigest() : journal.recordDigest());
        var payload = moved.encode(); var owner = rocket.ownerId().orElseThrow();
        // Admission-time encoding keeps the full native check (R39 for fluids); the operation itself is structural.
        EscrowFluidCodec.validateNative(payload);
        var model = new EscrowOperation(UUID.randomUUID(), sequence, owner, level.getGameTime(), EscrowOperation.Phase.PREPARED,
                EscrowReason.NONE, payload, RocketNativeResources.signature(payload), source, destination, guard);
        var markers = new ArrayList<>(beforeWrapper.markers()); markers.add(new EscrowPrefix.Link(model, destination, "DESTINATION").marker());
        var afterWrapper = new RocketEscrowSection(ledger, destination.afterEpoch(), markers);
        var rocketBefore = EscrowNativeEnvelope.rocket(rocket, EscrowNativeEnvelope.fuelOnly(flight, before), beforeWrapper);
        var rocketAfter = EscrowNativeEnvelope.rocket(rocket, EscrowNativeEnvelope.fuelOnly(flight, after), afterWrapper);
        var sourceBefore = loader.saveWithFullMetadata(); var sourceAfter = EscrowLoaderEndpoint.change(ledger, model, sourceBefore);
        var snapshot = rocket.snapshot().orElseThrow();
        var binding = new EscrowNativeBinding(EscrowSignatureKind.FUEL_VECTOR_V1, rocket.getUUID(), key.logicalRocket(), owner,
                snapshot.snapshotId(), snapshot.contentHash(), EscrowInvariant.rocket(rocket), EscrowNativeBinding.reference(source),
                EscrowNativeBinding.reference(destination), EscrowNativeEnvelope.digest(beforeWrapper.encode()),
                EscrowNativeEnvelope.digest(afterWrapper.encode()), before.resourceSignature(), after.resourceSignature(),
                EscrowNativeEnvelope.digest(sourceBefore), EscrowNativeEnvelope.digest(sourceAfter),
                EscrowNativeEnvelope.digest(rocketBefore), EscrowNativeEnvelope.digest(rocketAfter), true);
        var op = new EscrowOperation(model.id(), model.sequence(), owner, model.createdTick(), model.phase(), model.reason(), payload,
                model.payloadHash(), source, destination, guard, model.kind(), model.settlement(), binding);
        // Revalidate both fixed C2 projections against the frozen operation; no external callback runs in between.
        EscrowTags.require(EscrowLoaderEndpoint.change(ledger, op, sourceBefore).equals(sourceAfter)
                && EscrowReconciliationChange.rocket(ledger, op, projected).getCompound("wrapper").equals(afterWrapper.encode())
                && loader.saveWithFullMetadata().equals(sourceBefore), "Native capture changed during admission");
        return new Capture(new EscrowNativeBinding.Admission(op, beforeWrapper), null);
    }

    /**
     * The device's escrow progress, or null when escrow names nothing for it. PREPARING lasts until the device's own
     * debit is applied (the creation baseline, L1 or an UNKNOWN L1 outcome); TRANSFERRING follows through the proofs
     * and L2; ESCROW_PENDING is a held or retired-but-unpruned state, never a claim that no fuel moved; LEDGER_UNAVAILABLE
     * appears only before the debit. Reading never creates a ledger.
     */
    public static FluidFuelLoaderStatus progress(FluidFuelLoaderBlockEntity loader) {
        var root = loader.root().orElse(null);
        if (root == null || !(loader.getLevel() instanceof ServerLevel level) || !level.getServer().isSameThread()) { return null; }
        var server = level.getServer();
        var data = ResourceEscrowSavedData.existing(server);
        if (data == null) { return root.escrow().locked() ? FluidFuelLoaderStatus.ESCROW_PENDING : null; }
        var coordinator = data.bind(server);
        var named = coordinator.unresolvedNaming(root.device());
        if (named.isEmpty()) {
            var position = loader.getBlockPos();
            var key = new EscrowEndpoint.Key(EscrowEndpoint.Kind.BLOCK_ENTITY, level.dimension().location().toString(), root.device(), null,
                    EscrowAdapterIds.LOADER, position.getX(), position.getY(), position.getZ(), EscrowEndpoint.Unit.TANK);
            return root.escrow().locked() || coordinator.frozenIdentity(root.device())
                    || EscrowSettlementTombstones.endpointLocked(data.ledger(), key) ? FluidFuelLoaderStatus.ESCROW_PENDING : null;
        }
        var op = named.get(0);
        boolean applied = root.escrow().markers().stream().anyMatch(marker -> marker.operation().equals(op.id()));
        var scheduler = EscrowRuntime.of(server).reconciliationScheduler();
        boolean held = coordinator.currentReason(op) != EscrowReason.NONE || op.phase() != EscrowOperation.Phase.PREPARED
                || !op.settlement().isEmpty() || scheduler != null && !scheduler.view(op.id()).reason().equals(EscrowReason.NONE.name());
        return progress(coordinator.durable(op.id()), applied, coordinator.operational(), coordinator.writerUnavailable(), held,
                coordinator.writerUnknown());
    }

    /**
     * The progress of one operation naming the device (W95-R1), pure so that every combination is pinned. "Applied" means
     * the device's section holds this operation's marker, so its own debit has happened, and no status after it may say
     * that no fuel moved: an unavailable coordinator or writer (a failed baseline, or three unresolved writes) then reads
     * ESCROW_PENDING, and LEDGER_UNAVAILABLE only before it. An available ledger shows PREPARING until the debit,
     * TRANSFERRING after it, and ESCROW_PENDING for a held durable record or an UNKNOWN write after the debit.
     */
    static FluidFuelLoaderStatus progress(boolean durable, boolean applied, boolean operational, boolean writerUnavailable,
                                          boolean held, boolean writerUnknown) {
        if (!operational || writerUnavailable) {
            return applied ? FluidFuelLoaderStatus.ESCROW_PENDING : FluidFuelLoaderStatus.LEDGER_UNAVAILABLE;
        }
        if (applied) {
            // A debit needs the exact L1 acknowledgement, so an applied operation that is not durable is held, not new.
            return !durable || held || writerUnknown ? FluidFuelLoaderStatus.ESCROW_PENDING : FluidFuelLoaderStatus.TRANSFERRING;
        }
        return durable && held ? FluidFuelLoaderStatus.ESCROW_PENDING : FluidFuelLoaderStatus.PREPARING;
    }

    /**
     * Queues a paced device's attempt once in the fair queue (W95-R5): READY while it waits there, or ESCROW_FULL, with
     * nothing queued, when the bounded queue is full; the device retries at its next interval.
     */
    public static FluidFuelLoaderStatus offer(FluidFuelLoaderBlockEntity loader, EscrowResourceQueue.Attempt attempt) {
        var server = ((ServerLevel) loader.getLevel()).getServer();
        return queued(EscrowRuntime.of(server).fills(server).offer(loader.root().orElseThrow().device(), attempt));
    }

    /** READY means that the device waits in the fair queue; a refused offer queued nothing. */
    static FluidFuelLoaderStatus queued(boolean waiting) {
        return waiting ? FluidFuelLoaderStatus.READY : FluidFuelLoaderStatus.ESCROW_FULL;
    }

    static EscrowEndpoint.Key rocketKey(ServerLevel level, RocketEntity rocket) {
        var chunk = rocket.chunkPosition();
        return new EscrowEndpoint.Key(EscrowEndpoint.Kind.ENTITY, level.dimension().location().toString(), rocket.getUUID(),
                rocket.assemblyTransactionId().orElseThrow(), EscrowAdapterIds.ROCKET, chunk.x, 0, chunk.z, EscrowEndpoint.Unit.PRIMARY);
    }

    private static List<EscrowPrefix.Link> links(List<EscrowOperation> chain) {
        return chain.stream().map(op -> new EscrowPrefix.Link(op, op.destination(), "DESTINATION")).toList();
    }

    /** The wrapper's markers are exactly the first members of this destination chain, in order. */
    private static boolean appliedPrefix(List<EscrowOperation> chain, List<EscrowPrefix.Marker> markers) {
        if (markers.size() > chain.size()) { return false; }
        var expected = links(chain);
        for (int i = 0; i < markers.size(); i++) {
            if (!expected.get(i).marker().equals(markers.get(i))) { return false; }
        }
        return true;
    }

    private static io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketFuelState fuel(net.minecraft.nbt.CompoundTag envelope) {
        return RocketFlightNbtCodec.decode(envelope.getCompound("flight")).data().orElseThrow().fuel();
    }
}
