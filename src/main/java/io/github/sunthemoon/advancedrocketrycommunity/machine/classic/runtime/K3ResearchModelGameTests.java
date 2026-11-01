package io.github.sunthemoon.advancedrocketrycommunity.machine.classic.runtime;

import io.github.sunthemoon.advancedrocketrycommunity.ModIdentity;
import io.github.sunthemoon.advancedrocketrycommunity.gametest.K3ModelTestLayout;
import io.github.sunthemoon.advancedrocketrycommunity.gametest.K3ModelTestRegion;
import io.github.sunthemoon.advancedrocketrycommunity.machine.multiblock.pattern.PatternMatcher;
import io.github.sunthemoon.advancedrocketrycommunity.machine.research.ResearchPatterns;
import java.util.UUID;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.*;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.gametest.*;

@GameTestHolder(ModIdentity.MOD_ID) @PrefixGameTestTemplate(false)
public final class K3ResearchModelGameTests {
    private K3ResearchModelGameTests() { }
    @GameTest(template = "empty", batch = "w236_analyser_model", timeoutTicks = 40)
    public static void analyserProjectsOnlyFormedStateInEveryFacing(GameTestHelper helper) { exercise(helper, true); }
    @GameTest(template = "empty", batch = "w236_observatory_model", timeoutTicks = 40)
    public static void observatoryProjectsOnlyFormedStateInEveryFacing(GameTestHelper helper) { exercise(helper, false); }
    private static void exercise(GameTestHelper helper, boolean processor) {
        var layout = processor ? K3ModelTestLayout.ANALYSER : K3ModelTestLayout.OBSERVATORY;
        try (var region = new K3ModelTestRegion(helper, layout.center(), layout.radius())) {
            var pattern = ResearchPatterns.definition(processor);
            layout.assertIsolated(helper, region, pattern.cells().keySet(), pattern.controllerAnchor());
            int index = 0;
            for (Direction facing : Direction.Plane.HORIZONTAL) {
                var origin = layout.origin(index++);
                try (var f = new ResearchFixture(helper.getLevel(), origin, processor, facing, UUID.randomUUID())) {
                    helper.assertTrue(f.controller.formed(), "K3 research fixture did not form: " + f.controller.reason());
                    K3ModelTestRegion.assertProjection(helper, f.controller, true);
                    ClassicModelProjectionGameTests.exerciseRotation(helper, f.controller, f::revalidate);
                    helper.assertTrue(f.controller.getRenderBoundingBox().equals(ClassicModelProjection.bounds(f.controller.definition().kind(), facing, origin)),
                            "K3 research render bounds differ");
                    var part = f.world(f.pattern.cells().entrySet().stream()
                            .filter(e -> e.getValue() instanceof PatternMatcher.ExactBlock).findFirst().orElseThrow().getKey());
                    var state = f.level.getBlockState(part); f.level.setBlock(part, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL); f.revalidate();
                    helper.assertTrue(!f.controller.formed(), "K3 research remained formed after break");
                    K3ModelTestRegion.assertProjection(helper, f.controller, false);
                    f.level.setBlock(part, state, Block.UPDATE_ALL); f.revalidate();
                    helper.assertTrue(f.controller.formed(), "K3 research did not reform");
                    K3ModelTestRegion.assertProjection(helper, f.controller, true);
                }
            }
        }
        helper.succeed();
    }
}
