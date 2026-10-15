package io.github.sunthemoon.advancedrocketrycommunity.resourceescrow;

import io.github.sunthemoon.advancedrocketrycommunity.rocket.entity.RocketEntity;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.persistence.RocketTransferSavedData;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;
import net.minecraft.server.level.ServerLevel;

/** F1 only: source-own store, same-worker force and readback before the checked ARMED write. */
final class EscrowFlightCheckpoint implements AutoCloseable {
    private final Map<UUID, EscrowFlightSource> pending = new HashMap<>();
    private boolean closed;

    boolean locked(UUID logical) { return pending.containsKey(logical); }
    void invalidated(RocketEntity rocket) {
        var frozen = pending.get(rocket.assemblyTransactionId().orElse(null));
        if (frozen != null) { frozen.unchanged.set(false); }
    }
    void observe(RocketEntity rocket) {
        var frozen = pending.get(rocket.assemblyTransactionId().orElse(null));
        if (frozen != null) { frozen.observe(); }
    }

    /** Package-private; the successor dispatcher must supply its validated, fully reserved launch tuple. */
    boolean begin(RocketEntity rocket, EscrowFlightAuthority.Launch launch, Consumer<EscrowFlightTables.Armed> completion) {
        return begin(rocket, launch, EscrowFlightAuthority::arm, () -> true, completion);
    }

    /** Complete F1 adapter: reserve the exact F2 record as well as its ARMED root before own-store. */
    boolean begin(RocketEntity rocket, EscrowGovernedLaunch prepared,
                  Consumer<io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketTransferRecord> completion) {
        return begin(rocket, prepared, record -> true, completion);
    }

    boolean begin(RocketEntity rocket, EscrowGovernedLaunch prepared,
                  java.util.function.Predicate<io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketTransferRecord> boundary,
                  Consumer<io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketTransferRecord> completion) {
        if (!(rocket.level() instanceof ServerLevel level) || !level.getServer().isSameThread()) { return false; }
        var journal = RocketTransferSavedData.get(level.getServer());
        var captured = new java.util.concurrent.atomic.AtomicReference<io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketTransferRecord>();
        return begin(rocket, prepared.launch(), (root, launch, generation) -> {
            var candidate = prepared.preflight(root, generation);
            var arm = candidate.armedFlights().stream().filter(value -> value.logical().equals(launch.logical())).findFirst().orElseThrow();
            var record = prepared.record(arm);
            journal.validateNewRecord(record);
            EscrowTags.require(boundary.test(record), "F1 launch lost its action boundary");
            captured.set(record);
            return candidate;
        }, () -> captured.get() != null && boundary.test(captured.get()), arm -> {
            io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketTransferRecord record = null;
            if (arm != null) {
                var data = ResourceEscrowSavedData.existing(level.getServer());
                try {
                    if (data != null && data.bind(level.getServer()).governedAvailable(arm.logical())
                            && boundary.test(captured.get())) {
                        record = prepared.afterArm(data.ledger(), arm, EscrowNativeEnvelope.rocket(rocket), journal);
                    }
                } catch (IllegalArgumentException refused) {
                    // ARMED remains durable and locked; only its checked cancellation/recovery may retire it.
                }
            }
            completion.accept(record);
        });
    }

    @FunctionalInterface private interface Preparation {
        EscrowLedger capture(EscrowLedger root, EscrowFlightAuthority.Launch launch, long generation);
    }

    private boolean begin(RocketEntity rocket, EscrowFlightAuthority.Launch launch,
                          Preparation preparation, java.util.function.BooleanSupplier boundary,
                          Consumer<EscrowFlightTables.Armed> completion) {
        if (closed || pending.size() >= 64 || locked(launch.logical()) || !RocketEscrowGate.idle(rocket)
                || !(rocket.level() instanceof ServerLevel level)) { return false; }
        var server = level.getServer(); var data = ResourceEscrowSavedData.existing(server);
        if (data == null) { return false; }
        var coordinator = data.bind(server); var journal = RocketTransferSavedData.get(server);
        if (!coordinator.operational() || !journal.retryChecked(server)
                || !io.github.sunthemoon.advancedrocketrycommunity.rocket.server.RocketRuntime.resourceMutationAllowed(rocket)) { return false; }
        EscrowFlightSource frozen;
        try {
            frozen = new EscrowFlightSource(rocket, boundary);
            EscrowTags.require(launch.source().equals(rocket.getUUID()) && launch.logical().equals(frozen.endpoint.logicalRocket())
                    && launch.owner().equals(rocket.ownerId().orElseThrow()) && launch.wrapper().equals(rocket.escrowSection())
                    && launch.signature().equals(frozen.signature) && launch.invariant().equals(frozen.invariant)
                    && launch.sourceEnvelope().equals(frozen.digest)
                    && launch.committedDebits().equals(java.util.Set.copyOf(rocket.flightData().orElseThrow().fuel().committedDebits()))
                    && coordinator.admissionAuthority().pendingFor(launch.logical()) == 0
                    && RocketEscrowAudit.journal(launch.journal(), journal.acknowledgedGuard(launch.logical()).orElse(null)) == EscrowReason.NONE,
                    "F1 source or journal binding changed");
            EscrowSettlementCapacity.preflight(preparation.capture(coordinator.ledger(), launch,
                    Math.addExact(coordinator.ledger().writerGeneration(), 1)));
        } catch (IllegalArgumentException | ArithmeticException refused) { return false; }
        var noTransfer = EscrowFlightJournal.noTransfer(journal, launch.logical(), launch.transfer(),
                Math.addExact(coordinator.ledger().issuedLaunchSerial(), 1));
        if (!noTransfer.getAsBoolean() || !frozen.observe()) { return false; }
        var runtime = EscrowRuntime.of(server); pending.put(launch.logical(), frozen);
        runtime.checkpoint("F1_STORE_BEFORE");
        boolean started = runtime.barrier().begin(runtime.store(level), frozen.endpoint, frozen.frontier, frozen.validator(),
                server.getTickCount(), result -> {
                    if (closed || !result.confirmed() || !frozen.observe() || !noTransfer.getAsBoolean()) {
                        if (result.proof() != null) { result.proof().release(); }
                        pending.remove(launch.logical(), frozen); completion.accept(null); return;
                    }
                    runtime.checkpoint("F1_READBACK_AFTER"); var proof = result.proof();
                    boolean queued = coordinator.flightWrite(launch.logical(), (root, generation) -> {
                        EscrowTags.require(frozen.observe(), "F1 source changed before ARMED capture");
                        runtime.checkpoint("F1_ARM_CAPTURE");
                        return preparation.capture(root, launch, generation);
                    }, () -> proof.valid() && frozen.unchanged.get() && noTransfer.getAsBoolean(), committed -> {
                        proof.release(); pending.remove(launch.logical(), frozen);
                        if (committed) {
                            runtime.checkpoint("F1_ARM_ACK");
                            completion.accept(coordinator.ledger().armedFlights().stream()
                                    .filter(arm -> arm.logical().equals(launch.logical()) && arm.transfer().equals(launch.transfer())).findFirst().orElseThrow());
                        } else { completion.accept(null); }
                    });
                    if (!queued) { proof.release(); pending.remove(launch.logical(), frozen); completion.accept(null); }
                });
        if (!started) { pending.remove(launch.logical(), frozen); }
        return started;
    }

    @Override public void close() { closed = true; pending.values().forEach(value -> value.unchanged.set(false)); pending.clear(); }
}
