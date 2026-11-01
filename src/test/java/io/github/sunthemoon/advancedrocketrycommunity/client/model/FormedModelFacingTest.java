package io.github.sunthemoon.advancedrocketrycommunity.client.model;

import static org.junit.jupiter.api.Assertions.*;

import io.github.sunthemoon.advancedrocketrycommunity.machine.classic.runtime.ClassicModelProjection;
import io.github.sunthemoon.advancedrocketrycommunity.machine.classic.runtime.K4ModelProjection;
import io.github.sunthemoon.advancedrocketrycommunity.machine.precision.PrecisionAssemblerBlock;
import io.github.sunthemoon.advancedrocketrycommunity.machine.rolling.RollingMachineBlock;
import io.github.sunthemoon.advancedrocketrycommunity.testsupport.MinecraftBootstrap;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockBehaviour;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/** Client-side half of the native rotation regressions: test the renderer's actual draw/orientation decision. */
class FormedModelFacingTest {
    @BeforeAll static void bootstrap() { MinecraftBootstrap.initialize(); }

    @Test void clearedRotationPacketWithholdsHullAndMatchingFacingRestoresItsOrientation() {
        for (var fixture : new ObjModelFixture[] {ObjModelFixture.ROLLING_MACHINE, ObjModelFixture.PRECISION_ASSEMBLER}) {
            var block = fixture == ObjModelFixture.ROLLING_MACHINE
                    ? new RollingMachineBlock(BlockBehaviour.Properties.of())
                    : new PrecisionAssemblerBlock(BlockBehaviour.Properties.of());
            for (Direction facing : Direction.Plane.HORIZONTAL) {
                var original = block.defaultBlockState().setValue(RollingMachineBlock.FACING, facing);
                var projection = new K4ModelProjection();
                var formed = new CompoundTag(); ClassicModelProjection.encode(formed, true);
                projection.read(formed);
                assertEquals(facing, ClassicObjModelRenderer.modelFacing(fixture, original, projection.formed()));
                var rotated = original.rotate(Rotation.CLOCKWISE_90);
                assertNotEquals(facing, rotated.getValue(RollingMachineBlock.FACING));
                // These are the cleared/matching tags asserted on real controllers after revalidation in GameTest.
                projection.read(new CompoundTag());
                assertNull(ClassicObjModelRenderer.modelFacing(fixture, rotated, projection.formed()));
                projection.read(null);
                assertNull(ClassicObjModelRenderer.modelFacing(fixture, rotated, projection.formed()));
                projection.read(formed);
                assertEquals(facing, ClassicObjModelRenderer.modelFacing(fixture, original, projection.formed()));
            }
        }
    }

    @Test void anchorUsesCanonicalNorthAndOtherModelsRequireHorizontalStateAndProjection() {
        var stone = Blocks.STONE.defaultBlockState();
        assertEquals(Direction.NORTH, ClassicObjModelRenderer.modelFacing(ObjModelFixture.ELEVATOR_ANCHOR, stone, true));
        assertNull(ClassicObjModelRenderer.modelFacing(ObjModelFixture.ELEVATOR_ANCHOR, stone, false));
        assertNull(ClassicObjModelRenderer.modelFacing(ObjModelFixture.RAILGUN, stone, true));
    }

    @Test void etcherClearedProjectionCannotDrawAtTheNewFacing() {
        var block = new io.github.sunthemoon.advancedrocketrycommunity.machine.classic.runtime.ClassicControllerBlock(BlockBehaviour.Properties.of());
        var fixture = ObjModelFixture.PRECISION_LASER_ETCHER;
        for (Direction facing : Direction.Plane.HORIZONTAL) {
            var state = block.defaultBlockState().setValue(RollingMachineBlock.FACING, facing);
            assertEquals(facing, ClassicObjModelRenderer.modelFacing(fixture, state, true));
            assertNull(ClassicObjModelRenderer.modelFacing(fixture, state.rotate(Rotation.CLOCKWISE_90), false));
        }
    }
}
