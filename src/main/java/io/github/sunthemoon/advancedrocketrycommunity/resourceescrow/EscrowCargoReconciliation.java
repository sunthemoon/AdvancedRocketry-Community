package io.github.sunthemoon.advancedrocketrycommunity.resourceescrow;

import io.github.sunthemoon.advancedrocketrycommunity.rocket.cargo.RocketCargoPreparations;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.cargo.device.RocketFluidCargoBlockEntity;
import java.util.List;
import java.util.Map;

/** C2 for one fluid cargo member. Replay owns the runtime's existing bounded reconstruction ticket. */
final class EscrowCargoReconciliation implements AutoCloseable {
    enum Step { WAITING, PUBLISHED, DONE }
    private RocketCargoPreparations.Ticket ticket;
    private EscrowCargoPublication.Preparation publication;
    private EscrowOperation active;
    private EscrowReconciliationView captured;

    static boolean supported(EscrowOperation op) {
        return op.nativeBinding().complete() && (op.kind() == EscrowOperation.Kind.FLUID_PROPULSION_FILL
                && op.nativeBinding().kind() == EscrowSignatureKind.FUEL_VECTOR_V1
                || (op.kind() == EscrowOperation.Kind.FLUID_CARGO_LOAD || op.kind() == EscrowOperation.Kind.FLUID_CARGO_UNLOAD)
                && op.nativeBinding().kind() == EscrowSignatureKind.ROCKET_COMPOSITE_V1 && EscrowFluidCargoEndpoint.is(op.device()));
    }

    Step applyNext(EscrowCoordinator coordinator, EscrowOperation op, EscrowReconciliationView view,
            Map<EscrowEndpoint.Key, List<EscrowPrefix.Link>> chains) {
        EscrowReconciliationAction.requireNativeFluid(op);
        if (ticket != null && (!ticket.valid() || active == op && captured != null && captured != view)) { close(); }
        // Only our own device publication permits a fresh component capture without another reconstruction.
        if (ticket != null && active == op && captured == null) { captured = view; }
        try {
            var step = applyNext(op, endpoint -> view.ends.get(endpoint.key()).state().markers().size() == chains.get(endpoint.key()).size(),
                    endpoint -> coordinator.frozen(endpoint.key()), () -> prepare(coordinator, op, view),
                    endpoint -> publish(coordinator, op, view, endpoint));
            if (step == Step.DONE && active == op) { close(); }
            return step;
        } catch (IllegalArgumentException | IllegalStateException | java.util.NoSuchElementException invalid) {
            if (active == op) { close(); }
            throw new EscrowReconciliationView.Refusal(EscrowReason.OTHER_STATE);
        }
    }

    /** C2 ordering shared by native execution and driver tests; adapters supply capture, replay and publication. */
    static Step applyNext(EscrowOperation op, java.util.function.Predicate<EscrowEndpoint> after,
            java.util.function.Predicate<EscrowEndpoint> frozen, java.util.function.BooleanSupplier replayReady,
            java.util.function.Consumer<EscrowEndpoint> publish) {
        if (!after.test(op.rocket()) && (frozen.test(op.rocket()) || !replayReady.getAsBoolean())) { return Step.WAITING; }
        for (var endpoint : List.of(op.source(), op.destination())) {
            if (after.test(endpoint)) { continue; }
            if (frozen.test(endpoint)) { return Step.WAITING; }
            publish.accept(endpoint);
            return Step.PUBLISHED;
        }
        return Step.DONE;
    }

    private boolean prepare(EscrowCoordinator coordinator, EscrowOperation op, EscrowReconciliationView view) {
        var end = view.ends.get(op.rocket().key()); var runtime = EscrowRuntime.of(end.level().getServer());
        if (ticket != null && active != op) { return false; }
        if (ticket == null) {
            ticket = runtime.cargoPreparations().beginReplay(end.rocket(), EscrowCargoPayload.decode(op.payload()), op.kind().cargoLoad()).orElse(null);
            if (ticket == null) { return false; }
            active = op; captured = view;
        }
        if (!ticket.advance()) { return false; }
        if (ticket.cursor().failure().isPresent()) { throw new EscrowReconciliationView.Refusal(EscrowReason.OTHER_STATE); }
        if (publication == null) {
            publication = new EscrowCargoPublication.Preparation(view.root, op, ticket,
                    () -> captured != null && captured.unchanged(coordinator)
                            && !coordinator.frozen(op.rocket().key()) && !coordinator.currentReason(op).manual());
        }
        return publication.verify();
    }

    private void publish(EscrowCoordinator coordinator, EscrowOperation op, EscrowReconciliationView view, EscrowEndpoint endpoint) {
        var end = view.ends.get(endpoint.key()); var server = end.level().getServer(); var runtime = EscrowRuntime.of(server);
        java.util.function.BooleanSupplier authority = () -> view.unchanged(coordinator)
                && !coordinator.frozen(endpoint.key()) && !coordinator.currentReason(op).manual();
        String label = endpoint.key().equals(op.destination().key()) ? "C2_DESTINATION_" : "C2_SOURCE_";
        if (end.rocket() == null) {
            var permit = EscrowFluidCargoPublication.issue(view.root, op, end.envelope(), server.getTickCount(), authority);
            runtime.checkpoint(label + "BEFORE");
            ((RocketFluidCargoBlockEntity) end.source()).applyEscrowCargo(permit);
            if (active == op) { captured = null; }
        } else {
            var permit = publication.issue();
            if (permit == null) { throw new IllegalStateException("Replay verification is incomplete"); }
            runtime.checkpoint(label + "BEFORE"); end.rocket().applyEscrowCargo(permit); close();
        }
        runtime.monitor().observed(endpoint.level(), end.rocket() != null, end.rocket() != null
                ? new net.minecraft.world.level.ChunkPos(endpoint.x(), endpoint.z())
                : new net.minecraft.world.level.ChunkPos(EscrowFluidCargoEndpoint.position(endpoint)));
        runtime.checkpoint(label + "AFTER");
    }

    @Override public void close() {
        if (ticket != null) { ticket.close(); }
        ticket = null; publication = null; active = null; captured = null;
    }
}
