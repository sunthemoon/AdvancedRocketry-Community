package io.github.sunthemoon.advancedrocketrycommunity.gametest;

import static org.junit.jupiter.api.Assertions.*;

import io.github.sunthemoon.advancedrocketrycommunity.testsupport.MinecraftBootstrap;
import java.util.HashSet;
import java.util.List;
import java.util.HashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class MonoMotorOcclusionFixtureTest {
    @BeforeAll static void bootstrap() { MinecraftBootstrap.initialize(); }

    @Test void sixStatesAndControlStayInsideTheEmptyTemplatesActualClearedVolume() {
        var centers = new HashSet<BlockPos>();
        for (int index = 0; index <= 6; index++) {
            BlockPos base = MonoMotorOcclusionGameTests.columnBase(index);
            assertTrue(centers.add(base));
            for (BlockPos cell : List.of(base, base.below(), base.north(), base.south(), base.east(), base.west(),
                    base.above(), base.above(2), base.above().west(), base.above(2).west())) {
                // StructureUtils.clearSpaceForStructure: minX-2, minY-3, minZ-3; maxX+3, maxY+20, maxZ+3.
                assertTrue(cell.getX() >= -2 && cell.getX() <= 3, cell.toString());
                assertTrue(cell.getZ() >= -3 && cell.getZ() <= 3, cell.toString());
                assertTrue(cell.getY() >= -3 && cell.getY() <= 20, cell.toString());
            }
        }
        for (BlockPos a : centers) for (BlockPos b : centers) {
            if (!a.equals(b)) assertTrue(a.distManhattan(b) >= 2, "Columns must not overwrite another's air cell");
        }
    }

    @Test void originalTwoStateFixturesKeepTheirExactColumns() {
        assertEquals(new BlockPos(2, 2, -2), MonoMotorOcclusionGameTests.columnBase(0));
        assertEquals(new BlockPos(2, 2, 0), MonoMotorOcclusionGameTests.columnBase(1));
        assertEquals(new BlockPos(2, 2, 2), MonoMotorOcclusionGameTests.columnBase(2));
    }

    @Test void fixtureRejectsUnboundedColumnIndices() {
        assertThrows(IllegalArgumentException.class, () -> MonoMotorOcclusionGameTests.columnBase(-1));
        assertThrows(IllegalArgumentException.class, () -> MonoMotorOcclusionGameTests.columnBase(7));
    }

    @Test void completeEnclosuresFitTheClearedVolumeAndShareOnlyIdenticalStone() {
        var all = new HashMap<BlockPos, BlockState>();
        for (int index = 0; index <= 6; index++) {
            var cells = SealedLightColumn.cells(SealedLightColumn.base(index), Blocks.IRON_BLOCK.defaultBlockState());
            assertEquals(60, cells.size());
            cells.forEach((pos, state) -> {
                assertTrue(pos.getX() >= -2 && pos.getX() <= 3, pos.toString());
                assertTrue(pos.getZ() >= -3 && pos.getZ() <= 3, pos.toString());
                assertTrue(pos.getY() >= -3 && pos.getY() <= 20, pos.toString());
                BlockState previous = all.putIfAbsent(pos, state);
                if (previous != null) {
                    assertEquals(Blocks.STONE.defaultBlockState(), previous, pos.toString());
                    assertEquals(previous, state, pos.toString());
                }
                if (!state.is(Blocks.STONE)) {
                    for (Direction face : Direction.values()) {
                        assertTrue(cells.containsKey(pos.relative(face)), "Open enclosure face: " + pos + " " + face);
                    }
                }
            });
        }
    }

    @Test void opaqueControlsHaveStoneOnEveryOtherFaceAndProbesKeepTheirTwoStepLightPath() {
        for (int index = 0; index <= 6; index++) {
            BlockPos base = SealedLightColumn.base(index);
            var cells = SealedLightColumn.cells(base, Blocks.IRON_BLOCK.defaultBlockState());
            assertEquals(Blocks.AIR.defaultBlockState(), cells.get(base));
            assertEquals(Blocks.IRON_BLOCK.defaultBlockState(), cells.get(base.above()));
            assertEquals(Blocks.GLOWSTONE.defaultBlockState(), cells.get(base.above(2)));
            assertEquals(Blocks.AIR.defaultBlockState(), cells.get(base.above().west()));
            assertEquals(Blocks.AIR.defaultBlockState(), cells.get(base.above(2).west()));
            for (Direction face : Direction.values()) {
                if (face != Direction.UP) assertEquals(Blocks.STONE.defaultBlockState(), cells.get(base.relative(face)));
            }
        }
    }
}
