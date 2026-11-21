package io.github.sunthemoon.advancedrocketrycommunity.gametest;

import io.github.sunthemoon.advancedrocketrycommunity.ModIdentity;
import io.github.sunthemoon.advancedrocketrycommunity.atmosphere.scan.CellObservation;
import io.github.sunthemoon.advancedrocketrycommunity.atmosphere.scan.VolumePosition;
import io.github.sunthemoon.advancedrocketrycommunity.atmosphere.server.ServerLevelVolumeWorldView;
import io.github.sunthemoon.advancedrocketrycommunity.registry.ModBlocks;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestGenerator;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.TestFunction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/** Chemical motors transmit block light without changing collision, support or atmosphere sealing. */
@GameTestHolder(ModIdentity.MOD_ID)
@PrefixGameTestTemplate(false)
public final class MonoMotorOcclusionGameTests {
    private MonoMotorOcclusionGameTests() { }

    /** Opt-in stress companions run in separate, adjacent GameTest structures in each affected batch. */
    @GameTestGenerator
    public static List<TestFunction> neighbouringLightSources() {
        return LightTransmissionNeighbours.tests();
    }

    @GameTest(template = "empty", batch = "motor_occlusion", timeoutTicks = 100)
    public static void monoMotorsDoNotOccludeAndPassLightToTheCellBeneath(GameTestHelper helper) {
        assertNonOccludingColumns(helper, List.of(ModBlocks.ROCKET_MOTOR.get().defaultBlockState(),
                ModBlocks.ADVANCED_ROCKET_MOTOR.get().defaultBlockState()));
    }

    @GameTest(template = "empty", batch = "bipropellant_motor_occlusion", timeoutTicks = 100)
    public static void bipropellantMotorsDoNotOccludeAndPassLightToTheCellBeneath(GameTestHelper helper) {
        assertNonOccludingColumns(helper, List.of(ModBlocks.BIPROPELLANT_ROCKET_MOTOR.get().defaultBlockState(),
                ModBlocks.ADVANCED_BIPROPELLANT_ROCKET_MOTOR.get().defaultBlockState()));
    }

    /** Shared bounded fixture used by the chemical/nuclear motors and fuel tanks, including the opaque control. */
    static void assertNonOccludingColumns(GameTestHelper helper, List<BlockState> states) {
        if (states.isEmpty() || states.size() > 6) throw new IllegalArgumentException("Expected one to six block states");
        var driver = new GameTestTickDriver(helper);
        ServerLevel level = helper.getLevel();
        List<SealedLightColumn> columns = new ArrayList<>();
        for (int i = 0; i < states.size(); i++) {
            columns.add(new SealedLightColumn(helper, states.get(i), i, true));
        }
        columns.add(new SealedLightColumn(helper, Blocks.IRON_BLOCK.defaultBlockState(), states.size(), false));
        try {
            for (SealedLightColumn column : columns) {
                column.place(helper);
                assertProperties(helper, level, column);
            }
            SealedLightColumn.await(helper, driver, columns);
        } catch (RuntimeException | Error failure) {
            SealedLightColumn.clear(level, columns);
            throw failure;
        }
    }

    static BlockPos columnBase(int index) {
        return SealedLightColumn.base(index);
    }

    private static void assertProperties(GameTestHelper helper, ServerLevel level, SealedLightColumn column) {
        BlockPos tested = column.tested;
        BlockState state = level.getBlockState(tested);
        helper.assertTrue(state.equals(column.expectedState), "Fixture lost " + column.name());
        if (!column.transmits) {
            helper.assertTrue(state.canOcclude() && state.isSolidRender(level, tested)
                    && state.getLightBlock(level, tested) == 15, "Opaque control no longer blocks light");
            return;
        }
        helper.assertTrue(!state.canOcclude() && !state.isSolidRender(level, tested),
                column.name() + " still occludes as an opaque cube");
        helper.assertTrue(state.getLightBlock(level, tested) == 1 && !state.propagatesSkylightDown(level, tested),
                column.name() + " light attenuation changed: " + state.getLightBlock(level, tested));
        helper.assertTrue(state.isCollisionShapeFullBlock(level, tested) && state.isSolid() && state.blocksMotion()
                        && state.isFaceSturdy(level, tested, Direction.UP)
                        && state.isRedstoneConductor(level, tested) && state.isSuffocating(level, tested),
                column.name() + " collision or support properties changed");
        VolumePosition cell = new VolumePosition(tested.getX(), tested.getY(), tested.getZ());
        helper.assertTrue(new ServerLevelVolumeWorldView(level, true).observe(cell) == CellObservation.SEALED,
                column.name() + " no longer seals an atmosphere");
    }
}
