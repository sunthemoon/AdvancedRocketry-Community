package io.github.sunthemoon.advancedrocketrycommunity.resourceescrow;

import io.github.sunthemoon.advancedrocketrycommunity.rocket.entity.RocketEntity;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.persistence.RocketTransferSavedData;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.transaction.RocketRegion;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;
import net.minecraft.server.level.ServerLevel;

/** F1 only: source-own store, same-worker force and readback before the checked ARMED write. */
final class EscrowFlightCheckpoint implements AutoCloseable {
    private final Map<UUID, EscrowFlightSource> pending = new HashMap<>();
    // Same lifetime and 64-launch bound as pending, retained through the synchronous F2 publication callback.
    private final Map<UUID, RocketRegion> destinations = new HashMap<>();
    private record Retry(RocketEntity rocket, EscrowSuccessorRetry schedule) { }
    private final Map<UUID, Retry> retries = new HashMap<>();
    private boolean closed;

    boolean locked(UUID logical) { return pending.containsKey(logical); }
    /** W90b: begin's own capacity and lock test, read-only, so the warm-up's preconditions cannot drift from it. */
    boolean admits(UUID logical) { return !closed && pending.size() < 64 && !locked(logical); }
    List<RocketRegion> pendingDestinations() { return List.copyOf(destinations.values()); }
    boolean retryReady(UUID logical, long tick) {
        var retry = retries.get(logical);
        return !closed && (retry == null || retry.schedule().ready(tick));
    }
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
        return begin(rocket, launch, EscrowFlightAuthority::arm, () -> true, () -> null, completion);
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
        EscrowConsumerTimingProbe.mark(level.getServer(), "f1.attempt");
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
        }, () -> captured.get() != null && boundary.test(captured.get()),
                () -> RocketRegion.fromSnapshot(captured.get().destinationSnapshot()), arm -> {
            io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketTransferRecord record = null;
            if (arm != null) {
                var data = ResourceEscrowSavedData.existing(level.getServer());
                net.minecraft.nbt.CompoundTag observed = null;
                try {
                    boolean available = data != null && data.bind(level.getServer()).governedAvailable(arm.logical());
                    if (available && boundary.test(captured.get())) {
                        observed = EscrowGovernedEnvelope.capture(rocket);
                        record = prepared.afterArm(data.ledger(), arm, observed, journal);
                    } else {
                        EscrowLaunchDiscard.note("F1_AFTER_ARM", () -> "logical=" + arm.logical() + " transfer=" + arm.transfer()
                                + " reason=" + (data == null ? "NO_LEDGER" : !available ? "LEDGER_UNAVAILABLE" : "ACTION_BOUNDARY"));
                    }
                } catch (IllegalArgumentException refused) {
                    // ARMED remains durable and locked; only its checked cancellation/recovery may retire it.
                    var lastObserved = observed;
                    EscrowLaunchDiscard.note("F1_AFTER_ARM", () -> "logical=" + arm.logical() + " transfer=" + arm.transfer()
                            + " reason=" + refused.getClass().getSimpleName() + ": " + refused.getMessage() + " fields="
                            + EscrowLaunchDiscard.detail(() -> EscrowLaunchDiscard.differences(
                                    captured.get().nativeTransfer().orElseThrow().sourceEnvelope(captured.get()), lastObserved)));
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
                          java.util.function.Supplier<RocketRegion> destination,
                          Consumer<EscrowFlightTables.Armed> completion) {
        if (!admits(launch.logical()) || !RocketEscrowGate.idle(rocket)
                || !(rocket.level() instanceof ServerLevel level)) {
            if (rocket.level() instanceof ServerLevel observedLevel) {
                EscrowConsumerTimingProbe.mark(observedLevel.getServer(), "f1.refused.CLOSED_CAPACITY_LOCK_OR_SOURCE");
            }
            return false;
        }
        var server = level.getServer(); var data = ResourceEscrowSavedData.existing(server);
        var observation = EscrowRuntime.of(server).timings();
        if (data == null) { observation.sample("f1.refused.NO_LEDGER", 0); return false; }
        retries.entrySet().removeIf(entry -> entry.getValue().rocket().isRemoved() && !pending.containsKey(entry.getKey()));
        if (!retryReady(launch.logical(), server.getTickCount())
                || !retries.containsKey(launch.logical()) && retries.size() >= 64) { observation.sample("f1.refused.RETRY_WAIT_OR_CAPACITY", 0); return false; }
        var coordinator = data.bind(server); var journal = RocketTransferSavedData.get(server);
        // R17 item 3: L's checked state, not a quiet journal; the retry still pushes any pending journal write.
        if (!coordinator.operational() || !journal.retryChecked(server) && !journal.actionAuthorityAvailable(launch.logical())
                || !io.github.sunthemoon.advancedrocketrycommunity.rocket.server.RocketRuntime.resourceMutationAllowed(rocket)) { observation.sample("f1.refused.COORDINATOR_JOURNAL_OR_RESOURCE_AUTHORITY", 0); return false; }
        if (!EscrowRuntime.of(server).barrier().available(level.dimension().location().toString(), true, server.getTickCount())) {
            observation.sample("f1.refused.BARRIER_BUDGET", 0); return false;
        }
        EscrowFlightSource frozen;
        try {
            frozen = new EscrowFlightSource(rocket, boundary);
            EscrowTags.require(launch.source().equals(rocket.getUUID()) && launch.logical().equals(frozen.endpoint.logicalRocket())
                    && launch.owner().equals(rocket.ownerId().orElseThrow()) && launch.wrapper().equals(rocket.escrowSection())
                    && launch.signature().equals(frozen.signature) && launch.invariant().equals(frozen.invariant)
                    && launch.sourceEnvelope().equals(frozen.digest)
                    && launch.committedDebits().equals(java.util.Set.copyOf(rocket.flightData().orElseThrow().fuel().committedDebits()))
                    && coordinator.admissionAuthority().pendingFor(launch.logical()) == 0
                    && RocketEscrowAudit.journal(launch.journal(), journal.logicalGuard(launch.logical()).orElse(null)) == EscrowReason.NONE,
                    "F1 source or journal binding changed");
            EscrowSettlementCapacity.preflight(preparation.capture(coordinator.ledger(), launch,
                    Math.addExact(coordinator.ledger().writerGeneration(), 1)));
        } catch (IllegalArgumentException | ArithmeticException refused) {
            EscrowLaunchDiscard.note("F1_PREFLIGHT", () -> "logical=" + launch.logical() + " transfer=" + launch.transfer()
                    + " reason=" + refused.getClass().getSimpleName() + ": " + refused.getMessage());
            observation.sample("f1.refused.BINDING_OR_PREFLIGHT", 0); return false;
        }
        var noTransfer = EscrowFlightJournal.noTransfer(journal, launch.logical(), launch.transfer(),
                Math.addExact(coordinator.ledger().issuedLaunchSerial(), 1));
        if (!noTransfer.getAsBoolean() || !frozen.observe()) { observation.sample("f1.refused.SOURCE_OR_JOURNAL_CHANGED", 0); return false; }
        var retry = retries.computeIfAbsent(launch.logical(), key -> new Retry(rocket, new EscrowSuccessorRetry())).schedule();
        // Zero means not yet assigned by the actual coalesced writer capture, not a reserved serial.
        var serial = new java.util.concurrent.atomic.AtomicLong();
        var runtime = EscrowRuntime.of(server); pending.put(launch.logical(), frozen);
        var region = destination.get();
        if (region != null) { destinations.put(launch.logical(), region); }
        Consumer<EscrowFlightTables.Armed> finished = arm -> {
            try { completion.accept(arm); }
            finally { destinations.remove(launch.logical(), region); }
        };
        runtime.checkpoint("F1_STORE_BEFORE");
        long barrierBegan = observation == EscrowTimingSink.NONE ? 0 : System.nanoTime();
        boolean started = runtime.barrier().begin(runtime.store(level), frozen.endpoint, frozen.frontier, frozen.validator(),
                server.getTickCount(), result -> {
                    if (observation != EscrowTimingSink.NONE) { observation.sample("f1.barrier", System.nanoTime() - barrierBegan); }
                    if (closed || !result.confirmed() || !frozen.observe() || !noTransfer.getAsBoolean()) {
                        String reason = result.confirmed() ? "SOURCE_OR_JOURNAL_CHANGED"
                                : result.reason() == EscrowReason.NONE ? "SUPERSEDING_SAVE" : result.reason().name();
                        observation.sample("f1.stall." + reason, 0);
                        if (result.proof() != null) { result.proof().release(); }
                        if (!closed) { retry.failed(coordinator.ledger().id(), launch.logical(), launch.transfer(), 0, "F1_PROOF",
                                reason, server.getTickCount()); }
                        pending.remove(launch.logical(), frozen); finished.accept(null); return;
                    }
                    runtime.checkpoint("F1_READBACK_AFTER"); var proof = result.proof();
                    boolean queued = coordinator.flightWrite(launch.logical(), (root, generation) -> {
                        EscrowTags.require(frozen.observe(), "F1 source changed before ARMED capture");
                        runtime.checkpoint("F1_ARM_CAPTURE");
                        var candidate = preparation.capture(root, launch, generation);
                        serial.set(candidate.armedFlights().stream().filter(arm -> arm.logical().equals(launch.logical())
                                && arm.transfer().equals(launch.transfer())).findFirst().orElseThrow().serial());
                        return candidate;
                    }, () -> proof.valid() && frozen.unchanged.get() && noTransfer.getAsBoolean(), committed -> {
                        proof.release(); pending.remove(launch.logical(), frozen);
                        if (committed) {
                            retries.remove(launch.logical());
                            runtime.checkpoint("F1_ARM_ACK");
                            EscrowConsumerTimingProbe.mark(server, "f1.complete");
                            finished.accept(coordinator.ledger().armedFlights().stream()
                                    .filter(arm -> arm.logical().equals(launch.logical()) && arm.transfer().equals(launch.transfer())).findFirst().orElseThrow());
                        } else {
                            observation.sample("f1.stall.LEDGER_WRITE_FAILED", 0);
                            if (!closed) { retry.failed(coordinator.ledger().id(), launch.logical(), launch.transfer(), serial.get(),
                                    "F1_WRITE", "LEDGER_WRITE_FAILED", server.getTickCount()); }
                            finished.accept(null);
                        }
                    });
                    if (!queued) {
                        observation.sample("f1.stall.LEDGER_QUEUE_WAIT", 0);
                        proof.release(); pending.remove(launch.logical(), frozen);
                        if (!closed) { retry.failed(coordinator.ledger().id(), launch.logical(), launch.transfer(), 0,
                                "F1_WRITE", "LEDGER_QUEUE_WAIT", server.getTickCount()); }
                        finished.accept(null);
                    }
                });
        if (!started) {
            observation.sample("f1.refused.BARRIER_BEGIN", 0);
            pending.remove(launch.logical(), frozen);
            destinations.remove(launch.logical(), region);
            if (retry.failures() == 0) { retries.remove(launch.logical()); }
        }
        return started;
    }

    @Override public void close() {
        closed = true; pending.values().forEach(value -> value.unchanged.set(false)); pending.clear(); destinations.clear(); retries.clear();
    }
}
