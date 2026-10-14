package io.github.sunthemoon.advancedrocketrycommunity.rocket.server;

import static io.github.sunthemoon.advancedrocketrycommunity.rocket.server.RocketTransferBayRecoveryGate.Action.TICK_SOURCE;

import io.github.sunthemoon.advancedrocketrycommunity.AdvancedRocketryCommunity;
import io.github.sunthemoon.advancedrocketrycommunity.celestial.CelestialIds;
import io.github.sunthemoon.advancedrocketrycommunity.progression.classic.ClassicAdvancementEvents;
import io.github.sunthemoon.advancedrocketrycommunity.progression.classic.firstevent.FirstEvent;
import io.github.sunthemoon.advancedrocketrycommunity.progression.classic.firstevent.FirstEventCutSeams;
import io.github.sunthemoon.advancedrocketrycommunity.progression.classic.firstevent.FirstEventDisposition;
import io.github.sunthemoon.advancedrocketrycommunity.progression.classic.firstevent.FirstEventDispositionSource;
import io.github.sunthemoon.advancedrocketrycommunity.progression.classic.firstevent.FirstEventKind;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.entity.RocketEntity;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketFlightData;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketFlightLimits;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketFlightState;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketTransferPhase;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketTransferRecord;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.persistence.RocketTransferSavedData;
import java.util.List;
import java.util.UUID;
import java.util.function.BooleanSupplier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

/**
 * W44 section 2.3: the checked Moon landing completion. An eligible first completion is elected in journal RAM and
 * written by the existing W11b2 writer; LANDED and its notifications run only as the continuation of the exact
 * confirmation. A completion whose event the journal already holds is projected (section 2.5) without any RAM change.
 */
final class MoonLandingCompletion {
    /** LANDED_NOW: the world-first path owns this landing; ORDINARY: land now; WAIT: hold the terminal pose. */
    enum Result { LANDED_NOW, WAIT, ORDINARY }

    private MoonLandingCompletion() {
    }

    static Result complete(MinecraftServer server, RocketTransferSavedData journal, RocketTransferRecord record,
                           RocketEntity destination, RocketTransferBayDispatch bays, BooleanSupplier publish,
                           FirstEventDispositionSource dispositions) {
        // 1. Only a Moon destination counts; the source Level does not matter (choice R2).
        if (!destination.level().dimension().equals(CelestialIds.MOON_LEVEL)) {
            return Result.ORDINARY;
        }
        // 2. A safe actual completion of the unique committed authority, or wait in the terminal pose.
        if (!safeCompletion(server, journal, record, destination, bays)) {
            return Result.WAIT;
        }
        // 3. The RAM slot is classified, never treated as proof: this transfer's event is projected.
        var ram = journal.firstMoonLandingRam();
        if (ram.event() instanceof FirstEvent.MoonLanding held && held.transferId().equals(record.transferId())) {
            journal.afterAcknowledged(server, record.logicalRocketId(), publish, () -> { });
            return Result.LANDED_NOW;
        }
        FirstEventDisposition disposition = dispositions.disposition(server, FirstEventKind.FIRST_MOON_LANDING,
                journal.durableFirstMoonLanding(), ram);
        if (disposition == FirstEventDisposition.NOT_FIRST) {
            return Result.ORDINARY;
        }
        // 4. The writer needs a continuation slot and no failed write.
        if (disposition == FirstEventDisposition.DEFER || !journal.canQueueEffect()) {
            return Result.WAIT;
        }
        // 5. Snapshot the event: the aboard, eligible players, sorted; never truncated.
        List<UUID> participants = FirstEventCutSeams.participants(server, FirstEventKind.FIRST_MOON_LANDING)
                .orElseGet(() -> RocketTransferEntities.aboard(destination).stream()
                        .filter(ClassicAdvancementEvents::eligible).map(ServerPlayer::getUUID).sorted().toList());
        if (participants.size() > RocketFlightLimits.MAX_PASSENGERS) {
            return Result.WAIT;
        }
        var origin = record.destinationSnapshot().sourceOrigin();
        FirstEvent.MoonLanding event = FirstEvent.MoonLanding.create(UUID.randomUUID(), record.transferId(),
                record.logicalRocketId(), record.ownerId(), destination.getUUID(), record.checksum(), origin.x(),
                origin.y(), origin.z(), destination.level().getGameTime(), participants);
        // 6. EMPTY to ELECTED in RAM; 7. LANDED and the notifications wait for the exact confirmation.
        if (!journal.electFirstMoonLanding(event)) {
            return Result.WAIT;
        }
        AdvancedRocketryCommunity.LOGGER.info(
                "ARCE_FIRST_EVENT_ELECTED kind=first_moon_landing event={} transfer={} participants={}",
                event.eventId(), record.transferId(), participants.size());
        journal.afterAcknowledged(server, record.logicalRocketId(), publish, () -> { });
        return Result.LANDED_NOW;
    }

    /**
     * Step 2: an operational journal, a COMMITTED record (its checksum is validated on construction), no escrow fence
     * or bay hold, exactly one loaded match that is the record's destination, committed authority and DESCENT with
     * its descent elapsed.
     */
    private static boolean safeCompletion(MinecraftServer server, RocketTransferSavedData journal,
                                          RocketTransferRecord record, RocketEntity destination,
                                          RocketTransferBayDispatch bays) {
        if (!journal.operational() || record.phase() != RocketTransferPhase.COMMITTED
                || !RocketTransferEscrowRules.flightAllowed(server, journal, record, bays, TICK_SOURCE)) {
            return false;
        }
        List<RocketEntity> matches = RocketTransferEntities.loadedMatches(server, record, true);
        if (matches.size() != 1 || matches.get(0) != destination
                || record.destinationEntityId().filter(destination.getUUID()::equals).isEmpty()
                || !RocketTransferEntities.isCommittedAuthority(destination, record)) {
            return false;
        }
        RocketFlightData flight = destination.flightData().orElse(null);
        return flight != null && flight.state() == RocketFlightState.DESCENT
                && destination.level().getGameTime() - flight.stateStartedGameTime() >= RocketFlightLimits.DESCENT_TICKS;
    }
}
