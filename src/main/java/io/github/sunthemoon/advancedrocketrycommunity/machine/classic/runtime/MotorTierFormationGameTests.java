package io.github.sunthemoon.advancedrocketrycommunity.machine.classic.runtime;

import io.github.sunthemoon.advancedrocketrycommunity.ModIdentity;
import io.github.sunthemoon.advancedrocketrycommunity.classiccomponent.MotorContent;
import io.github.sunthemoon.advancedrocketrycommunity.classiccomponent.MotorDefinition;
import io.github.sunthemoon.advancedrocketrycommunity.machine.multiblock.pattern.*;
import io.github.sunthemoon.advancedrocketrycommunity.material.MaterialContent;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/**
 * C16a-07b: ADR-064 §1.1 and §4.1 let every motor tier fill a motor cell. Each machine whose pattern has a motor cell
 * is built once per tier and once with a copper coil there, through its existing fixture. The Level's production
 * manager forms it against the datapack pattern and the registered block tags, as for a placed controller.
 */
@GameTestHolder(ModIdentity.MOD_ID)
@PrefixGameTestTemplate(false)
public final class MotorTierFormationGameTests {
    private static final String NOT_A_MOTOR = "copper_coil";

    @GameTest(template = "empty", batch = "w41_motor_tiers", timeoutTicks = 30)
    public static void latheFormsWithEveryMotorTierAndRefusesACoil(GameTestHelper helper) {
        tiers(helper, "lathe", (motor, check) -> {
            try (var fixture = new ClassicLatheGameTests.Fixture(helper, Direction.NORTH, motor)) { check.accept(fixture.controller, List.of(fixture.motor)); }
        });
    }
    @GameTest(template = "empty", batch = "w41_motor_tiers", timeoutTicks = 30)
    public static void cuttingMachineFormsWithEveryMotorTierAndRefusesACoil(GameTestHelper helper) {
        tiers(helper, "cutting_machine", (motor, check) -> {
            try (var fixture = new ArcCuttingGameTests.Fixture(helper, false, Direction.NORTH, motor)) {
                check.accept(fixture.controller, motorCells(fixture.pattern, fixture::world));
            }
        });
    }
    @GameTest(template = "empty", batch = "w41_motor_tiers", timeoutTicks = 30)
    public static void chemicalReactorFormsWithEveryMotorTierAndRefusesACoil(GameTestHelper helper) { c16c(helper, "chemical_reactor"); }
    @GameTest(template = "empty", batch = "w41_motor_tiers", timeoutTicks = 30)
    public static void etcherFormsWithEveryMotorTierAndRefusesACoil(GameTestHelper helper) { c16c(helper, "precision_laser_etcher"); }
    @GameTest(template = "empty", batch = "w41_motor_tiers", timeoutTicks = 30)
    public static void centrifugeFormsWithEveryMotorTierAndRefusesACoil(GameTestHelper helper) { c16c(helper, "centrifuge"); }
    @GameTest(template = "empty", batch = "w41_motor_tiers", timeoutTicks = 30)
    public static void observatoryFormsWithEveryMotorTierAndRefusesACoil(GameTestHelper helper) {
        tiers(helper, "observatory", (motor, check) -> {
            try (var fixture = ResearchGameTests.fixture(helper, false, Direction.NORTH, UUID.randomUUID(), 0, motor)) {
                check.accept(fixture.controller, motorCells(fixture.pattern, fixture::world));
            }
        });
    }
    private static void c16c(GameTestHelper helper, String kind) {
        tiers(helper, kind, (motor, check) -> {
            try (var fixture = new C16cMachineFixture(helper, kind, Direction.NORTH, motor)) {
                check.accept(fixture.controller, motorCells(fixture.pattern, fixture::world));
            }
        });
    }

    /** Builds one machine with {@code motor} in every motor cell, checks the live controller, then removes the machine. */
    @FunctionalInterface private interface Build { void run(BlockState motor, Check check); }
    @FunctionalInterface private interface Check { void accept(ClassicControllerBlockEntity controller, List<BlockPos> motorCells); }

    /** Four fresh builds, one per tier, must form; a fifth with a copper coil must be refused at the motor cell. */
    private static void tiers(GameTestHelper helper, String machine, Build build) {
        for (MotorDefinition tier : MotorDefinition.values()) {
            BlockState motor = MotorContent.block(tier).get().defaultBlockState();
            build.run(motor, (controller, cells) -> {
                placed(helper, machine, tier.id(), motor, cells);
                helper.assertTrue(controller.formed() && controller.firstDiagnostic().isEmpty(),
                        machine + " did not form with " + tier.id() + ": " + controller.reason() + " " + controller.firstDiagnostic());
            });
        }
        // The motor's own crafting part, and a member of the sibling coils tag that the arc furnace accepts.
        BlockState coil = MaterialContent.block(NOT_A_MOTOR).defaultBlockState();
        build.run(coil, (controller, cells) -> {
            placed(helper, machine, NOT_A_MOTOR, coil, cells);
            helper.assertTrue(!controller.formed() && controller.reason() == ClassicWait.UNFORMED,
                    machine + " formed with a " + NOT_A_MOTOR + " in its motor cell");
            var diagnostic = controller.firstDiagnostic().orElse(null);
            helper.assertTrue(diagnostic != null && diagnostic.reason() == PatternDiagnosticReason.BLOCK_MISMATCH
                    && diagnostic.expected().equals("tag=" + MotorDefinition.TAG_ID)
                    && diagnostic.actualBlockId().equals(Optional.of(ModIdentity.id(NOT_A_MOTOR).toString()))
                    && cells.contains(new BlockPos(diagnostic.worldPosition().x(), diagnostic.worldPosition().y(), diagnostic.worldPosition().z())),
                    machine + " was refused somewhere other than its motor cell: " + diagnostic);
        });
        helper.succeed();
    }
    private static void placed(GameTestHelper helper, String machine, String block, BlockState state, List<BlockPos> cells) {
        helper.assertTrue(!cells.isEmpty() && cells.stream().allMatch(cell -> helper.getLevel().getBlockState(cell).is(state.getBlock())),
                machine + " fixture did not put " + block + " in every motor cell");
    }
    private static List<BlockPos> motorCells(MultiblockPatternDefinition pattern, Function<PatternPosition, BlockPos> world) {
        return pattern.cells().entrySet().stream()
                .filter(cell -> cell.getValue() instanceof PatternMatcher.BlockTag tag && tag.tagId().equals(MotorDefinition.TAG_ID))
                .map(cell -> world.apply(cell.getKey())).toList();
    }
    private MotorTierFormationGameTests() { }
}
