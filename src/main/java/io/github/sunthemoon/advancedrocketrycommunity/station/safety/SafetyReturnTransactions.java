package io.github.sunthemoon.advancedrocketrycommunity.station.safety;

import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketTransferPhase;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketTransferRecord;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.persistence.RocketTransferSavedData;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import net.minecraft.server.MinecraftServer;

/**
 * ADR-066 section 3.3: a player in a rocket transfer is not eligible for the safety return. A transfer holds every
 * passenger in either manifest of a journal record that has not committed; a committed record is a landed reservation
 * whose passengers have arrived. A blocked journal hides its records, so it holds every player: unknown is not absent.
 */
public final class SafetyReturnTransactions {
    private SafetyReturnTransactions() {
    }

    public static boolean inRocketTransfer(MinecraftServer server, UUID player) {
        Objects.requireNonNull(server, "server");
        RocketTransferSavedData journal = RocketTransferSavedData.get(server);
        return inRocketTransfer(journal.operational(), journal.operational() ? journal.entries() : List.of(), player);
    }

    /** The pure rule, for tests. */
    public static boolean inRocketTransfer(boolean journalOperational, List<RocketTransferRecord> records,
                                           UUID player) {
        Objects.requireNonNull(records, "records");
        Objects.requireNonNull(player, "player");
        if (!journalOperational) {
            return true;
        }
        for (RocketTransferRecord record : records) {
            if (record.phase() != RocketTransferPhase.COMMITTED
                    && (record.sourceFlightData().passengers().assignment(player).isPresent()
                    || record.destinationFlightData().passengers().assignment(player).isPresent())) {
                return true;
            }
        }
        return false;
    }
}
