package io.github.sunthemoon.advancedrocketrycommunity.rocket.server;

import static org.junit.jupiter.api.Assertions.*;

import io.github.sunthemoon.advancedrocketrycommunity.rocket.RocketLimits;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.forge.VanillaContainerRocketAdapter;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.model.*;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.persistence.RocketSnapshotNbtCodec;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.stats.RocketStats;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.transfer.RocketDiscoveryColumns;
import io.github.sunthemoon.advancedrocketrycommunity.testsupport.MinecraftBootstrap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BarrelBlockEntity;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class W197MaximumFixtureTest {
    private static RocketStructureSnapshot maximum;

    @BeforeAll static void bootstrap() {
        MinecraftBootstrap.initialize();
        var zero = new RocketPosition(0, 0, 0);
        var base = RocketStructureSnapshot.create(new UUID(197, 0), new ResourceLocation("minecraft:overworld"),
                new RocketPosition(4096, 100, 4096),
                List.of(new RocketBlock(zero, new RocketBlockState(new ResourceLocation("minecraft:iron_block"), Map.of()))),
                List.of(zero), new RocketStats(1, 100, 1000, 1000, 1, 1, 1, 0), 0,
                new RocketPropulsion(RocketPropulsion.Mode.ABSTRACT, 1000, 0, 1, 1, RocketPropulsion.Settings.DEFAULTS));
        maximum = W197MaximumFixture.maximumSnapshot(base);
    }

    @Test void exactAcceptedSnapshotCeilingRoundTrips() {
        var encoded = RocketSnapshotNbtCodec.encode(maximum);
        assertEquals(1_048_576, RocketNbtSize.uncompressedBytes(encoded));
        assertEquals(2048, maximum.blocks().size());
        assertEquals(32768, maximum.bounds().volume());
        assertEquals(128, maximum.stats().blockEntityCount());
        var decoded = RocketSnapshotNbtCodec.decode(encoded).snapshot().orElseThrow();
        assertEquals(encoded, RocketSnapshotNbtCodec.encode(decoded));
        assertEquals(2048, maximum.blocks().stream().map(RocketBlock::position).distinct().count());
    }

    @Test void all64NonOverlappingPairsRetainTheCeilingAnd16ColumnsPerEnd() {
        assertEquals(RocketOrdinaryRetirement.MAX_PENDING, W197MaximumFixture.QUEUE);
        var columns = new HashSet<net.minecraft.world.level.ChunkPos>();
        for (int i = 0; i < W197MaximumFixture.QUEUE; i++) {
            for (int start : new int[] {4096, 8192}) {
                var origin = new RocketPosition(start + i % 8 * 80, 100, start + i / 8 * 80);
                var snapshot = maximum.relocated(new UUID(start, i), maximum.sourceDimension(), origin, 0);
                var discovery = RocketDiscoveryColumns.of(snapshot);
                assertTrue(discovery.admissible());
                assertEquals(16, discovery.count());
                assertTrue(columns.addAll(discovery.columns()));
                assertEquals(1_048_576, RocketNbtSize.uncompressedBytes(RocketSnapshotNbtCodec.encode(snapshot)));
                assertEquals(20, new SealedPlanAdmission(snapshot).slots());
                assertTrue(new SealedPlanAdmission(snapshot).allowed());
            }
        }
        assertEquals(64 * 2 * 16, columns.size());
    }

    @Test void all128PayloadsAreAcceptedByTheActualBarrelAdapter() {
        var adapter = new VanillaContainerRocketAdapter();
        int count = 0;
        for (var block : maximum.blocks()) {
            if (block.blockEntityPayload().isEmpty()) { continue; }
            var payload = block.blockEntityPayload().orElseThrow();
            assertTrue(payload.uncompressedBytes() <= RocketLimits.MAX_BLOCK_ENTITY_NBT_BYTES);
            var barrel = new BarrelBlockEntity(BlockPos.ZERO, Blocks.BARREL.defaultBlockState());
            assertTrue(adapter.restore(barrel, payload));
            assertEquals(payload, adapter.capture(barrel));
            count++;
        }
        assertEquals(128, count);
    }

    @Test void oneByteOverCeilingIsStillRejected() {
        var encoded = RocketSnapshotNbtCodec.encode(maximum);
        var blocks = encoded.getList("relative_blocks", 10);
        var payload = java.util.stream.IntStream.range(0, blocks.size()).mapToObj(blocks::getCompound)
                .filter(block -> block.contains("block_entity")).findFirst().orElseThrow()
                .getCompound("block_entity").getCompound("data").getList("Items", 10).getCompound(0).getCompound("tag");
        var noise = payload.getByteArray("w197_test_padding");
        payload.putByteArray("w197_test_padding", java.util.Arrays.copyOf(noise, noise.length + 1));
        assertEquals(1_048_577, RocketNbtSize.uncompressedBytes(encoded));
        assertTrue(RocketSnapshotNbtCodec.decode(encoded).snapshot().isEmpty());
    }
}
