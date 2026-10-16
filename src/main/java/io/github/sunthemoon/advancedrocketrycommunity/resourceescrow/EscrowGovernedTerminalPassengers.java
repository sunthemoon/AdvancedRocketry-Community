package io.github.sunthemoon.advancedrocketrycommunity.resourceescrow;

import io.github.sunthemoon.advancedrocketrycommunity.rocket.entity.RocketEntity;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketPassengerManifest;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketTransferRecord;
import java.util.UUID;
import java.util.function.Predicate;
import net.minecraft.server.MinecraftServer;

/** Owner-thread F6 passenger prerequisite. No playerdata creation, movement or implicit offline success. */
final class EscrowGovernedTerminalPassengers {
    private EscrowGovernedTerminalPassengers() { }

    static boolean resolved(MinecraftServer server, RocketTransferRecord record, EscrowFlightTables.Outcome outcome,
                            RocketEntity survivor) {
        if (!server.isSameThread() || survivor == null || !survivor.isAlive() || !survivor.isAddedToWorld()
                || survivor.getServer() != server || !survivor.operational()) { return false; }
        try {
            if (!EscrowGovernedTerminalDisk.survivor(record, outcome, EscrowNativeEnvelope.rocket(survivor))) { return false; }
        } catch (IllegalArgumentException | IllegalStateException | ArithmeticException invalid) { return false; }
        var manifest = (outcome == EscrowFlightTables.Outcome.CANCELLED
                ? record.sourceFlightData() : record.destinationFlightData()).passengers();
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
}
