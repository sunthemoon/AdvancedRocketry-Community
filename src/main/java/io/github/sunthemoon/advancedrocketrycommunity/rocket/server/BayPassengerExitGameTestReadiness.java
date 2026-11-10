package io.github.sunthemoon.advancedrocketrycommunity.rocket.server;

import io.github.sunthemoon.advancedrocketrycommunity.AdvancedRocketryCommunity;
import io.github.sunthemoon.advancedrocketrycommunity.gametest.EntitySections;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.function.BooleanSupplier;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;

/** Fixture loading only; finishes before any rider, transfer, exit or ticket/chunk audit is created. */
final class BayPassengerExitGameTestReadiness {
    // W122's setup wall-clock bound, not an extension of any exit's timed budget.
    private static final long SETUP_NANOS = 600_000_000_000L;

    private BayPassengerExitGameTestReadiness() { }

    static void prepare(GameTestHelper helper, BlockPos origin, Set<Long> forcedColumns,
            BooleanSupplier gate, String scenario) {
        var level = helper.getLevel();
        var columns = new ArrayList<ChunkPos>();
        for (int x = (origin.getX() >> 4) - 1; x <= (origin.getX() >> 4) + 1; x++) {
            for (int z = (origin.getZ() >> 4) - 1; z <= (origin.getZ() >> 4) + 1; z++) {
                var column = new ChunkPos(x, z); columns.add(column);
                if (!level.getForcedChunks().contains(column.toLong())) {
                    level.setChunkForced(x, z, true); forcedColumns.add(column.toLong());
                }
            }
        }
        long tick = helper.getTick(), started = System.nanoTime();
        String before = missing(level, columns, origin.getY());
        boolean ready = EntitySections.awaitReady(level,
                () -> gate.getAsBoolean() && missing(level, columns, origin.getY()).isEmpty(), started + SETUP_NANOS);
        helper.assertTrue(ready, "Bay exit fixture readiness deadline: " + missing(level, columns, origin.getY()));
        helper.assertTrue(helper.getTick() == tick, "Fixture loading consumed the measured exit's tick budget");
        AdvancedRocketryCommunity.LOGGER.info(
                "ARCE_BAY_EXIT_READINESS case={} initially_missing={} test_tick={} elapsed_ms={}",
                scenario, before, tick, (System.nanoTime() - started) / 1_000_000);
    }

    private static String missing(ServerLevel level, List<ChunkPos> columns, int y) {
        var missing = new ArrayList<String>();
        for (var column : columns) {
            boolean loaded = level.areEntitiesLoaded(column.toLong());
            boolean ticking = level.isPositionEntityTicking(column.getWorldPosition().atY(y));
            if (!loaded || !ticking) { missing.add(column + ":entities=" + loaded + ",ticking=" + ticking); }
        }
        return String.join(";", missing);
    }
}
