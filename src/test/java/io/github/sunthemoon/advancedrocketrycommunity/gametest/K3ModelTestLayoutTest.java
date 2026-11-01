package io.github.sunthemoon.advancedrocketrycommunity.gametest;

import static org.junit.jupiter.api.Assertions.*;

import io.github.sunthemoon.advancedrocketrycommunity.datagen.V180C16cMachineData;
import io.github.sunthemoon.advancedrocketrycommunity.machine.multiblock.pattern.MultiblockPatternJsonCodec;
import io.github.sunthemoon.advancedrocketrycommunity.machine.multiblock.pattern.PatternPosition;
import io.github.sunthemoon.advancedrocketrycommunity.machine.research.ResearchPatterns;
import io.github.sunthemoon.advancedrocketrycommunity.machine.solar.SolarArrayLayout;
import io.github.sunthemoon.advancedrocketrycommunity.testsupport.MinecraftBootstrap;
import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Objects;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ChunkPos;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

class K3ModelTestLayoutTest {
    @BeforeAll static void bootstrap() { MinecraftBootstrap.initialize(); }

    private record Pattern(Collection<PatternPosition> cells, PatternPosition anchor) { }

    private static Pattern pattern(K3ModelTestLayout layout) throws Exception {
        if (layout == K3ModelTestLayout.ETCHER) {
            var definition = MultiblockPatternJsonCodec.decode(V180C16cMachineData.pattern("precision_laser_etcher")
                    .toString().getBytes(StandardCharsets.UTF_8));
            return new Pattern(definition.cells().keySet(), definition.controllerAnchor());
        }
        if (layout == K3ModelTestLayout.SOLAR) {
            return new Pattern(SolarArrayLayout.cells().stream().map(SolarArrayLayout.Cell::local).toList(), SolarArrayLayout.ANCHOR);
        }
        if (layout == K3ModelTestLayout.BEACON) {
            try (var input = K3ModelTestLayoutTest.class.getResourceAsStream(
                    "/data/advancedrocketrycommunity/machine_patterns/planetary_beacon.json")) {
                var definition = MultiblockPatternJsonCodec.decode(Objects.requireNonNull(input).readAllBytes());
                return new Pattern(definition.cells().keySet(), definition.controllerAnchor());
            }
        }
        var definition = ResearchPatterns.definition(layout == K3ModelTestLayout.ANALYSER);
        return new Pattern(definition.cells().keySet(), definition.controllerAnchor());
    }

    @ParameterizedTest @EnumSource(K3ModelTestLayout.class)
    void everyFacingAndMirrorIsTicketedAndApartFromEveryOtherFacing(K3ModelTestLayout layout) throws Exception {
        var pattern = pattern(layout);
        var center = new ChunkPos(layout.center());
        var claimed = new HashSet<BlockPos>();
        int expected = switch (layout) {
            case ANALYSER -> 18; case OBSERVATORY -> 325; case SOLAR -> 249; case BEACON -> 105; case ETCHER -> 63;
        };
        for (int index = 0; index < 4; index++) {
            var reach = layout.reach(index, pattern.cells(), pattern.anchor());
            int height = switch (layout) { case OBSERVATORY -> index * 8; case SOLAR -> index * 4; default -> 0; };
            assertEquals(100 + height, layout.origin(index).getY());
            assertEquals(expected, reach.size(), "All optional/air cells and transforms must be included");
            assertTrue(reach.contains(layout.origin(index)));
            for (var position : reach) {
                var chunk = new ChunkPos(position);
                assertTrue(Math.abs(chunk.x - center.x) <= layout.radius() && Math.abs(chunk.z - center.z) <= layout.radius(),
                        () -> layout + " unticketed position " + position);
            }
            assertTrue(Collections.disjoint(claimed, reach), "Validation can see another facing's cells");
            claimed.addAll(reach);
        }
    }

    @ParameterizedTest @EnumSource(K3ModelTestLayout.class)
    void usesTheSmallestSquareRadiusEvenWithVerticalSeparation(K3ModelTestLayout layout) throws Exception {
        int expectedRadius = layout == K3ModelTestLayout.SOLAR ? 1 : 0;
        assertEquals(expectedRadius, layout.radius());
        if (layout.radius() == 0) { return; }
        var pattern = pattern(layout);
        var origin = layout.origin(0);
        var offsets = layout.reach(0, pattern.cells(), pattern.anchor()).stream().map(p -> p.subtract(origin)).toList();
        int smallerWidth = (2 * (layout.radius() - 1) + 1) * 16;
        int minX = offsets.stream().mapToInt(BlockPos::getX).min().orElseThrow();
        int maxX = offsets.stream().mapToInt(BlockPos::getX).max().orElseThrow();
        int minZ = offsets.stream().mapToInt(BlockPos::getZ).min().orElseThrow();
        int maxZ = offsets.stream().mapToInt(BlockPos::getZ).max().orElseThrow();
        // Even one solar controller's complete rotated reach is wider than a radius-0 chunk.
        assertTrue(maxX - minX + 1 > smallerWidth && maxZ - minZ + 1 > smallerWidth);
    }

    @Test void keepsTheFourOriginalRowsAndTheEtcherRowAndTheirFullFringesApart() {
        for (var a : K3ModelTestLayout.values()) {
            if (a == K3ModelTestLayout.ETCHER) {
                assertEquals(new BlockPos(-2_440_000, 100, -2_440_000), a.center());
            } else {
                assertEquals(Math.floorDiv(-2_360_000 - a.ordinal() * 1000, 16) * 16, a.center().getZ());
            }
            for (var b : K3ModelTestLayout.values()) {
                if (a == b) { continue; }
                assertTrue(Math.abs(new ChunkPos(a.center()).z - new ChunkPos(b.center()).z) > a.radius() + b.radius() + 4);
            }
        }
    }

    @Test void rejectsIndicesOutsideTheFourFacings() {
        for (var layout : K3ModelTestLayout.values()) {
            assertThrows(IllegalArgumentException.class, () -> layout.origin(-1));
            assertThrows(IllegalArgumentException.class, () -> layout.origin(4));
        }
    }
}
