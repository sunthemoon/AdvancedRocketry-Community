package io.github.sunthemoon.advancedrocketrycommunity.machine.solar;

import io.github.sunthemoon.advancedrocketrycommunity.ModIdentity;
import io.github.sunthemoon.advancedrocketrycommunity.gametest.K3ModelTestRegion;
import io.github.sunthemoon.advancedrocketrycommunity.gametest.FormedModelRotationProbe;
import io.github.sunthemoon.advancedrocketrycommunity.machine.classic.runtime.ClassicModelProjection;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.gametest.*;

@GameTestHolder(ModIdentity.MOD_ID) @PrefixGameTestTemplate(false)
public final class K3SolarModelGameTests {
    private K3SolarModelGameTests() { }
    @GameTest(template = "empty", batch = "w236_solar_model", timeoutTicks = 40)
    public static void arrayProjectsOnlyFormationAcrossBreakAndReformInEveryFacing(GameTestHelper helper) {
        int index = 0;
        for (Direction facing : Direction.Plane.HORIZONTAL) {
            var origin = new BlockPos(-2_360_000 + index++ * 128, 100, -2_362_000);
            try (var region = new K3ModelTestRegion(helper, origin);
                 var f = new SolarArrayFixture(helper.getLevel(), origin)) {
                f.install(origin, 63, facing);
                helper.assertTrue(f.array.structure() == SolarArrayRules.Structure.FORMED, "K3 array did not form");
                K3ModelTestRegion.assertProjection(helper, f.array, true);
                var original = FormedModelRotationProbe.rotate(helper, f.array);
                FormedModelRotationProbe.assertPresentation(helper, f.array, false);
                f.array.revalidate(f.level);
                FormedModelRotationProbe.assertPresentation(helper, f.array, false);
                FormedModelRotationProbe.set(helper, f.array, original);
                f.array.revalidate(f.level);
                FormedModelRotationProbe.assertPresentation(helper, f.array, true);
                helper.assertTrue(f.array.getRenderBoundingBox().equals(ClassicModelProjection.bounds(ModIdentity.id("solar_array"), facing, origin)),
                        "K3 solar render bounds differ");
                var transform = ClassicModelProjection.transform(facing);
                var offset = transform.applyOffset(new io.github.sunthemoon.advancedrocketrycommunity.machine.multiblock.pattern.PatternPosition(1, 0, 0));
                var plug = origin.offset(offset.x(), offset.y(), offset.z()); var state = f.level.getBlockState(plug);
                f.set(plug, Blocks.AIR.defaultBlockState()); f.array.revalidate(f.level);
                helper.assertTrue(f.array.structure() != SolarArrayRules.Structure.FORMED, "K3 array remained formed");
                K3ModelTestRegion.assertProjection(helper, f.array, false);
                f.set(plug, state); f.array.revalidate(f.level);
                helper.assertTrue(f.array.structure() == SolarArrayRules.Structure.FORMED, "K3 array did not reform");
                K3ModelTestRegion.assertProjection(helper, f.array, true);
            }
        }
        helper.succeed();
    }
}
