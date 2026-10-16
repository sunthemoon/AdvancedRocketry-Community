package io.github.sunthemoon.advancedrocketrycommunity.resourceescrow;

import io.github.sunthemoon.advancedrocketrycommunity.rocket.entity.RocketEntity;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.persistence.RocketTransferSavedData;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;

/** Loaded-only audit and the server-owned ordinary reconciliation entry point. */
public final class RocketEscrowAudit {
    public record Placement(UUID identity, UUID logical, String type, String level, int chunkX, int chunkZ,
                            double x, double y, double z, double expectedX, double expectedY, double expectedZ,
                            String flightLevel, boolean occupied) { }
    private RocketEscrowAudit() { }

    public static EscrowReason placement(EscrowEndpoint endpoint, Placement observed) {
        if (!endpoint.identity().equals(observed.identity()) || !endpoint.logicalRocket().equals(observed.logical())
                || !endpoint.type().equals(observed.type())) { return EscrowReason.INVALID_ENDPOINT; }
        if (!endpoint.level().equals(observed.level()) || !endpoint.level().equals(observed.flightLevel())
                || endpoint.x() != observed.chunkX() || endpoint.z() != observed.chunkZ()
                || Double.compare(observed.x(), observed.expectedX()) != 0
                || Double.compare(observed.y(), observed.expectedY()) != 0
                || Double.compare(observed.z(), observed.expectedZ()) != 0
                || observed.occupied()) { return EscrowReason.RELOCATED_ENDPOINT; }
        return EscrowReason.NONE;
    }

    /** Sequence is session-local; unrelated journal records must not invalidate a NONE guard. */
    public static EscrowReason journal(EscrowGuard frozen, RocketTransferSavedData.JournalGuard current) {
        if (current == null) { return EscrowReason.TRANSFER_JOURNAL_UNPROVED; }
        if (!frozen.rocket().equals(current.logical()) || !Objects.equals(frozen.transfer(), current.transfer())
                || frozen.transfer() != null && !frozen.journalDigest().equals(current.recordDigest())) {
            return EscrowReason.JOURNAL_GUARD_MISMATCH;
        }
        return EscrowReason.NONE;
    }

    static EscrowReason placement(EscrowEndpoint endpoint, RocketEntity rocket) {
        if (!rocket.operational()) { return EscrowReason.INVALID_ENDPOINT; }
        var flight = rocket.flightData().orElseThrow(); var origin = flight.currentOrigin();
        return placement(endpoint, new Placement(rocket.getUUID(), rocket.assemblyTransactionId().orElseThrow(),
                BuiltInRegistries.ENTITY_TYPE.getKey(rocket.getType()).toString(), rocket.level().dimension().location().toString(),
                rocket.chunkPosition().x, rocket.chunkPosition().z, rocket.getX(), rocket.getY(), rocket.getZ(),
                origin.x() + .5D, origin.y(), origin.z() + .5D, flight.currentDimension().toString(),
                rocket.isPassenger() || !rocket.getPassengers().isEmpty() || !flight.passengers().assignments().isEmpty()));
    }

    /** Called at tracking, tick, live load and serialization boundaries, before any endpoint proof. */
    public static void observe(RocketEntity rocket) {
        if (!(rocket.level() instanceof ServerLevel level) || !level.getServer().isSameThread()) { return; }
        EscrowRuntime.of(level.getServer()).observeFlight(rocket);
        var data = ResourceEscrowSavedData.existing(level.getServer());
        if (data == null || rocket.assemblyTransactionId().isEmpty()) { return; }
        var coordinator = data.bind(level.getServer());
        for (var op : data.ledger().records()) {
            if (!op.destination().identity().equals(rocket.getUUID())
                    && !op.guard().rocket().equals(rocket.assemblyTransactionId().orElseThrow())) { continue; }
            var reason = placement(op.destination(), rocket);
            if (reason == EscrowReason.NONE) { continue; }
            invalidate(level, op.destination(), rocket);
            // Manual choices and settlements are sticky. Never replace one with an automatic diagnosis.
            if (!coordinator.currentReason(op).manual() && op.phase() != EscrowOperation.Phase.SETTLING_ACCEPT
                    && op.phase() != EscrowOperation.Phase.SETTLING_ABANDON) { coordinator.transition(op.id(), reason); }
        }
        coordinator.persistTransitions();
    }

    private static void invalidate(ServerLevel level, EscrowEndpoint endpoint, RocketEntity rocket) {
        var monitor = EscrowRuntime.of(level.getServer()).monitor();
        monitor.observed(endpoint.level(), true, new ChunkPos(endpoint.x(), endpoint.z()));
        monitor.observed(level.dimension().location().toString(), true, rocket.chunkPosition());
    }

    /** Only the coordinator's exact C7 acknowledgement may report scheduler success. */
    static void attempt(EscrowCoordinator coordinator, net.minecraft.server.MinecraftServer server, EscrowOperation op,
                        List<EscrowOperation> records, java.util.function.Consumer<EscrowReconciliationScheduler.Result> completion) {
        var journal = RocketTransferSavedData.get(server);
        if (!journal.retryChecked(server)) {
            completion.accept(new EscrowReconciliationScheduler.Result(EscrowReason.TRANSFER_JOURNAL_UNPROVED, false)); return;
        }
        var reason = journal(op.guard(), journal.acknowledgedGuard(op.guard().rocket()).orElse(null));
        if (reason != EscrowReason.NONE) { completion.accept(new EscrowReconciliationScheduler.Result(reason, false)); return; }
        ServerLevel level = server.getLevel(net.minecraft.resources.ResourceKey.create(net.minecraft.core.registries.Registries.DIMENSION,
                net.minecraft.resources.ResourceLocation.tryParse(op.destination().level())));
        if (level != null && level.getEntity(op.destination().identity()) instanceof RocketEntity rocket) {
            reason = placement(op.destination(), rocket);
            if (reason != EscrowReason.NONE) {
                invalidate(level, op.destination(), rocket);
                completion.accept(new EscrowReconciliationScheduler.Result(reason, false)); return;
            }
        }
        coordinator.reconcile(server, op, completion);
    }
}
