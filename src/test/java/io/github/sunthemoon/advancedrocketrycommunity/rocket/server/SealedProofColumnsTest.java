package io.github.sunthemoon.advancedrocketrycommunity.rocket.server;

import static org.junit.jupiter.api.Assertions.*;

import io.github.sunthemoon.advancedrocketrycommunity.rocket.model.*;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.stats.RocketStats;
import io.github.sunthemoon.advancedrocketrycommunity.testsupport.MinecraftBootstrap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.ChunkPos;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class SealedProofColumnsTest {
    @BeforeAll static void bootstrap() { MinecraftBootstrap.initialize(); }
    @Test void standaloneSnapshotsDoNotAcquireTheUnrelatedGovernedSixteenColumnLimit() throws Exception {
        var columns = columns(snapshot(320), 21);
        assertTrue(columns.size() > 16); assertTrue(columns.contains(new ChunkPos(0, 0)));
        assertTrue(columns.contains(new ChunkPos(20, 0)));
    }
    @Test void theCombinedOrdinarySlotCeilingRefusesTheWholePlanWithoutTruncating() throws Exception {
        assertTrue(columns(snapshot(320), 109).isEmpty());
        assertTrue(columns(snapshot(32760), 1).isEmpty());
    }
    @SuppressWarnings("unchecked") private static List<ChunkPos> columns(RocketStructureSnapshot snapshot, int terrain) throws Exception {
        return (List<ChunkPos>) SealedEntityView.class.getDeclaredMethod("columns", RocketStructureSnapshot.class, int.class)
                .invoke(null, snapshot, terrain);
    }
    private static RocketStructureSnapshot snapshot(int length) {
        var state = new RocketBlockState(ResourceLocation.tryParse("minecraft:iron_block"), Map.of());
        return RocketStructureSnapshot.create(UUID.randomUUID(), ResourceLocation.tryParse("minecraft:overworld"),
                new RocketPosition(3, 64, 3), List.of(new RocketBlock(new RocketPosition(0, 0, 0), state),
                        new RocketBlock(new RocketPosition(length, 0, 0), state)), List.of(), new RocketStats(2, 20, 0, 0, 0, 0, 0, 0), 0);
    }
}
