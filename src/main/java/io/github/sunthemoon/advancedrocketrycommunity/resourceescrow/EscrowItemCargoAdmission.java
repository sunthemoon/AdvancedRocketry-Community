package io.github.sunthemoon.advancedrocketrycommunity.resourceescrow;

import io.github.sunthemoon.advancedrocketrycommunity.rocket.cargo.RocketCargoPreparations;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.cargo.RocketCargoSelection;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.cargo.RocketItemCargoSelection;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.cargo.device.*;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.persistence.RocketTransferSavedData;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.server.RocketRuntime;
import java.util.List;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;

/** Owner-thread, per-device admission continuation. It never publishes resources or acknowledges its own write. */
public final class EscrowItemCargoAdmission implements AutoCloseable {
    private final RocketItemCargoBlockEntity device;
    private RocketCargoPreparations.Ticket ticket;
    private EscrowCargoPreparedCapture capture;
    private RocketItemCargoRoot before;
    private CompoundTag nativeBefore;
    private EscrowItemCargoPayload delta;
    private io.github.sunthemoon.advancedrocketrycommunity.rocket.entity.RocketEntity eligible;
    private EscrowCoordinator coordinator;
    private EscrowLedger virtual;
    private RocketTransferSavedData.JournalGuard journal;
    private RocketCargoStatus status = RocketCargoStatus.ADMISSION_DISABLED;
    private java.util.UUID queuedDevice;
    private boolean turnClaimed;
    private boolean retrying;
    private RocketItemCargoSelection.Cursor selection;
    private final java.util.Set<EscrowItemCargoPayload> rejected = new java.util.HashSet<>();
    private io.github.sunthemoon.advancedrocketrycommunity.rocket.model.RocketStructureSnapshot selectionSnapshot;
    public EscrowItemCargoAdmission(RocketItemCargoBlockEntity device) { this.device = device; }
    public EscrowOperation.Kind kind() {
        return device.role() == RocketCargoRole.LOAD ? EscrowOperation.Kind.ITEM_CARGO_LOAD : EscrowOperation.Kind.ITEM_CARGO_UNLOAD;
    }
    public RocketCargoStatus status() { return status; }
    public boolean active() { return ticket != null; }
    public java.util.Optional<net.minecraft.core.BlockPos> cursor() {
        return ticket == null || delta == null ? java.util.Optional.empty() : java.util.Optional.of(
                new net.minecraft.core.BlockPos(delta.cursor().x(), delta.cursor().y(), delta.cursor().z()));
    }
    public int cursorSlot() { return ticket == null || delta == null ? -1 : kind().cargoLoad() ? delta.to() : delta.from(); }
    public RocketCargoStatus attempt() {
        var outcome = attemptOnce();
        if (outcome != RocketCargoStatus.READY && outcome != RocketCargoStatus.PREPARING
                && outcome != RocketCargoStatus.OTHER_TRANSFER_PENDING && outcome != RocketCargoStatus.ROCKET_BUSY) {
            device.yieldTransferTurn();
        }
        return outcome;
    }
    private RocketCargoStatus attemptOnce() {
        if (!device.live()) { return status = RocketCargoStatus.WAITING_UNLOADED; }
        EscrowReconciliationPruning.observe(device);
        var root = device.root().orElse(null); var server = ((ServerLevel) device.getLevel()).getServer();
        var refusal = device.deviceRefusal();
        if (refusal != null) { return status = refusal; }
        if (!EscrowCargoSwitches.enabled(server, kind(), root.device())) { return status = RocketCargoStatus.ADMISSION_DISABLED; }
        if (!device.refreshTurns()) { return status = RocketCargoStatus.ESCROW_FULL; }
        if (queuedDevice != null) { return status; }
        if (inspect() != null) { return status; }
        if (!EscrowRuntime.of(server).fills(server).offerCargo(root.device(), this::step, this::discard)) {
            discard();
            return status = RocketCargoStatus.ESCROW_FULL;
        }
        queuedDevice = root.device(); return status = RocketCargoStatus.READY;
    }
    private boolean step() {
        if (ticket == null) { retrying = false; begin(); } else { advance(); }
        return ticket == null && !retrying;
    }
    private void begin() {
        if (inspect() != null) { return; }
        if (!device.claimTransferTurn()) { status = RocketCargoStatus.OTHER_TRANSFER_PENDING; return; }
        turnClaimed = true;
        try {
            // An active replay or preparation ticket goes first; this walk resumes at the same position afterwards.
            var selected = EscrowRuntime.of(eligible.getServer()).cargoPreparations().select(selection,
                    eligible.getServer().getTickCount());
            if (selected == null) { retrying = true; status = RocketCargoStatus.PREPARING; return; }
            if (selected.status() != RocketCargoSelection.Status.ELIGIBLE) {
                var empty = selection.noMove();
                status = empty == null ? status(selected.status()) : RocketCargoStatus.valueOf(empty.name());
                return;
            }
            delta = selected.delta();
        } catch (io.github.sunthemoon.advancedrocketrycommunity.rocket.cargo.RocketCargoWorkBudget.Exceeded exceeded) {
            status = RocketCargoStatus.CARGO_TOO_LARGE; return;
        }
        ticket = EscrowRuntime.of(eligible.getServer()).cargoPreparations().begin(eligible, delta, kind().cargoLoad()).orElse(null);
        status = ticket == null ? RocketCargoStatus.OTHER_TRANSFER_PENDING : RocketCargoStatus.PREPARING;
    }
    /** Pure eligibility before queueing, repeated before taking the transient preparation ticket. */
    private RocketCargoStatus inspect() {
        if (active()) { return status; }
        var refusal = device.deviceRefusal();
        if (refusal != null) { return status = refusal; }
        var level = (ServerLevel) device.getLevel(); var server = level.getServer();
        var root = device.root().orElseThrow();
        if (!EscrowCargoSwitches.enabled(server, kind(), root.device())) { return status = RocketCargoStatus.ADMISSION_DISABLED; }
        var resolved = RocketCargoBindingChecks.resolve(level, device.getBlockPos(), root.owner(), root.binding());
        if (resolved.refusal() != null) { return status = resolved.refusal(); }
        var rocket = resolved.rocket(); var runtime = EscrowRuntime.of(server);
        if (selection != null && (before != root || eligible != rocket || selectionSnapshot != rocket.snapshot().orElseThrow())) {
            return status = RocketCargoStatus.STALE_BINDING;
        }
        if (runtime.stopping()) { return status = RocketCargoStatus.LEDGER_UNAVAILABLE; }
        if (!RocketCargoBindingChecks.stationary(rocket) || RocketCargoBindingChecks.occupied(rocket)) {
            return status = RocketCargoStatus.ROCKET_BUSY;
        }
        if (rocket.flightData().orElseThrow().fuel().propulsion().isEmpty() || rocket.snapshot().orElseThrow().propulsion().isEmpty()) {
            return status = RocketCargoStatus.LEGACY_ROCKET;
        }
        var data = ResourceEscrowSavedData.existing(server);
        coordinator = data == null ? null : data.bind(server);
        if (coordinator == null ? runtime.propulsion(server).state() != EscrowAdmissionAuthority.Delegate.State.NO_FILE
                : !coordinator.operational()) { return status = RocketCargoStatus.LEDGER_UNAVAILABLE; }
        if (coordinator == null && virtual == null) { virtual = EscrowLedger.empty(java.util.UUID.randomUUID()); }
        var logical = rocket.assemblyTransactionId().orElseThrow();
        if (EscrowSettlementTombstones.history(ledger(), logical)
                || ledger().rocketAuthorities().stream().anyMatch(head -> logical.equals(head.logical()))) {
            return status = RocketCargoStatus.RECEIPT_BACKED;
        }
        if (coordinator != null && coordinator.logicalLocked(logical) || runtime.cargoPreparing(logical) || !rocket.escrowSection().markers().isEmpty()) {
            device.transferBlocked();
            return status = RocketCargoStatus.OTHER_TRANSFER_PENDING;
        }
        if (!device.transferTurn()) { device.transferBlocked(); return status = RocketCargoStatus.OTHER_TRANSFER_PENDING; }
        if (!RocketEscrowGate.idle(rocket) || !RocketRuntime.resourceMutationAllowed(rocket)) {
            return status = RocketCargoStatus.TRANSFER_JOURNAL_UNPROVED;
        }
        journal = RocketTransferSavedData.get(server).acknowledgedGuard(logical).orElse(null);
        if (journal == null) { return status = RocketCargoStatus.TRANSFER_JOURNAL_UNPROVED; }
        if (kind().cargoLoad() && root.buffer().slots().stream().allMatch(CompoundTag::isEmpty)) {
            return status = RocketCargoStatus.DEVICE_EMPTY;
        }
        if (selection == null) {
            selection = new RocketItemCargoSelection.Cursor(rocket.snapshot().orElseThrow(), root.buffer(), kind().cargoLoad(),
                    candidate -> rejected.contains(candidate) ? RocketCargoSelection.Status.CARGO_TOO_LARGE : RocketCargoSelection.Status.ELIGIBLE);
        }
        before = root; eligible = rocket; selectionSnapshot = rocket.snapshot().orElseThrow();
        return null;
    }
    static RocketCargoStatus status(RocketCargoSelection.Status result) {
        return switch (result) {
            case ELIGIBLE -> RocketCargoStatus.READY;
            case CARGO_TOO_LARGE -> RocketCargoStatus.CARGO_TOO_LARGE;
            case UNSUPPORTED_DATA -> RocketCargoStatus.UNSUPPORTED_DATA;
            case NO_CARGO_STORAGE -> RocketCargoStatus.NO_CARGO_STORAGE;
            case UNRESOLVED_LOOT -> RocketCargoStatus.UNRESOLVED_LOOT;
            case INVALID_DATA -> RocketCargoStatus.INVALID_DATA;
        };
    }
    /** Run only on the owner thread; each stage reserves the shared session budget before native work. */
    public void advance() {
        if (ticket == null) { return; }
        var server = ticket.rocket().getServer();
        if (!EscrowCargoSwitches.enabled(server, kind(), before.device())) { refuse(RocketCargoStatus.ADMISSION_DISABLED); return; }
        if (!unchanged()) { refuse(RocketCargoStatus.STALE_BINDING); return; }
        try {
            if (capture == null) {
                boolean ready = ticket.advance();
                if (ticket.cursor().failure().isPresent()) {
                    if (ticket.cursor().failure().orElseThrow() == RocketCargoSelection.Status.CARGO_TOO_LARGE) { retryCandidate(); }
                    else { refuse(status(ticket.cursor().failure().orElseThrow())); }
                    return;
                }
                if (!ready) {
                    if (!ticket.valid()) { refuse(RocketCargoStatus.STALE_BINDING); }
                    return;
                }
                var op = EscrowItemCargoProjection.operation(device, before, ticket, delta, kind(), coordinator == null ? 1 : coordinator.nextSequence(), journal);
                capture = EscrowCargoPreparedCapture.nativeCapture(ticket, ledger().id(), op,
                        () -> { nativeBefore = device.saveWithFullMetadata(); return nativeBefore; },
                        () -> EscrowItemCargoProjection.after(nativeBefore, ledger().id(), op), this::nativeUnchanged, 0);
            }
            if (!capture.advance(ledger(), coordinator == null ? List.of() : coordinator.pendingAdmissions())) { return; }
            var result = capture.result();
            if (result.refusal() != null) {
                if (result.refusal() == EscrowCoordinator.Transfer.CARGO_TOO_LARGE) { retryCandidate(); }
                else { refuse(result.refusal() == EscrowCoordinator.Transfer.INVALID
                        ? status(result.status()) : EscrowCargoRefusals.status(result.refusal())); }
                return;
            }
            var operation = result.capture().operation(); var queue = EscrowRuntime.of(server).fills(server);
            queue.preparing(operation.id());
            var outcome = coordinator == null ? EscrowTransferPreparation.prepareCargo(capture, this::unchanged,
                    admission -> EscrowLedgerCreation.create(server, EscrowRuntime.of(server), virtual.id(), admission,
                            ignored -> queue.resolved(operation.id())))
                    : coordinator.prepareCargo(capture, this::unchanged, ignored -> queue.resolved(operation.id()));
            if (outcome != EscrowCoordinator.Transfer.QUEUED) { queue.resolved(operation.id()); }
            if (outcome == EscrowCoordinator.Transfer.UNAVAILABLE && capture.valid()) { return; }
            if (outcome == EscrowCoordinator.Transfer.CARGO_TOO_LARGE) { retryCandidate(); }
            else { refuse(EscrowCargoRefusals.status(outcome)); }
        } catch (EscrowTags.TooLarge | io.github.sunthemoon.advancedrocketrycommunity.rocket.cargo.RocketCargoWorkBudget.Exceeded large) { retryCandidate(); }
        catch (io.github.sunthemoon.advancedrocketrycommunity.rocket.cargo.RocketItemCargoMutation.Refusal refused) { refuse(status(refused.status())); }
        catch (IllegalArgumentException | ArithmeticException | java.util.NoSuchElementException changed) { refuse(RocketCargoStatus.STALE_BINDING); }
    }
    /** Effective eligibility can reject an inner-valid payload; continue canonical selection without losing the FIFO turn. */
    private void retryCandidate() {
        EscrowTags.require(rejected.size() < 27 * io.github.sunthemoon.advancedrocketrycommunity.rocket.RocketLimits.MAX_BLOCK_ENTITIES
                && rejected.add(delta), "Cargo selection repeated a refused payload");
        if (capture != null) { capture.discard(); } else { ticket.close(); }
        selection.retry();
        capture = null; ticket = null; nativeBefore = null; retrying = true; status = RocketCargoStatus.PREPARING;
    }
    private boolean unchanged() {
        if (!ticket.valid() || !device.live() || device.root().orElse(null) != before || device.deviceRefusal() != null) { return false; }
        var rocket = ticket.rocket(); var level = (ServerLevel) device.getLevel();
        var resolved = RocketCargoBindingChecks.resolve(level, device.getBlockPos(), before.owner(), before.binding());
        return resolved.rocket() == rocket && RocketCargoBindingChecks.stationary(rocket) && !RocketCargoBindingChecks.occupied(rocket)
                && RocketRuntime.escrowReconciliationBoundary(rocket)
                && sameLedger(level)
                && RocketTransferSavedData.get(level.getServer()).acknowledgedGuard(ticket.cursor().logical()).filter(journal::equals).isPresent();
    }
    /** The complete native comparison is invoked by prepared capture only inside its reserved work step. */
    private boolean nativeUnchanged() {
        return unchanged() && (nativeBefore == null || nativeBefore.equals(device.saveWithFullMetadata()));
    }
    private EscrowLedger ledger() { return coordinator == null ? virtual : coordinator.ledger(); }
    private boolean sameLedger(ServerLevel level) {
        var data = ResourceEscrowSavedData.existing(level.getServer());
        return data == null ? coordinator == null && EscrowRuntime.of(level.getServer()).propulsion(level.getServer()).state()
                == EscrowAdmissionAuthority.Delegate.State.NO_FILE : coordinator != null && data.bind(level.getServer()) == coordinator;
    }
    private void refuse(RocketCargoStatus refusal) { close(); status = refusal; }
    @Override public void close() {
        if (queuedDevice != null && device.getLevel() instanceof ServerLevel level && level.getServer().isSameThread()) {
            var id = queuedDevice; queuedDevice = null; EscrowRuntime.of(level.getServer()).fills(level.getServer()).cancel(id);
        }
        discard();
    }
    private void discard() {
        device.yieldTransferTurn();
        if (turnClaimed) { device.releaseTransferTurn(); turnClaimed = false; }
        if (capture != null) { capture.discard(); } else if (ticket != null) { ticket.close(); }
        queuedDevice = null;
        retrying = false; rejected.clear(); selectionSnapshot = null; selection = null;
        ticket = null; capture = null; before = null; nativeBefore = null; delta = null; eligible = null;
        coordinator = null; virtual = null; journal = null;
    }
}
