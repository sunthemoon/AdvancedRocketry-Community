package io.github.sunthemoon.advancedrocketrycommunity.resourceescrow;

import io.github.sunthemoon.advancedrocketrycommunity.rocket.entity.RocketEntity;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import net.minecraft.server.level.ServerLevel;

/** Permission to finish only a named loader fill's own work, never transfer recovery or new admission. */
public final class EscrowFillSelfGuard {
    private EscrowFillSelfGuard() { }

    public static boolean allows(RocketEntity rocket, EscrowGuard guard) {
        if (guard == null || guard.transfer() == null || !(rocket.level() instanceof ServerLevel level)
                || !level.getServer().isSameThread() || !rocket.operational()
                || rocket.assemblyTransactionId().filter(guard.rocket()::equals).isEmpty()) { return false; }
        var runtime = EscrowRuntime.of(level.getServer());
        if (runtime.stopping() || runtime.flightCheckpoint().locked(guard.rocket())) { return false; }
        var data = ResourceEscrowSavedData.existing(level.getServer());
        if (data == null) { return false; }
        var key = new EscrowEndpoint.Key(EscrowEndpoint.Kind.ENTITY, level.dimension().location().toString(),
                rocket.getUUID(), guard.rocket(), EscrowAdapterIds.ROCKET,
                rocket.chunkPosition().x, 0, rocket.chunkPosition().z, EscrowEndpoint.Unit.PRIMARY);
        return data.bind(level.getServer()).fillSelfGuard(guard, key, rocket.escrowSection());
    }

    static boolean matches(boolean operational, boolean flightLocked, EscrowLedger root, EscrowGuard guard,
                           EscrowEndpoint.Key destination, RocketEscrowSection wrapper, List<EscrowOperation> unresolved,
                           Function<EscrowOperation, EscrowReason> reason,
                           Map<EscrowEndpoint.Key, EscrowPrefix.State> frontiers) {
        if (!operational || flightLocked || guard.transfer() == null || !guard.rocket().equals(destination.logicalRocket())
                || EscrowSettlementTombstones.history(root, guard.rocket())
                || root.armedFlights().stream().anyMatch(arm -> arm.logical().equals(guard.rocket()))
                || wrapper.ledger() != null && !root.id().equals(wrapper.ledger())) { return false; }
        var members = unresolved.stream().filter(op -> guard.rocket().equals(op.guard().rocket())
                || guard.rocket().equals(op.source().logicalRocket()) || guard.rocket().equals(op.destination().logicalRocket())
                || destination.identity().equals(op.source().identity()) || destination.identity().equals(op.destination().identity())).toList();
        if (members.isEmpty() || members.stream().anyMatch(op -> !member(op, reason.apply(op), guard, destination))) { return false; }
        var unique = members.stream().collect(java.util.stream.Collectors.toMap(EscrowOperation::id, Function.identity(), (a, b) -> a));
        var chains = EscrowPrefix.chains(List.copyOf(unique.values()));
        var markers = chains.get(destination).stream().map(EscrowPrefix.Link::marker).toList();
        if (!markers.containsAll(wrapper.markers())) { return false; }
        // A frozen frontier is allowed only when it is exactly an AFTER prefix of these same unresolved fills.
        return frontiers.entrySet().stream().filter(entry -> guard.rocket().equals(entry.getKey().logicalRocket())
                        || destination.identity().equals(entry.getKey().identity()) || chains.containsKey(entry.getKey()))
                .allMatch(entry -> chains.containsKey(entry.getKey()) && EscrowPrefix.classify(root.id(),
                        chains.get(entry.getKey()), entry.getValue()) == EscrowPrefix.Classification.AFTER);
    }

    private static boolean member(EscrowOperation op, EscrowReason reason, EscrowGuard guard, EscrowEndpoint.Key destination) {
        return op.kind() == EscrowOperation.Kind.FLUID_PROPULSION_FILL && EscrowLoaderEndpoint.is(op.source())
                && EscrowAdapterIds.nativeRocket(op.destination()) && op.destination().key().equals(destination)
                && op.nativeBinding().complete() && op.nativeBinding().kind() == EscrowSignatureKind.FUEL_VECTOR_V1
                && op.settlement().isEmpty() && op.guard().rocket().equals(guard.rocket())
                && java.util.Objects.equals(op.guard().transfer(), guard.transfer())
                && op.guard().journalDigest().equals(guard.journalDigest())
                && (op.phase() == EscrowOperation.Phase.PREPARED && op.reason() == EscrowReason.NONE
                        || op.phase() == EscrowOperation.Phase.STALLED && op.reason().retryableProofFailure())
                && (reason == EscrowReason.NONE || reason.retryableProofFailure());
    }
}
