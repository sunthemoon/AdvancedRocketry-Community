package io.github.sunthemoon.advancedrocketrycommunity.gametest;

import static org.junit.jupiter.api.Assertions.*;

import io.github.sunthemoon.advancedrocketrycommunity.testsupport.MinecraftBootstrap;
import java.util.HashSet;
import java.util.List;
import net.minecraft.core.BlockPos;
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
}
