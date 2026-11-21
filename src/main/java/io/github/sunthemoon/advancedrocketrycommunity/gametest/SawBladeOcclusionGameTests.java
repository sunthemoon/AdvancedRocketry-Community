package io.github.sunthemoon.advancedrocketrycommunity.gametest;

import io.github.sunthemoon.advancedrocketrycommunity.ModIdentity;
import io.github.sunthemoon.advancedrocketrycommunity.atmosphere.scan.CellObservation;
import io.github.sunthemoon.advancedrocketrycommunity.atmosphere.scan.VolumePosition;
import io.github.sunthemoon.advancedrocketrycommunity.atmosphere.server.ServerLevelVolumeWorldView;
import io.github.sunthemoon.advancedrocketrycommunity.registry.ModBlocks;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/** R282: rendering/light transmission changes without changing collision, support, sealing or state identity. */
@GameTestHolder(ModIdentity.MOD_ID)
@PrefixGameTestTemplate(false)
public final class SawBladeOcclusionGameTests {
    private SawBladeOcclusionGameTests() { }

    @GameTest(template = "empty", batch = "saw_occlusion", timeoutTicks = 100)
    public static void sawDoesNotOccludeAndPassesLightWithoutChangingServerGeometry(GameTestHelper helper) {
        var driver = new GameTestTickDriver(helper);
        var columns = List.of(new SealedLightColumn(helper, ModBlocks.SAW_BLADE.get().defaultBlockState(), 0, true),
                new SealedLightColumn(helper, Blocks.IRON_BLOCK.defaultBlockState(), 1, false));
        try {
            for (SealedLightColumn column : columns) {
                column.place(helper);
                assertProperties(helper, column);
            }
            SealedLightColumn.await(helper, driver, columns);
        } catch (RuntimeException | Error failure) {
            SealedLightColumn.clear(helper.getLevel(), columns);
            throw failure;
        }
    }

    private static void assertProperties(GameTestHelper helper, SealedLightColumn column) {
        ServerLevel level = helper.getLevel();
        BlockPos tested = column.tested;
        var state = level.getBlockState(tested);
        boolean saw = column.transmits;
        helper.assertTrue(state.equals(column.expectedState), "Fixture lost " + column.name());
        helper.assertTrue(state.canOcclude() != saw && state.isSolidRender(level, tested) != saw
                && state.getLightBlock(level, tested) == (saw ? 1 : 15), "Occlusion/light attenuation differs");
        if (!saw) return;
        helper.assertTrue(state.getProperties().isEmpty() && !state.hasBlockEntity() && !state.isAir(),
                "Saw acquired state, a block entity or air semantics");
        helper.assertTrue(state.isCollisionShapeFullBlock(level, tested) && state.isSolid() && state.blocksMotion()
                && state.isFaceSturdy(level, tested, Direction.UP) && state.isRedstoneConductor(level, tested)
                && state.isSuffocating(level, tested) && !state.propagatesSkylightDown(level, tested),
                "Saw collision, support, motion, conductor or skylight properties changed");
        helper.assertTrue(new ServerLevelVolumeWorldView(level, true).observe(
                new VolumePosition(tested.getX(), tested.getY(), tested.getZ())) == CellObservation.SEALED,
                "Saw no longer seals an atmosphere");
    }
}
