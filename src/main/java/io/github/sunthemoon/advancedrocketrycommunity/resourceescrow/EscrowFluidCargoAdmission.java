package io.github.sunthemoon.advancedrocketrycommunity.resourceescrow;

import io.github.sunthemoon.advancedrocketrycommunity.config.CommonConfig;
import io.github.sunthemoon.advancedrocketrycommunity.machine.tank.TankCapacity;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.cargo.RocketCargoPreparations;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.cargo.RocketCargoSelection;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.cargo.device.*;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.persistence.RocketTransferSavedData;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.server.RocketRuntime;
import java.util.List;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraftforge.fluids.FluidStack;

/** Owner-thread, per-device admission continuation. It never publishes resources or acknowledges its own write. */
public final class EscrowFluidCargoAdmission implements AutoCloseable {
    private final RocketFluidCargoBlockEntity device;
    private RocketCargoPreparations.Ticket ticket;
    private EscrowCargoPreparedCapture capture;
    private RocketFluidCargoRoot before;
    private CompoundTag nativeBefore;
    private EscrowCargoPayload delta;
    private EscrowCoordinator coordinator;
    private EscrowLedger virtual;
    private RocketTransferSavedData.JournalGuard journal;
    private int capacity;
    private RocketCargoStatus status = RocketCargoStatus.ADMISSION_DISABLED;
    private java.util.UUID queuedDevice;
    public EscrowFluidCargoAdmission(RocketFluidCargoBlockEntity device) { this.device = device; }
    public EscrowOperation.Kind kind() {
        return device.role() == RocketCargoRole.LOAD ? EscrowOperation.Kind.FLUID_CARGO_LOAD : EscrowOperation.Kind.FLUID_CARGO_UNLOAD;
    }
    public RocketCargoStatus status() { return status; }
    public boolean active() { return ticket != null; }
    public RocketCargoStatus attempt() {
        if (!device.live()) { return status = RocketCargoStatus.WAITING_UNLOADED; }
        var root = device.root().orElse(null); var server = ((ServerLevel) device.getLevel()).getServer();
        var refusal = device.deviceRefusal();
        if (refusal != null) { return status = refusal; }
        if (!EscrowCargoSwitches.enabled(server, kind(), root.device())) { return status = RocketCargoStatus.ADMISSION_DISABLED; }
        if (queuedDevice != null) { return status; }
        if (!EscrowRuntime.of(server).fills(server).offerCargo(root.device(), this::step, this::discard)) {
            return status = RocketCargoStatus.ESCROW_FULL;
        }
        queuedDevice = root.device(); return status = RocketCargoStatus.READY;
    }
    private boolean step() {
        if (ticket == null) { begin(); } else { advance(); }
        return ticket == null;
    }
    private RocketCargoStatus begin() {
        if (active()) { return status; }
        var refusal = device.deviceRefusal();
        if (refusal != null) { return status = refusal; }
        var level = (ServerLevel) device.getLevel(); var server = level.getServer();
        var root = device.root().orElseThrow();
        if (!EscrowCargoSwitches.enabled(server, kind(), root.device())) { return status = RocketCargoStatus.ADMISSION_DISABLED; }
        var resolved = RocketCargoBindingChecks.resolve(level, device.getBlockPos(), root);
        if (resolved.refusal() != null) { return status = resolved.refusal(); }
        var rocket = resolved.rocket(); var runtime = EscrowRuntime.of(server);
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
        virtual = coordinator == null ? EscrowLedger.empty(java.util.UUID.randomUUID()) : null;
        var logical = rocket.assemblyTransactionId().orElseThrow();
        if (EscrowSettlementTombstones.history(ledger(), logical)
                || ledger().rocketAuthorities().stream().anyMatch(head -> logical.equals(head.logical()))) {
            return status = RocketCargoStatus.RECEIPT_BACKED;
        }
        if (coordinator != null && coordinator.logicalLocked(logical) || runtime.cargoPreparing(logical) || !rocket.escrowSection().markers().isEmpty()) {
            return status = RocketCargoStatus.OTHER_TRANSFER_PENDING;
        }
        if (!RocketEscrowGate.idle(rocket) || !RocketRuntime.resourceMutationAllowed(rocket)) {
            return status = RocketCargoStatus.TRANSFER_JOURNAL_UNPROVED;
        }
        journal = RocketTransferSavedData.get(server).acknowledgedGuard(logical).orElse(null);
        if (journal == null) { return status = RocketCargoStatus.TRANSFER_JOURNAL_UNPROVED; }
        if (kind().cargoLoad() && root.tank().amount() == 0) { return status = RocketCargoStatus.DEVICE_EMPTY; }
        if (!kind().cargoLoad() && root.tank().amount() == 16_000) { return status = RocketCargoStatus.DEVICE_FULL; }
        capacity = TankCapacity.fromMultiplier(CommonConfig.tankCapacityMultiplier());
        var selected = RocketCargoSelection.select(rocket.snapshot().orElseThrow(), root.tank().amount() == 0
                ? FluidStack.EMPTY : FluidStack.loadFluidStackFromNBT(root.tank().encode()), kind().cargoLoad(), capacity,
                ignored -> RocketCargoSelection.Status.ELIGIBLE);
        if (selected.status() != RocketCargoSelection.Status.ELIGIBLE) {
            return status = switch (selected.status()) {
                case CARGO_TOO_LARGE -> RocketCargoStatus.CARGO_TOO_LARGE;
                case UNSUPPORTED_DATA -> RocketCargoStatus.UNSUPPORTED_DATA;
                case NO_CARGO_STORAGE -> RocketCargoStatus.NO_CARGO_STORAGE;
                default -> RocketCargoStatus.INVALID_DATA;
            };
        }
        delta = selected.delta(); before = root;
        ticket = runtime.cargoPreparations().begin(rocket, delta, kind().cargoLoad()).orElse(null);
        return status = ticket == null ? RocketCargoStatus.OTHER_TRANSFER_PENDING : RocketCargoStatus.PREPARING;
    }
    /** Run only on the owner thread; each stage reserves the shared session budget before native work. */
    public void advance() {
        if (ticket == null) { return; }
        var server = ticket.rocket().getServer();
        if (!EscrowCargoSwitches.enabled(server, kind(), before.device())) { refuse(RocketCargoStatus.ADMISSION_DISABLED); return; }
        if (!unchanged()) { refuse(RocketCargoStatus.STALE_BINDING); return; }
        try {
            if (capture == null) {
                if (!ticket.advance()) { return; }
                if (ticket.cursor().failure().isPresent()) {
                    refuse(ticket.cursor().failure().orElseThrow() == RocketCargoSelection.Status.CARGO_TOO_LARGE
                            ? RocketCargoStatus.CARGO_TOO_LARGE : RocketCargoStatus.INVALID_DATA); return;
                }
                var op = EscrowFluidCargoProjection.operation(device, before, ticket, delta, kind(), coordinator == null ? 1 : coordinator.nextSequence(), journal);
                capture = EscrowCargoPreparedCapture.nativeCapture(ticket, ledger().id(), op,
                        () -> { nativeBefore = device.saveWithFullMetadata(); return nativeBefore; },
                        () -> EscrowFluidCargoProjection.after(nativeBefore, ledger().id(), op), this::unchanged, capacity);
            }
            if (!capture.advance(ledger(), coordinator == null ? List.of() : coordinator.pendingAdmissions())) { return; }
            var result = capture.result();
            if (result.refusal() != null) { refuse(EscrowCargoRefusals.status(result.refusal())); return; }
            var operation = result.capture().operation(); var queue = EscrowRuntime.of(server).fills(server);
            queue.preparing(operation.id());
            var outcome = coordinator == null ? EscrowTransferPreparation.prepareCargo(capture, this::unchanged,
                    admission -> EscrowLedgerCreation.create(server, EscrowRuntime.of(server), virtual.id(), admission,
                            ignored -> queue.resolved(operation.id())))
                    : coordinator.prepareCargo(capture, this::unchanged, ignored -> queue.resolved(operation.id()));
            if (outcome != EscrowCoordinator.Transfer.QUEUED) { queue.resolved(operation.id()); }
            if (outcome == EscrowCoordinator.Transfer.UNAVAILABLE && capture.valid()) { return; }
            refuse(EscrowCargoRefusals.status(outcome));
        } catch (EscrowTags.TooLarge large) { refuse(RocketCargoStatus.CARGO_TOO_LARGE); }
        catch (IllegalArgumentException | ArithmeticException | java.util.NoSuchElementException changed) { refuse(RocketCargoStatus.STALE_BINDING); }
    }
    private boolean unchanged() {
        if (!ticket.valid() || !device.live() || device.root().orElse(null) != before || device.deviceRefusal() != null
                || capacity != TankCapacity.fromMultiplier(CommonConfig.tankCapacityMultiplier())) { return false; }
        var rocket = ticket.rocket(); var level = (ServerLevel) device.getLevel();
        var resolved = RocketCargoBindingChecks.resolve(level, device.getBlockPos(), before);
        return resolved.rocket() == rocket && RocketCargoBindingChecks.stationary(rocket) && !RocketCargoBindingChecks.occupied(rocket)
                && RocketRuntime.escrowReconciliationBoundary(rocket)
                && sameLedger(level)
                && RocketTransferSavedData.get(level.getServer()).acknowledgedGuard(ticket.cursor().logical()).filter(journal::equals).isPresent()
                && (nativeBefore == null || nativeBefore.equals(device.saveWithFullMetadata()));
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
        if (capture != null) { capture.discard(); } else if (ticket != null) { ticket.close(); }
        queuedDevice = null;
        ticket = null; capture = null; before = null; nativeBefore = null; delta = null; coordinator = null; virtual = null; journal = null;
    }
}
