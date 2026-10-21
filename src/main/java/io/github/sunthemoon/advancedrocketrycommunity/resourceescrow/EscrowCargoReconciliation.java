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
        if (ticket != null && (!ticket.valid() || active == op && captured != view)) { close(); }
        for (var endpoint : List.of(op.source(), op.destination())) {
            var end = view.ends.get(endpoint.key()); var chain = chains.get(endpoint.key());
            if (end.state().markers().size() == chain.size()) { continue; }
            if (coordinator.frozen(endpoint.key())) { return Step.WAITING; }
            try {
                var server = end.level().getServer(); var runtime = EscrowRuntime.of(server);
                java.util.function.BooleanSupplier authority = () -> view.unchanged(coordinator)
                        && !coordinator.frozen(endpoint.key()) && !coordinator.currentReason(op).manual();
                boolean destination = endpoint.key().equals(op.destination().key());
                String label = destination ? "C2_DESTINATION_" : "C2_SOURCE_";
                if (end.rocket() == null) {
                    var permit = EscrowFluidCargoPublication.issue(view.root, op, end.envelope(), server.getTickCount(), authority);
                    runtime.checkpoint(label + "BEFORE");
                    ((RocketFluidCargoBlockEntity) end.source()).applyEscrowCargo(permit);
                } else {
                    if (ticket != null && active != op) { return Step.WAITING; }
                    if (ticket == null) {
                        ticket = runtime.cargoPreparations().beginReplay(end.rocket(), EscrowCargoPayload.decode(op.payload()),
                                op.kind().cargoLoad()).orElse(null);
                        if (ticket == null) { return Step.WAITING; }
                        active = op; captured = view;
                    }
                    if (!ticket.advance()) { return Step.WAITING; }
                    if (ticket.cursor().failure().isPresent()) { throw new EscrowReconciliationView.Refusal(EscrowReason.OTHER_STATE); }
                    if (publication == null) { publication = new EscrowCargoPublication.Preparation(view.root, op, ticket, authority); }
                    var permit = publication.issue();
                    if (permit == null) { return Step.WAITING; }
                    runtime.checkpoint(label + "BEFORE"); end.rocket().applyEscrowCargo(permit); close();
                }
                runtime.monitor().observed(endpoint.level(), end.rocket() != null, end.rocket() != null
                        ? new net.minecraft.world.level.ChunkPos(endpoint.x(), endpoint.z())
                        : new net.minecraft.world.level.ChunkPos(EscrowFluidCargoEndpoint.position(endpoint)));
                runtime.checkpoint(label + "AFTER");
                return Step.PUBLISHED;
            } catch (IllegalArgumentException | IllegalStateException | java.util.NoSuchElementException invalid) {
                if (active == op) { close(); }
                throw new EscrowReconciliationView.Refusal(EscrowReason.OTHER_STATE);
            }
        }
        if (active == op) { close(); }
        return Step.DONE;
    }

    @Override public void close() {
        if (ticket != null) { ticket.close(); }
        ticket = null; publication = null; active = null; captured = null;
    }
}
