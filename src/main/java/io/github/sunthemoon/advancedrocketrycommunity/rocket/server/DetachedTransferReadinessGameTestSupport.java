package io.github.sunthemoon.advancedrocketrycommunity.rocket.server;

import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketTransferRecord;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.model.RocketBounds;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.transfer.RocketDiscoveryColumns;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;

/** Fixture-only reads: the service's PREPARED arrival must not mistake a loading column for the tested refusal. */
final class DetachedTransferReadinessGameTestSupport {
    private DetachedTransferReadinessGameTestSupport() { }

    static List<ChunkPos> columns(BlockPos origin, RocketBounds bounds) {
        return RocketDiscoveryColumns.of(origin.getX(), origin.getZ(), bounds).releaseSet();
    }

    static boolean ready(ServerLevel level, List<ChunkPos> columns) {
        return RocketTransferEntities.entitiesLoadedColumns(columns, level::areEntitiesLoaded) == columns.size();
    }

    static void assertReady(GameTestHelper helper, ServerLevel level, RocketTransferRecord record,
                            List<ChunkPos> waitedColumns) {
        var actual = RocketTransferEntities.destinationTicketSet(record.destinationSnapshot());
        helper.assertTrue(level.dimension().location().equals(record.destinationSnapshot().sourceDimension())
                        && waitedColumns.containsAll(actual) && ready(level, actual),
                "Fixture readiness does not cover the prepared arrival's ticket set: waited=" + waitedColumns + ", actual=" + actual);
    }
}
