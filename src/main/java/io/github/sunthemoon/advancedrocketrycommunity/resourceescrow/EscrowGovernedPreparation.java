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

    public static boolean begin(RocketEntity rocket, RocketFlightData countdown, RocketStructureSnapshot destination,
            Predicate<RocketTransferRecord> boundary, Consumer<RocketTransferRecord> accepted, Runnable discarded) {
        if (!(rocket.level() instanceof ServerLevel level) || !level.getServer().isSameThread()
                || !rocket.operational() || !RocketEscrowGate.idle(rocket)) { return false; }
        var server = level.getServer(); var data = ResourceEscrowSavedData.existing(server);
        if (!EscrowRuntime.of(server).flightCheckpoint().retryReady(countdown.logicalRocketId(), server.getTickCount())) { return false; }
        var journal = RocketTransferSavedData.get(server);
        if (data == null || !data.bind(server).governedAvailable(countdown.logicalRocketId())) { return false; }
        // R17 item 3: F1 acquires L's checked lease; another rocket's pending journal change does not refuse the launch.
        if (!journal.retryChecked(server) && !journal.actionAuthorityAvailable(countdown.logicalRocketId())) { return false; }
        if (!EscrowRuntime.of(server).barrier().available(level.dimension().location().toString(), true, server.getTickCount())) {
            return false;
        }
        var guard = journal.logicalGuard(countdown.logicalRocketId()).orElse(null);
        // COMMITTED-predecessor replacement has its own F7/F8 prerequisite; never treat it as no-T.
        if (guard == null || guard.transfer() != null) { return false; }
        EscrowGovernedLaunch prepared;
        try {
            prepared = EscrowGovernedLaunch.prepare(data.ledger(), EscrowNativeEnvelope.rocket(rocket),
                    rocket.snapshot().orElseThrow(), countdown, destination, UUID.randomUUID(),
                    new EscrowGuard(guard.logical(), null, Math.max(1, guard.sequence()), guard.rootDigest()));
        } catch (IllegalArgumentException | ArithmeticException refused) { return false; }
        return EscrowRuntime.of(server).flightCheckpoint().begin(rocket, prepared, boundary, record -> {
            if (record == null || !boundary.test(record)) { discarded.run(); return; }
            // This callback receives only the acknowledged ARMED tuple and exact still-present E.
            prepare(journal, server, record, () -> {
                if (!rocket.isAlive() || !rocket.isAddedToWorld() || !rocket.operational() || !boundary.test(record)) { return false; }
                var authority = EscrowRuntime.of(server).governedFlights().authority(data::ledger,
                        () -> data.bind(server).governedAvailable(record.logicalRocketId()), journal, record);
                var permit = countdownPermit(authority, EscrowNativeEnvelope.rocket(rocket), level.getGameTime(),
                        () -> rocket.isAlive() && rocket.isAddedToWorld() && boundary.test(record));
                if (permit == null) { return false; }
                EscrowRuntime.of(server).checkpoint("F2_PREPARED_ACK");
                rocket.advanceGovernedSource(permit);
                EscrowRuntime.of(server).checkpoint("F2_COUNTDOWN");
                accepted.accept(record); return true;
            }, discarded);
        });
    }

    /**
     * F2: writes the exact PREPARED record after the acknowledged ARMED tuple and queues its countdown for that record's
     * acknowledgement. A full callback queue does not undo ARMED or PREPARED, and it does not discard the launch
     * either (review B F9): PREPARED is already written, so F3 issues START_COUNTDOWN itself once it is acknowledged,
     * and the request stays JOURNAL_PENDING. A queued countdown still reports its own later discard.
     */
    static boolean prepare(RocketTransferSavedData journal, net.minecraft.server.MinecraftServer server, RocketTransferRecord record,
                           BooleanSupplier countdown, Runnable discarded) {
        journal.put(record);
        return journal.afterAcknowledged(server, record.logicalRocketId(), countdown, discarded);
    }

    static EscrowGovernedSourceAction.Permit countdownPermit(EscrowGovernedDispatch.Authority authority,
            CompoundTag observed, long gameTime, BooleanSupplier boundary) {
        if (authority == null) { return null; }
        try {
            return EscrowGovernedSourceAction.issue(authority, EscrowGovernedSourceAction.Action.START_COUNTDOWN,
                    observed, gameTime, boundary);
        } catch (IllegalArgumentException | ArithmeticException refused) { return null; }
    }
}
