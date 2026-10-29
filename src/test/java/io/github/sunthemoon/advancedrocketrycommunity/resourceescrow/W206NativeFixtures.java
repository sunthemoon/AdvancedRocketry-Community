package io.github.sunthemoon.advancedrocketrycommunity.resourceescrow;

import io.github.sunthemoon.advancedrocketrycommunity.rocket.entity.RocketEntity;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.server.W206Observation;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentLinkedQueue;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;

/** Existing governed fixture and read-only checkpoint subscription, diagnostic export only. */
public final class W206NativeFixtures {
    private W206NativeFixtures() { }

    public static RocketEntity rocket(ServerLevel level, BlockPos at, UUID ledger, String shape) {
        EscrowTestEndpoints.requireEnabled(level.getServer());
        int[] extent = W206Observation.extent(shape);
        int[][] corners = extent[0] == 0 && extent[1] == 0 ? new int[][] {{0, 0}}
                : new int[][] {{0, 0}, {extent[0], extent[1]}};
        return EscrowGovernedMarginGameTests.rocket(level, at, ledger, corners);
    }

    public static Runnable observe(MinecraftServer server, ConcurrentLinkedQueue<Map<String, Object>> queue) {
        EscrowTestEndpoints.requireEnabled(server);
        return EscrowRuntime.of(server).terminalCutForTests(server, cut -> {
            if (queue.size() >= 32768) { throw new IllegalStateException("W206 observation bound exceeded"); }
            queue.add(Map.of("kind", "CHECKPOINT", "checkpoint", cut, "tick", server.getTickCount(),
                    "ns", System.nanoTime(), "owner_thread", server.isSameThread()));
        });
    }
}
