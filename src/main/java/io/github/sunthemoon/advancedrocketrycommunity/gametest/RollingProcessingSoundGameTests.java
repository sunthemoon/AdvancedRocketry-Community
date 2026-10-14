package io.github.sunthemoon.advancedrocketrycommunity.gametest;

import static io.github.sunthemoon.advancedrocketrycommunity.gametest.RollingMachineGameTestFixtures.*;

import io.github.sunthemoon.advancedrocketrycommunity.ModIdentity;
import io.github.sunthemoon.advancedrocketrycommunity.machine.multiblock.lifecycle.MultiblockFormationState;
import io.github.sunthemoon.advancedrocketrycommunity.machine.process.ProcessMachineState;
import io.github.sunthemoon.advancedrocketrycommunity.machine.rolling.RollingMachineBlockEntity;
import io.github.sunthemoon.advancedrocketrycommunity.machine.rolling.RollingMachineRuntime;
import io.github.sunthemoon.advancedrocketrycommunity.sound.ClassicSound;
import io.github.sunthemoon.advancedrocketrycommunity.sound.ProcessingFlagCodec;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.material.Fluids;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.IFluidHandler;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/** One pre-registered schedule; all processing runs through the production server tick manager. */
@GameTestHolder(ModIdentity.MOD_ID)
@PrefixGameTestTemplate(false)
public final class RollingProcessingSoundGameTests {
    private RollingProcessingSoundGameTests() { }

    @GameTest(template = "rocket_test", batch = "w42_rolling", timeoutTicks = 180)
    public static void realRollingRecipeProjectsOnlySuccessfulProgression(GameTestHelper helper) {
        placeStructure(helper);
        int[] step = {0};
        BlockPos casing = new BlockPos(2, 3, 4);
        helper.onEachTick(() -> {
            var controller = controller(helper);
            switch (step[0]) {
                case 0 -> {
                    if (controller.formationState() != MultiblockFormationState.FORMED) { return; }
                    assertProjection(helper, controller, false);
                    var input = port(helper, ITEM_INPUT).getCapability(ForgeCapabilities.ITEM_HANDLER).resolve().orElseThrow();
                    var fluid = port(helper, FLUID_INPUT).getCapability(ForgeCapabilities.FLUID_HANDLER).resolve().orElseThrow();
                    var energy = port(helper, ENERGY_INPUT).getCapability(ForgeCapabilities.ENERGY).resolve().orElseThrow();
                    helper.assertTrue(input.insertItem(0, new ItemStack(Items.IRON_INGOT, 2), false).isEmpty(), "Rolling input refused");
                    helper.assertTrue(fluid.fill(new FluidStack(Fluids.WATER, 100), IFluidHandler.FluidAction.EXECUTE) == 100, "Rolling fluid refused");
                    helper.assertTrue(energy.receiveEnergy(40, false) == 40, "Rolling seed energy refused");
                    step[0]++;
                }
                case 1 -> {
                    if (!controller.processingActive()) { return; }
                    assertProjection(helper, controller, true); step[0]++;
                }
                case 2 -> {
                    if (controller.processState() != ProcessMachineState.WAITING_ENERGY) { return; }
                    assertProjection(helper, controller, false);
                    replaceOutput(helper, new ItemStack(Items.DIRT, 64));
                    var energy = port(helper, ENERGY_INPUT).getCapability(ForgeCapabilities.ENERGY).resolve().orElseThrow();
                    for (int i = 0; i < 2; i++) {
                        helper.assertTrue(energy.receiveEnergy(1_000, false) == 1_000, "Rolling refill refused");
                    }
                    wake(helper); step[0]++;
                }
                case 3 -> {
                    if (controller.processState() != ProcessMachineState.WAITING_OUTPUT) { return; }
                    assertProjection(helper, controller, false);
                    replaceOutput(helper, ItemStack.EMPTY); wake(helper); step[0]++;
                }
                case 4 -> {
                    if (!controller.processingActive()) { return; }
                    assertProjection(helper, controller, true);
                    helper.setBlock(casing, Blocks.AIR); step[0]++;
                }
                case 5 -> {
                    if (controller.formationState() == MultiblockFormationState.FORMED) { return; }
                    assertProjection(helper, controller, false);
                    helper.setBlock(casing, io.github.sunthemoon.advancedrocketrycommunity.registry.ModBlocks.MACHINE_CASING.get());
                    step[0]++;
                }
                case 6 -> {
                    if (!controller.processingActive()) { return; }
                    assertProjection(helper, controller, true);
                    var saved = controller.saveWithFullMetadata();
                    helper.assertTrue(!saved.contains("active"), "Processing flag entered disk persistence");
                    controller.load(saved);
                    assertProjection(helper, controller, false);
                    RollingMachineRuntime.observeController(helper.getLevel(), controller); step[0]++;
                }
                case 7 -> {
                    if (!controller.processingActive()) { return; }
                    assertProjection(helper, controller, true); step[0]++;
                }
                case 8 -> {
                    var output = port(helper, ITEM_OUTPUT).getCapability(ForgeCapabilities.ITEM_HANDLER).resolve().orElseThrow().getStackInSlot(0);
                    if (output.isEmpty()) { return; }
                    helper.assertTrue(output.is(Items.IRON_BARS) && output.getCount() == 8 && controller.processProgress().isEmpty(),
                            "Real rolling recipe did not complete exactly once");
                    assertProjection(helper, controller, false);
                    helper.succeed();
                }
                default -> throw new IllegalStateException("Unknown sound test step");
            }
        });
    }

    private static void wake(GameTestHelper helper) {
        RollingMachineRuntime.markProcessReady(helper.getLevel(), helper.absolutePos(CONTROLLER));
    }

    private static void replaceOutput(GameTestHelper helper, ItemStack stack) {
        var output = port(helper, ITEM_OUTPUT);
        var saved = output.saveWithFullMetadata();
        saved.getCompound("arce_rolling_port").put("item", stack.isEmpty() ? new CompoundTag() : stack.save(new CompoundTag()));
        output.load(saved);
    }

    private static void assertProjection(GameTestHelper helper, RollingMachineBlockEntity controller, boolean active) {
        helper.assertTrue(controller.processingActive() == active && ProcessingFlagCodec.decode(controller.getUpdateTag()) == active,
                "Rolling projection differs from expected processing state: " + controller.processState());
        helper.assertTrue(controller.activeProcessingSound() == (active ? ClassicSound.ROLLING_MACHINE : null), "Rolling sound mapping differs");
        helper.assertTrue(controller.getUpdateTag().getAllKeys().equals(Set.of("active")), "Rolling update tag exposed saved roots");
        var packet = controller.getUpdatePacket();
        helper.assertTrue(packet.getPos().equals(controller.getBlockPos()) && packet.getTag().equals(controller.getUpdateTag()),
                "Rolling update packet differs from update tag");
        var replica = new RollingMachineBlockEntity(controller.getBlockPos(), controller.getBlockState());
        var saved = replica.saveWithFullMetadata(); replica.onDataPacket(null, packet);
        helper.assertTrue(replica.processingActive() == active && replica.saveWithFullMetadata().equals(saved),
                "Rolling client update modified persistent state");
        CompoundTag malformed = new CompoundTag(); malformed.putByte("active", (byte) 2);
        replica.handleUpdateTag(malformed); helper.assertTrue(!replica.processingActive(), "Malformed rolling flag stayed active");
        replica.handleUpdateTag(controller.getUpdateTag()); replica.handleUpdateTag(new CompoundTag());
        helper.assertTrue(!replica.processingActive(), "Absent rolling flag stayed active");
    }
}
