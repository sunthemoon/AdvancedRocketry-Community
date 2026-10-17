package io.github.sunthemoon.advancedrocketrycommunity.resourceescrow;

import io.github.sunthemoon.advancedrocketrycommunity.rocket.entity.RocketEntity;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketPassengerManifest;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketTransferRecord;
import java.util.UUID;
import java.util.function.BooleanSupplier;
import java.util.function.Predicate;
import net.minecraft.server.MinecraftServer;

/** Owner-thread F6 passenger prerequisite. No playerdata creation, movement or implicit offline success. */
public final class EscrowGovernedTerminalPassengers {
    private EscrowGovernedTerminalPassengers() { }

    static boolean resolved(MinecraftServer server, RocketTransferRecord record, EscrowFlightTables.Outcome outcome,
                            RocketEntity survivor) {
        // Offline assignments are a cheap, silent wait, not a reason to encode the whole rocket each tick.
        try {
            return afterPassengers(() -> present(server, record, outcome, survivor),
                    () -> EscrowGovernedTerminalDisk.survivor(record, outcome, EscrowGovernedEnvelope.capture(survivor)));
        } catch (IllegalArgumentException | IllegalStateException | ArithmeticException invalid) { return false; }
    }

    /** Scheduling hint only. A positive result never supplies a terminal proof or action authority. */
    public static boolean present(MinecraftServer server, RocketTransferRecord record, EscrowFlightTables.Outcome outcome,
                                  RocketEntity survivor) {
        var manifest = (outcome == EscrowFlightTables.Outcome.CANCELLED
                ? record.sourceFlightData() : record.destinationFlightData()).passengers();
        return present(server, manifest, survivor);
    }

    static boolean present(MinecraftServer server, RocketPassengerManifest manifest, RocketEntity survivor) {
        if (!server.isSameThread() || survivor == null || !survivor.isAlive() || !survivor.isAddedToWorld()
                || survivor.getServer() != server || !survivor.operational()) { return false; }
        return resolved(manifest, id -> {
            var player = server.getPlayerList().getPlayer(id);
            return player != null && player.getServer() == server && !player.isRemoved() && player.isAlive()
                    && !player.isSpectator() && player.connection != null && player.connection.connection.isConnected()
                    && player.level() == survivor.level()
                    && player.getVehicle() == survivor;
        });
    }

    static boolean resolved(RocketPassengerManifest manifest, Predicate<UUID> onlineAtSurvivor) {
        return manifest.assignments().stream().allMatch(seat -> onlineAtSurvivor.test(seat.passengerId()));
    }

    /** Keep polling ahead of the caller's full boundary/capture, including when the last player reconnects. */
    static boolean afterPassengers(BooleanSupplier present, BooleanSupplier capture) {
        return present.getAsBoolean() && capture.getAsBoolean();
    }
}
