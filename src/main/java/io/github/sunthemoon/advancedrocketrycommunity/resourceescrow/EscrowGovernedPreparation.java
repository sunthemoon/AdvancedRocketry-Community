package io.github.sunthemoon.advancedrocketrycommunity.resourceescrow;

import io.github.sunthemoon.advancedrocketrycommunity.rocket.entity.RocketEntity;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketFlightData;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketTransferRecord;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.persistence.RocketTransferSavedData;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.model.RocketStructureSnapshot;
import java.util.UUID;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Predicate;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;

/** Live F1/F2 bridge. Neither a queued launch nor ARMED alone authorizes countdown or success. */
public final class EscrowGovernedPreparation {
    private EscrowGovernedPreparation() { }

    /** Read-only pre-F2 pad reservations; publication transfers responsibility to the journal in the same callback. */
    public static java.util.List<io.github.sunthemoon.advancedrocketrycommunity.rocket.transaction.RocketRegion> pendingDestinations(
            net.minecraft.server.MinecraftServer server) {
        return EscrowRuntime.of(server).flightCheckpoint().pendingDestinations();
    }

    /**
     * W90b (design 7.2 item 4): {@link #begin}'s preconditions without a side effect. L's journal lease is checked
     * without {@code retryChecked}, which can start a write, so this may refuse where begin would proceed; it never
     * admits what begin refuses. The destination warm-up allocates or binds nothing when it fails.
     */
    public static boolean available(RocketEntity rocket, RocketFlightData countdown) {
        return preconditions(rocket, countdown, false);
    }

    /** begin's own preconditions, in begin's order; {@code retry} lets L's lease check push a pending journal write. */
    private static boolean preconditions(RocketEntity rocket, RocketFlightData countdown, boolean retry) {
        if (!(rocket.level() instanceof ServerLevel level) || !level.getServer().isSameThread()
                || !rocket.operational() || !RocketEscrowGate.idle(rocket)) { return refused(rocket, countdown, retry, "SOURCE_OR_IDLE"); }
        var server = level.getServer(); var data = ResourceEscrowSavedData.existing(server);
        if (!EscrowRuntime.of(server).flightCheckpoint().retryReady(countdown.logicalRocketId(), server.getTickCount())) { return refused(rocket, countdown, retry, "RETRY_READY"); }
        var journal = RocketTransferSavedData.get(server);
        if (data == null || !data.bind(server).governedAvailable(countdown.logicalRocketId())) { return refused(rocket, countdown, retry, "GOVERNED_AVAILABLE"); }
        // R17 item 3: F1 acquires L's checked lease; another rocket's pending journal change does not refuse the launch.
        if (!(retry && journal.retryChecked(server)) && !journal.actionAuthorityAvailable(countdown.logicalRocketId())) { return refused(rocket, countdown, retry, "ACTION_AUTHORITY"); }
        if (!EscrowRuntime.of(server).barrier().available(level.dimension().location().toString(), true, server.getTickCount())) {
            return refused(rocket, countdown, retry, "BARRIER_AVAILABLE");
        }
        var guard = journal.logicalGuard(countdown.logicalRocketId()).orElse(null);
        // COMMITTED-predecessor replacement has its own F7/F8 prerequisite; never treat it as no-T.
        if (guard == null) { return refused(rocket, countdown, retry, "GUARD_ABSENT"); }
        if (guard.transfer() != null) { return refused(rocket, countdown, retry, "GUARD_HAS_TRANSFER"); }
        if (!EscrowRuntime.of(server).flightCheckpoint().admits(countdown.logicalRocketId())) { return refused(rocket, countdown, retry, "CHECKPOINT_ADMITS"); }
        return true;
    }

    private static boolean refused(RocketEntity rocket, RocketFlightData countdown, boolean retry, String check) {
        if (retry) { EscrowLaunchTrace.note(rocket, countdown, "BEGIN_PRECONDITION", check); }
        return false;
    }

    public static boolean begin(RocketEntity rocket, RocketFlightData countdown, RocketStructureSnapshot destination,
            Predicate<RocketTransferRecord> boundary, Consumer<RocketTransferRecord> accepted, Runnable discarded) {
        if (!preconditions(rocket, countdown, true)) { return false; }
        var level = (ServerLevel) rocket.level(); var server = level.getServer(); var data = ResourceEscrowSavedData.existing(server);
        var journal = RocketTransferSavedData.get(server);
        var guard = journal.logicalGuard(countdown.logicalRocketId()).orElseThrow();
        EscrowGovernedLaunch prepared;
        try {
            EscrowLaunchTrace.note(rocket, countdown, "PREPARE_ENTER", "");
            prepared = EscrowGovernedLaunch.prepare(data.ledger(), EscrowGovernedEnvelope.capture(rocket),
                    rocket.snapshot().orElseThrow(), countdown, destination, UUID.randomUUID(),
                    new EscrowGuard(guard.logical(), null, Math.max(1, guard.sequence()), guard.rootDigest()));
        } catch (IllegalArgumentException | ArithmeticException refused) {
            EscrowLaunchTrace.note(rocket, countdown, "PREPARE_EXCEPTION",
                    refused.getClass().getSimpleName() + ": " + refused.getMessage());
            return false;
        }
        EscrowLaunchTrace.note(rocket, countdown, "PREPARE_OK", "");
        boolean queued = EscrowRuntime.of(server).flightCheckpoint().begin(rocket, prepared, boundary, record -> {
            if (record == null || !boundary.test(record)) {
                EscrowLaunchDiscard.note("F1_CALLBACK", () -> "logical=" + countdown.logicalRocketId()
                        + " transfer=" + countdown.plan().orElseThrow().requestId()
                        + " reason=" + (record == null ? "NO_ACKNOWLEDGED_RECORD" : "ACTION_BOUNDARY"));
                discarded.run(); return;
            }
            // This callback receives only the acknowledged ARMED tuple and exact still-present E.
            prepare(journal, server, record, () -> {
                if (!rocket.isAlive() || !rocket.isAddedToWorld() || !rocket.operational() || !boundary.test(record)) {
                    EscrowLaunchDiscard.note("F2_CALLBACK", () -> "logical=" + record.logicalRocketId()
                            + " transfer=" + record.transferId() + " reason=SOURCE_OR_ACTION_BOUNDARY");
                    return false;
                }
                var authority = EscrowRuntime.of(server).governedFlights().authority(data::ledger,
                        () -> data.bind(server).governedAvailable(record.logicalRocketId()), journal, record);
                var permit = countdownPermit(authority, EscrowGovernedEnvelope.capture(rocket), level.getGameTime(),
                        () -> rocket.isAlive() && rocket.isAddedToWorld() && boundary.test(record));
                if (permit == null) {
                    EscrowLaunchDiscard.note("F2_CALLBACK", () -> "logical=" + record.logicalRocketId()
                            + " transfer=" + record.transferId() + " reason=NO_COUNTDOWN_PERMIT");
                    return false;
                }
                EscrowRuntime.of(server).checkpoint("F2_PREPARED_ACK");
                rocket.advanceGovernedSource(permit);
                EscrowLaunchTrace.note(rocket, countdown, "COUNTDOWN", "governed=true");
                EscrowRuntime.of(server).checkpoint("F2_COUNTDOWN");
                accepted.accept(record); return true;
            }, discarded, () -> EscrowRuntime.of(server).checkpoint("F2_PREPARED_SUBMITTED"));
        });
        EscrowLaunchTrace.note(rocket, countdown, queued ? "CHECKPOINT_QUEUED" : "CHECKPOINT_REFUSED", "");
        return queued;
    }

    /**
     * F2: writes the exact PREPARED record after the acknowledged ARMED tuple and queues its countdown for that record's
     * acknowledgement. A full callback queue does not undo ARMED or PREPARED, and it does not discard the launch
     * either (review B F9): PREPARED is already written, so F3 issues START_COUNTDOWN itself once it is acknowledged,
     * and the request stays JOURNAL_PENDING. A queued countdown still reports its own later discard.
     */
    static boolean prepare(RocketTransferSavedData journal, net.minecraft.server.MinecraftServer server, RocketTransferRecord record,
                           BooleanSupplier countdown, Runnable discarded) {
        return prepare(journal, server, record, countdown, discarded, () -> { });
    }

    private static boolean prepare(RocketTransferSavedData journal, net.minecraft.server.MinecraftServer server, RocketTransferRecord record,
                           BooleanSupplier countdown, Runnable discarded, Runnable submitted) {
        journal.put(record);
        submitted.run();
        return journal.afterAcknowledged(server, record.logicalRocketId(), countdown, discarded);
    }

    static EscrowGovernedSourceAction.Permit countdownPermit(EscrowGovernedDispatch.Authority authority,
            CompoundTag observed, long gameTime, BooleanSupplier boundary) {
        if (authority == null) {
            EscrowLaunchDiscard.note("F2_PERMIT", () -> "logical=null transfer=null reason=NO_AUTHORITY");
            return null;
        }
        try {
            return EscrowGovernedSourceAction.issue(authority, EscrowGovernedSourceAction.Action.START_COUNTDOWN,
                    observed, gameTime, boundary);
        } catch (IllegalArgumentException | ArithmeticException refused) {
            EscrowLaunchDiscard.note("F2_PERMIT", () -> "logical=" + authority.record().logicalRocketId()
                    + " transfer=" + authority.record().transferId()
                    + " reason=" + refused.getClass().getSimpleName() + ": " + refused.getMessage()
                    + " fields=" + EscrowLaunchDiscard.detail(() -> EscrowLaunchDiscard.differences(
                            authority.record().nativeTransfer().orElseThrow().sourceEnvelope(authority.record()), observed)));
            return null;
        }
    }
}
