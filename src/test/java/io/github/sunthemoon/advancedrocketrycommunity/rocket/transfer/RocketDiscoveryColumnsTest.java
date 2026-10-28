package io.github.sunthemoon.advancedrocketrycommunity.rocket.transfer;

import static org.junit.jupiter.api.Assertions.*;

import io.github.sunthemoon.advancedrocketrycommunity.rocket.model.RocketBlock;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.model.RocketBlockState;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.model.RocketBounds;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.model.RocketPosition;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.model.RocketStructureSnapshot;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.stats.RocketStats;
import io.github.sunthemoon.advancedrocketrycommunity.testsupport.MinecraftBootstrap;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.ChunkPos;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/** W90 (R21 item 2): the one checked column set, server-free. */
class RocketDiscoveryColumnsTest {
    @BeforeAll static void bootstrap() { MinecraftBootstrap.initialize(); }

    private static RocketBounds bounds(int minX, int maxX, int minZ, int maxZ) {
        return new RocketBounds(new RocketPosition(minX, 0, minZ), new RocketPosition(maxX, 1, maxZ));
    }

    private static List<ChunkPos> rectangle(int minX, int maxX, int minZ, int maxZ) {
        var columns = new ArrayList<ChunkPos>();
        for (int x = minX; x <= maxX; x++) { for (int z = minZ; z <= maxZ; z++) { columns.add(new ChunkPos(x, z)); } }
        return columns;
    }

    @Test void aMarginInsideOneChunkIsTheOriginColumnAndBordersAddOneColumnEach() {
        var inside = RocketDiscoveryColumns.of(8, 8, bounds(0, 0, 0, 0));
        assertTrue(inside.admissible()); assertEquals(List.of(new ChunkPos(0, 0)), inside.columns());
        var border = RocketDiscoveryColumns.of(15, 8, bounds(0, 0, 0, 0));
        assertEquals(rectangle(0, 1, 0, 0), border.columns());
        var corner = RocketDiscoveryColumns.of(15, 15, bounds(0, 0, 0, 0));
        assertEquals(rectangle(0, 1, 0, 1), corner.columns()); assertEquals(4, corner.count());
        // Margin 63 to 66 gives columns 3 to 4.
        assertEquals(rectangle(3, 4, 0, 0), RocketDiscoveryColumns.of(64, 8, bounds(0, 0, 0, 0)).columns());
    }

    @Test void theBoundIsSixteenColumnsWhateverTheShape() {
        // 1 x 16: blocks 0..252 from x = 1 span 0..255.
        var line = RocketDiscoveryColumns.of(1, 8, bounds(0, 252, 0, 0));
        assertEquals(RocketDiscoveryColumns.Status.VALID, line.status()); assertEquals(16, line.count());
        assertEquals(rectangle(0, 15, 0, 0), line.columns());
        // 1 x 17: the same rocket one block further spans 1..256.
        assertEquals(RocketDiscoveryColumns.Status.OVER_BUDGET, RocketDiscoveryColumns.of(2, 8, bounds(0, 252, 0, 0)).status());
        var square = RocketDiscoveryColumns.of(1, 1, bounds(0, 49, 0, 49));
        assertTrue(square.admissible()); assertEquals(16, square.count());
        var wide = RocketDiscoveryColumns.of(13, 1, bounds(0, 49, 0, 49));
        assertEquals(RocketDiscoveryColumns.Status.OVER_BUDGET, wide.status());
        assertEquals(0, wide.count()); assertTrue(wide.columns().isEmpty()); assertFalse(wide.containsOrigin());
    }

    @Test void negativeCoordinatesUseFloorColumns() {
        var columns = RocketDiscoveryColumns.of(-1, -1, bounds(0, 0, 0, 0));
        assertEquals(rectangle(-1, 0, -1, 0), columns.columns());
        assertEquals(new ChunkPos(-1, -1), columns.origin()); assertTrue(columns.admissible());
        assertEquals(rectangle(-2, -2, -2, -2), RocketDiscoveryColumns.of(-24, -24, bounds(0, 0, 0, 0)).columns());
        assertEquals(rectangle(-2, -1, -1, -1), RocketDiscoveryColumns.of(-17, -8, bounds(0, 0, 0, 0)).columns());
    }

    @Test void theExactMaxPlusTwoBoundary() {
        // Region max 13 stays in column 0 (13 + 2 = 15); 14 reaches column 1.
        assertEquals(rectangle(0, 0, 0, 0), RocketDiscoveryColumns.of(8, 8, bounds(0, 5, 0, 0)).columns());
        assertEquals(rectangle(0, 1, 0, 0), RocketDiscoveryColumns.of(8, 8, bounds(0, 6, 0, 0)).columns());
        // Region min 0 reaches column -1 (0 - 1); 1 stays in column 0.
        assertEquals(rectangle(-1, 0, 0, 0), RocketDiscoveryColumns.of(0, 8, bounds(0, 0, 0, 0)).columns());
        assertEquals(rectangle(0, 0, 0, 0), RocketDiscoveryColumns.of(1, 8, bounds(0, 0, 0, 0)).columns());
    }

    @Test void overflowAtEitherEndIsArithmeticAndNeverThrows() {
        for (var columns : List.of(
                RocketDiscoveryColumns.of(Integer.MIN_VALUE, 8, bounds(0, 0, 0, 0)),
                RocketDiscoveryColumns.of(Integer.MAX_VALUE - 1, 8, bounds(0, 0, 0, 0)),
                RocketDiscoveryColumns.of(8, Integer.MIN_VALUE, bounds(0, 0, 0, 0)),
                RocketDiscoveryColumns.of(8, Integer.MAX_VALUE - 1, bounds(0, 0, 0, 0)),
                RocketDiscoveryColumns.of(Integer.MAX_VALUE, 8, bounds(0, 1, 0, 0)),
                RocketDiscoveryColumns.of(Integer.MIN_VALUE, 8, bounds(-1, 0, 0, 0)))) {
            assertEquals(RocketDiscoveryColumns.Status.ARITHMETIC, columns.status());
            assertFalse(columns.valid() || columns.containsOrigin() || columns.admissible());
            assertEquals(List.of(), columns.refreshSet());
            assertEquals(List.of(columns.origin()), columns.releaseSet(), "Release still names the origin column");
        }
        assertEquals(RocketDiscoveryColumns.Status.VALID, RocketDiscoveryColumns.of(Integer.MAX_VALUE - 2, 8, bounds(0, 0, 0, 0)).status());
        assertEquals(RocketDiscoveryColumns.Status.VALID, RocketDiscoveryColumns.of(Integer.MIN_VALUE + 1, 8, bounds(0, 0, 0, 0)).status());
    }

    @Test void boundsThatMissTheOriginAcrossABorderAreValidButNotAdmissible() {
        // Blocks at relative x 5..6 from x = 15 span 20..21, margin 19..23: only column 1, not the origin's column 0.
        var outside = RocketDiscoveryColumns.of(15, 8, bounds(5, 6, 0, 0));
        assertTrue(outside.valid()); assertFalse(outside.containsOrigin()); assertFalse(outside.admissible());
        assertEquals(rectangle(1, 1, 0, 0), outside.columns());
        assertEquals(List.of(), outside.refreshSet());
        assertEquals(List.of(new ChunkPos(0, 0)), outside.releaseSet());
        // The same bounds from x = 8 keep the origin column inside the margin.
        assertTrue(RocketDiscoveryColumns.of(8, 8, bounds(5, 6, 0, 0)).admissible());
    }

    @Test void randomRegionsShareOneSetAndReleaseAddsOnlyTheOrigin() {
        var random = new Random(9_042L);
        int admissible = 0, outside = 0;
        for (int i = 0; i < 20_000; i++) {
            int originX = random.nextInt(4_096) - 2_048, originZ = random.nextInt(4_096) - 2_048;
            int minX = -random.nextInt(40), minZ = -random.nextInt(40);
            int maxX = minX + random.nextInt(60), maxZ = minZ + random.nextInt(60);
            if (random.nextInt(4) == 0) { minX += 20; maxX += 20; }
            var columns = RocketDiscoveryColumns.of(originX, originZ, bounds(minX, maxX, minZ, maxZ));
            if (!columns.valid()) { assertTrue(columns.refreshSet().isEmpty()); continue; }
            assertEquals(rectangle(columns.minColumnX(), columns.maxColumnX(), columns.minColumnZ(), columns.maxColumnZ()), columns.columns());
            assertTrue(columns.count() <= 16 && columns.count() == columns.columns().size());
            var release = new LinkedHashSet<>(columns.releaseSet());
            if (columns.admissible()) {
                admissible++;
                assertEquals(columns.columns(), columns.refreshSet(), "Readiness and refresh read different columns");
                var expected = new LinkedHashSet<>(columns.refreshSet()); expected.add(columns.origin());
                assertEquals(expected, release, "Release added more than the origin column");
            } else {
                outside++;
                assertEquals(List.of(), columns.refreshSet());
                assertEquals(java.util.Set.of(columns.origin()), release);
            }
        }
        assertTrue(admissible > 1_000 && outside > 100, "Random cases did not cover both kinds: " + admissible + "/" + outside);
    }

    @Test void theSnapshotFormUsesItsOriginAndBounds() {
        var iron = new RocketBlockState(ResourceLocation.tryParse("minecraft:iron_block"), Map.of());
        var snapshot = RocketStructureSnapshot.create(UUID.randomUUID(), ResourceLocation.tryParse("minecraft:overworld"),
                new RocketPosition(15, 80, 15), List.of(new RocketBlock(new RocketPosition(0, 0, 0), iron),
                        new RocketBlock(new RocketPosition(0, 1, 0), iron)),
                List.of(), new RocketStats(2, 100, 1_000, 2_000, 1, 0, 1, 0), 0);
        assertEquals(RocketDiscoveryColumns.of(15, 15, snapshot.bounds()).columns(), RocketDiscoveryColumns.of(snapshot).columns());
        assertEquals(4, RocketDiscoveryColumns.of(snapshot).refreshSet().size());
    }
}
