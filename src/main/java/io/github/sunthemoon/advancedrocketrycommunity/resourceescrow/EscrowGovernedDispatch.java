package io.github.sunthemoon.advancedrocketrycommunity.resourceescrow;

import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketTransferPhase;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketTransferRecord;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.persistence.RocketTransferSavedData;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;
import net.minecraft.server.MinecraftServer;

/** Checked record-3 routing. A route is not an entity-action, storage-proof or ticket permit. */
public final class EscrowGovernedDispatch implements AutoCloseable {
    public enum Route { HOLD, ACTIVE, CLEANUP }

    public record Decision(Route route, String reason) {
        public Decision {
            java.util.Objects.requireNonNull(route);
            EscrowTags.require((route == Route.HOLD) == (reason != null), "Invalid governed route");
        }
        static Decision hold(String reason) { return new Decision(Route.HOLD, reason); }
    }

    /** A phase-specific checked view. Consumers must still validate their action and native observations. */
    public static final class Authority {
        private final Thread owner = Thread.currentThread();
        private final EscrowLedger root;
        private final RocketTransferRecord record;
        private final Decision decision;
        private final BooleanSupplier valid;
        private boolean invalidated;

        private Authority(EscrowLedger root, RocketTransferRecord record, Decision decision, BooleanSupplier valid) {
            this.root = root; this.record = record; this.decision = decision; this.valid = valid;
        }
        public RocketTransferRecord record() { return record; }
        public Decision decision() { return decision; }
        public boolean valid() {
            if (Thread.currentThread() != owner || invalidated) { return false; }
            if (!valid.getAsBoolean()) { invalidated = true; return false; }
            return true;
        }
        EscrowLedger ledger() { return root; }
    }

    private final Map<UUID, Authority> checked = new HashMap<>();
    private boolean closed;

    /** No ledger creation, entity lookup, chunk readiness query, ticket or journal retry occurs here. */
    public Decision classify(MinecraftServer server, RocketTransferSavedData journal, RocketTransferRecord record) {
        if (closed || server == null || !server.isSameThread()) { return Decision.hold("GOVERNED_ACTION_REQUIRED"); }
        var data = ResourceEscrowSavedData.existing(server);
        if (data == null) { return Decision.hold("GOVERNED_LEDGER_MISSING"); }
        var coordinator = data.bind(server);
        return classify(coordinator::ledger, () -> coordinator.governedAvailable(record.logicalRocketId()), journal, record);
    }

    Decision classify(Supplier<EscrowLedger> ledger, BooleanSupplier operational,
                      RocketTransferSavedData journal, RocketTransferRecord record) {
        if (closed || !operational.getAsBoolean()) {
            invalidated(record.logicalRocketId()); return Decision.hold("GOVERNED_LEDGER_UNAVAILABLE");
        }
        checked.entrySet().removeIf(entry -> !entry.getValue().valid());
        var cached = checked.get(record.transferId());
        if (cached != null && cached.record() == record && cached.root == ledger.get()) { return cached.decision(); }
        var authority = acquire(ledger, operational, journal, record);
        if (authority == null) { return Decision.hold("GOVERNED_JOURNAL_UNACKNOWLEDGED"); }
        if (checked.size() < 64 && authority.decision().route() != Route.HOLD) {
            checked.put(record.transferId(), authority);
        }
        return authority.decision();
    }

    Authority authority(Supplier<EscrowLedger> ledger, BooleanSupplier operational,
                        RocketTransferSavedData journal, RocketTransferRecord record) {
        var decision = classify(ledger, operational, journal, record);
        return decision.route() == Route.HOLD ? null : checked.get(record.transferId());
    }

    /** An invalidated/joined observation cannot retain a previously obtained checked view. */
    public void invalidated(UUID logical) {
        checked.entrySet().removeIf(entry -> {
            if (!entry.getValue().record().logicalRocketId().equals(logical)) { return false; }
            entry.getValue().invalidated = true; return true;
        });
    }

    static Authority acquire(Supplier<EscrowLedger> ledger, BooleanSupplier operational,
                             RocketTransferSavedData journal, RocketTransferRecord record) {
        if (!operational.getAsBoolean() || !journal.acknowledgedCurrent()
                || journal.find(record.transferId()).filter(record::equals).isEmpty()) { return null; }
        var guard = journal.acknowledgedGuard(record.logicalRocketId()).orElse(null);
        if (guard == null || !record.transferId().equals(guard.transfer())) { return null; }
        var root = ledger.get(); var lease = journal.acknowledgedLease();
        var decision = decide(root, record);
        return new Authority(root, record, decision, () -> operational.getAsBoolean()
                && ledger.get() == root && lease.getAsBoolean());
    }

    /** The caller supplies only the checked ledger baseline/current acknowledged root, never a candidate. */
    static Decision decide(EscrowLedger root, RocketTransferRecord record) {
        if (record.nativeTransfer().isEmpty()) { return Decision.hold("GOVERNED_RECORD_REQUIRED"); }
        var envelope = record.nativeTransfer().orElseThrow();
        var expected = envelope.armed();
        if (!root.id().equals(expected.ledger())) { return Decision.hold("GOVERNED_LEDGER_MISMATCH"); }
        var actual = root.armedFlights().stream().filter(arm -> arm.logical().equals(record.logicalRocketId())).findFirst().orElse(null);
        if (actual == null) { return Decision.hold("GOVERNED_ARM_MISSING"); }
        if (!sameLaunch(expected, actual) || actual.generation() > root.writerGeneration()
                || actual.serial() > root.issuedLaunchSerial()) { return Decision.hold("GOVERNED_ARM_MISMATCH"); }
        if (root.records().stream().anyMatch(op -> actual.logical().equals(op.guard().rocket())
                || actual.logical().equals(op.source().logicalRocket()) || actual.logical().equals(op.destination().logicalRocket())
                || actual.transfer().equals(op.guard().transfer()))) {
            return Decision.hold("GOVERNED_ESCROW_PENDING");
        }
        if (EscrowSettlementTombstones.logicalLocked(root, actual.logical())
                || root.tombstones().stream().flatMap(tag -> EscrowTags.list(tag, "guards", 64).stream())
                .map(tag -> EscrowCodec.guard((net.minecraft.nbt.CompoundTag) tag))
                .anyMatch(guard -> actual.transfer().equals(guard.transfer()))) {
            return Decision.hold("GOVERNED_RETIRED_AUTHORITY");
        }
        if (root.floors().stream().anyMatch(floor -> floor.hasUUID("transfer_id")
                && actual.transfer().equals(floor.getUUID("transfer_id")))) {
            return Decision.hold("GOVERNED_EXACT_TRANSFER_FLOOR");
        }
        var head = root.rocketAuthorities().stream().filter(value -> value.logical().equals(actual.logical())).findFirst().orElse(null);
        String headReason = headReason(root, record, actual, head);
        if (headReason != null) { return Decision.hold(headReason); }
        if (actual.outcome() == EscrowFlightTables.Outcome.HANDED_OFF && record.phase() != RocketTransferPhase.COMMITTED) {
            return Decision.hold("GOVERNED_TERMINAL_PHASE_MISMATCH");
        }
        // The enclosing record constructor validates the complete immutable envelopes. Checking
        // its exact tuple here does not rehash both snapshots for each classification tick.
        return new Decision(actual.outcome() == EscrowFlightTables.Outcome.ARMED ? Route.ACTIVE : Route.CLEANUP, null);
    }

    private static String headReason(EscrowLedger root, RocketTransferRecord record,
                                     EscrowFlightTables.Armed arm, EscrowFlightTables.Head head) {
        if (arm.anchor() == null) {
            return head != null || EscrowSettlementTombstones.history(root, arm.logical()) ? "GOVERNED_READY_REQUIRED" : null;
        }
        if (head == null || !head.owner().equals(arm.owner()) || !head.anchor().equals(arm.anchor())
                || head.releaseGeneration() != arm.releaseGeneration() || head.releaseGeneration() > arm.generation()
                || head.issued() != arm.serial()) { return "GOVERNED_READY_MISMATCH"; }
        boolean destination = arm.outcome() == EscrowFlightTables.Outcome.HANDED_OFF;
        String invariant = EscrowInvariant.rocket(destination ? arm.destination() : arm.source(), arm.logical(), arm.owner(),
                EscrowAdapterIds.ROCKET, destination ? record.destinationSnapshot() : record.sourceSnapshot());
        String signature = (destination ? record.destinationFlightData() : record.sourceFlightData()).fuel().resourceSignature();
        if (!head.invariant().equals(invariant) || (head.epoch() == arm.epoch() || arm.outcome() != EscrowFlightTables.Outcome.ARMED)
                && !head.signature().equals(signature)) { return "GOVERNED_READY_MISMATCH"; }
        if (arm.outcome() == EscrowFlightTables.Outcome.ARMED) {
            return head.completed() >= arm.serial() || !head.physical().equals(arm.source()) || head.epoch() > arm.epoch()
                    ? "GOVERNED_READY_MISMATCH" : null;
        }
        UUID survivor = arm.outcome() == EscrowFlightTables.Outcome.HANDED_OFF ? arm.destination() : arm.source();
        long epoch = arm.outcome() == EscrowFlightTables.Outcome.HANDED_OFF ? 0 : arm.epoch();
        return head.completed() != arm.serial() || head.outcome() != arm.outcome()
                || !head.physical().equals(survivor) || head.epoch() != epoch ? "GOVERNED_READY_MISMATCH" : null;
    }

    private static boolean sameLaunch(EscrowFlightTables.Armed expected, EscrowFlightTables.Armed actual) {
        var original = expected.encode(); var observed = actual.encode();
        observed.putString("outcome", EscrowFlightTables.Outcome.ARMED.name());
        return original.equals(observed);
    }

    @Override public void close() { closed = true; checked.values().forEach(value -> value.invalidated = true); checked.clear(); }
}
