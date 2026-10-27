package io.github.sunthemoon.advancedrocketrycommunity.rocket.server;

import io.github.sunthemoon.advancedrocketrycommunity.AdvancedRocketryCommunity;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketTransferRecord;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.model.RocketBlock;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.model.RocketPosition;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.model.RocketStructureSnapshot;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.stats.RocketStats;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.transfer.RocketDiscoveryColumns;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.DistanceManager;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.Ticket;
import net.minecraft.world.level.ChunkPos;

/** Read-only GameTest observations of the recorded cold ends; never loads a column or places a ticket. */
final class RocketI6ColdColumns {
    private String previous = "";

    static RocketSourceRetirementFixtures.Flight crossingFootprint(RocketSourceRetirementFixtures.Flight fixture) {
        var old = fixture.record(); var source = old.sourceSnapshot(); var destination = old.destinationSnapshot();
        var state = source.blocks().get(0).state();
        var blocks = List.of(new RocketBlock(new RocketPosition(-1, 0, 0), state),
                new RocketBlock(new RocketPosition(0, 0, 0), state), new RocketBlock(new RocketPosition(1, 0, 0), state));
        var stats = new RocketStats(3, 300, source.stats().thrust(), source.stats().fuelCapacity(), 1, 1, 1, 0);
        source = RocketStructureSnapshot.create(source.snapshotId(), source.sourceDimension(), source.sourceOrigin(), blocks,
                source.passengerAnchors(), stats, source.createdAtGameTime(), source.propulsion().orElseThrow());
        destination = source.relocated(destination.snapshotId(), destination.sourceDimension(), destination.sourceOrigin(),
                destination.createdAtGameTime());
        var record = RocketTransferRecord.create(old.transferId(), old.logicalRocketId(), old.ownerId(), old.sourceEntityId(),
                source, destination, old.sourceFlightData(), old.destinationFlightData(), old.requiredFuel(), old.createdAtGameTime());
        return new RocketSourceRetirementFixtures.Flight(record, fixture.countdown());
    }

    void observe(GameTestHelper helper, RocketTransferRecord record, String stage) {
        var status = new StringBuilder(stage);
        for (var snapshot : List.of(record.sourceSnapshot(), record.destinationSnapshot())) {
            var level = RocketTransferEntities.level(helper.getLevel().getServer(), snapshot.sourceDimension());
            for (var column : RocketDiscoveryColumns.of(snapshot).columns()) {
                status.append(" ").append(snapshot.sourceDimension()).append("/").append(column.x).append(",").append(column.z)
                        .append(" ticket=").append(ticketed(level, column, record.transferId()))
                        .append(" terrain=").append(level.getChunkSource().getChunkNow(column.x, column.z) != null)
                        .append(" entities=").append(level.areEntitiesLoaded(column.toLong()))
                        .append(" ticking=").append(level.isPositionEntityTicking(
                                new BlockPos(column.x << 4, snapshot.sourceOrigin().y(), column.z << 4)));
            }
        }
        String next = status.toString();
        if (!next.equals(previous)) {
            AdvancedRocketryCommunity.LOGGER.info("W164_COLD_COLUMNS tick={} {}", helper.getLevel().getServer().getTickCount(), next);
            previous = next;
        }
    }

    private static boolean ticketed(ServerLevel level, ChunkPos column, UUID transfer) {
        // Same type-based inspection as RocketTransferFenceGameTests; no production access-transformer changes.
        var distance = level.getChunkSource().chunkMap.getDistanceManager();
        try {
            for (var field : DistanceManager.class.getDeclaredFields()) {
                if (!Long2ObjectMap.class.isAssignableFrom(field.getType())) { continue; }
                field.setAccessible(true);
                var map = (Long2ObjectMap<?>) field.get(distance);
                if (!(map.get(column.toLong()) instanceof Iterable<?> tickets)) { continue; }
                for (var ticket : tickets) {
                    if (ticket instanceof Ticket<?> && ticket.toString().contains("arce_rocket_flight")
                            && ticket.toString().contains(transfer.toString())) { return true; }
                }
            }
        } catch (ReflectiveOperationException failure) { throw new IllegalStateException("Cannot inspect cold-site tickets", failure); }
        return false;
    }
}
