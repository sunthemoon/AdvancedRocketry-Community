package io.github.sunthemoon.advancedrocketrycommunity.rocket.server;

import io.github.sunthemoon.advancedrocketrycommunity.AdvancedRocketryCommunity;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.entity.RocketEntity;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketFlightState;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketTransferRecord;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.persistence.RocketTransferSavedData;
import java.util.Objects;
import java.util.UUID;
import net.minecraft.server.MinecraftServer;

/** Tracks installed flight lifecycle state and bounded packaged-release checkpoints. */
final class RocketFlightLifecycleController {
    private boolean active;
    private boolean recoverySuppressedForReleaseTest;
    private UUID checkpointTransferId;
    private RocketFlightReleaseCheckpoint checkpoint;

    void onInstalled() {
        active = true;
    }

    boolean active() {
        return active;
    }

    boolean recoverySuppressedForReleaseTest() {
        return recoverySuppressedForReleaseTest;
    }

    void suppressRecoveryUntilStopForReleaseTest() {
        requireReleaseTestHooks();
        recoverySuppressedForReleaseTest = true;
    }

    void arm(UUID transferId, RocketFlightReleaseCheckpoint requestedCheckpoint) {
        requireReleaseTestHooks();
        if (!active) {
            throw new IllegalStateException("Rocket flight lifecycle is already paused");
        }
        checkpointTransferId = Objects.requireNonNull(transferId, "transferId");
        checkpoint = Objects.requireNonNull(requestedCheckpoint, "requestedCheckpoint");
    }

    void cancel(UUID transferId) {
        requireReleaseTestHooks();
        if (Objects.equals(checkpointTransferId, transferId)) {
            checkpointTransferId = null;
            checkpoint = null;
        }
    }

    void pauseIfReached(MinecraftServer server) {
        UUID transferId = checkpointTransferId;
        RocketFlightReleaseCheckpoint requestedCheckpoint = checkpoint;
        if (transferId == null || requestedCheckpoint == null) {
            return;
        }
        RocketTransferSavedData journal = RocketTransferSavedData.get(server);
        RocketTransferRecord record = journal.operational()
                ? journal.find(transferId).orElse(null)
                : null;
        if (record == null) {
            return;
        }
        RocketEntity source = RocketTransferEntities.findSource(server, record);
        RocketEntity destination = RocketTransferEntities.findDestination(server, record);
        RocketEntity authority = record.phase().destinationAuthoritative() ? destination : source;
        RocketFlightState state = authority == null
                ? null
                : authority.flightData().map(data -> data.state()).orElse(null);
        if (!requestedCheckpoint.reached(record.phase(), state)) {
            return;
        }
        active = false;
        checkpointTransferId = null;
        checkpoint = null;
        AdvancedRocketryCommunity.LOGGER.info(
                "ARCE_RELEASE_TEST_FLIGHT_PAUSED checkpoint={} transfer={} phase={} state={} entity={}",
                requestedCheckpoint,
                transferId,
                record.phase(),
                state,
                authority == null ? "none" : authority.getUUID()
        );
    }

    void requireReleaseTestHooks() {
        if (!Boolean.getBoolean("advancedrocketrycommunity.releaseTestHooks")) {
            throw new IllegalStateException("Rocket release-test hooks are disabled");
        }
    }

    void clear() {
        recoverySuppressedForReleaseTest = false;
        checkpointTransferId = null;
        checkpoint = null;
    }
}
