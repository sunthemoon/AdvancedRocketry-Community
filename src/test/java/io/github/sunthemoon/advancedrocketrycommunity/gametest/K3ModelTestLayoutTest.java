package io.github.sunthemoon.advancedrocketrycommunity.gametest;

import static org.junit.jupiter.api.Assertions.*;

import io.github.sunthemoon.advancedrocketrycommunity.machine.multiblock.pattern.MultiblockPatternJsonCodec;
import io.github.sunthemoon.advancedrocketrycommunity.machine.multiblock.pattern.PatternPosition;
import io.github.sunthemoon.advancedrocketrycommunity.machine.research.ResearchPatterns;
import io.github.sunthemoon.advancedrocketrycommunity.machine.solar.SolarArrayLayout;
import io.github.sunthemoon.advancedrocketrycommunity.testsupport.MinecraftBootstrap;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
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
        int expected = switch (layout) { case ANALYSER -> 18; case OBSERVATORY -> 325; case SOLAR -> 249; case BEACON -> 105; };
        for (int index = 0; index < 4; index++) {
            var reach = layout.reach(index, pattern.cells(), pattern.anchor());
            assertEquals(100, layout.origin(index).getY());
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
    void usesTheSmallestSquareRadiusAtTheExistingHeight(K3ModelTestLayout layout) throws Exception {
        int expectedRadius = switch (layout) { case ANALYSER, BEACON -> 0; case OBSERVATORY -> 1; case SOLAR -> 2; };
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
        var candidates = new ArrayList<Set<BlockPos>>();
        // Enumerate every possible controller placement in the smaller square, without moving it vertically.
        for (int x = -minX; x < smallerWidth - maxX; x++) { for (int z = -minZ; z < smallerWidth - maxZ; z++) {
            var candidate = new HashSet<BlockPos>();
            for (var offset : offsets) { candidate.add(offset.offset(x, 0, z)); }
            candidates.add(candidate);
        } }
        boolean[][] apart = new boolean[candidates.size()][candidates.size()];
        for (int a = 0; a < candidates.size(); a++) { for (int b = a + 1; b < candidates.size(); b++) {
            apart[a][b] = Collections.disjoint(candidates.get(a), candidates.get(b));
        } }
        for (int a = 0; a < candidates.size(); a++) { for (int b = a + 1; b < candidates.size(); b++) {
            if (!apart[a][b]) { continue; }
            for (int c = b + 1; c < candidates.size(); c++) {
                if (!apart[a][c] || !apart[b][c]) { continue; }
                for (int d = c + 1; d < candidates.size(); d++) {
                    assertFalse(apart[a][d] && apart[b][d] && apart[c][d], "Four facings fit a smaller radius");
                }
            }
        } }
    }

    @Test void keepsTheFourOriginalRowsAndTheirFullFringesApart() {
        for (var a : K3ModelTestLayout.values()) {
            assertEquals(Math.floorDiv(-2_360_000 - a.ordinal() * 1000, 16) * 16, a.center().getZ());
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
