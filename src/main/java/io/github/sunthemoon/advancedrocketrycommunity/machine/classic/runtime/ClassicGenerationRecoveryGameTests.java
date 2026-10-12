package io.github.sunthemoon.advancedrocketrycommunity.machine.classic.runtime;

import io.github.sunthemoon.advancedrocketrycommunity.ModIdentity;
import io.github.sunthemoon.advancedrocketrycommunity.machine.classic.resource.ClassicResourcesCodec;
import java.util.ArrayList;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(ModIdentity.MOD_ID)
@PrefixGameTestTemplate(false)
public final class ClassicGenerationRecoveryGameTests {
    @GameTest(template = "empty", batch = "w01f_controller_saved_first", timeoutTicks = 30)
    public static void newerControllerAndOlderPartSnapshotRebindToCurrentGeneration(GameTestHelper helper) {
        mixedSnapshots(helper, true, 1); helper.succeed();
    }
    @GameTest(template = "empty", batch = "w01f_parts_saved_first", timeoutTicks = 30)
    public static void olderControllerAndSuccessorPartSnapshotReformWithoutForeignAdoption(GameTestHelper helper) {
        mixedSnapshots(helper, false, 1); helper.succeed();
    }
    @GameTest(template = "empty", batch = "w01g_parts_two_ahead", timeoutTicks = 30)
    public static void olderControllerAndPartsTwoGenerationsAheadReformWithoutConflict(GameTestHelper helper) {
        mixedSnapshots(helper, false, 2); helper.succeed();
    }
    @GameTest(template = "empty", batch = "w01g_controller_two_ahead", timeoutTicks = 30)
    public static void controllerTwoGenerationsAheadOfItsPartSnapshotsRebindsThem(GameTestHelper helper) {
        mixedSnapshots(helper, true, 2); helper.succeed();
    }
    /** Re-forms the machine {@code steps} times between the older and newer snapshots, then reloads one of each. */
    private static void mixedSnapshots(GameTestHelper helper, boolean controllerSavedFirst, int steps) {
        try (var fixture = new ClassicLatheGameTests.Fixture(helper, Direction.NORTH, true, true)) {
            fixture.seed(3, 0);
            var fluidHatch = (ClassicHatchBlockEntity) helper.getLevel().getBlockEntity(fixture.fluidInputPosition);
            var oldInputOptional = fixture.inputHatch.getCapability(ForgeCapabilities.ITEM_HANDLER);
            var oldInput = fixture.input();
            var oldFluid = fluidHatch.getCapability(ForgeCapabilities.FLUID_HANDLER).orElseThrow(() -> new IllegalStateException("Fluid"));
            oldFluid.fill(new net.minecraftforge.fluids.FluidStack(net.minecraft.world.level.material.Fluids.WATER, 9_000),
                    net.minecraftforge.fluids.capability.IFluidHandler.FluidAction.EXECUTE);
            var ownerId = fixture.controller.controllerState().machineInstanceId();
            var originalResources = ClassicResourcesCodec.encode(fixture.controller.checkpoint().resources());
            var olderController = fixture.controller.saveWithFullMetadata();
            var parts = fixture.hatches(); var olderParts = new ArrayList<CompoundTag>();
            parts.forEach(hatch -> olderParts.add(hatch.saveWithFullMetadata()));
            long olderGeneration = fixture.controller.controllerState().generation();
            var motorState = helper.getLevel().getBlockState(fixture.motor);
            for (int step = 0; step < steps; step++) {
                helper.getLevel().setBlock(fixture.motor, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
                fixture.manager.changed(fixture.motor); fixture.manager.tick();
                helper.getLevel().setBlock(fixture.motor, motorState, Block.UPDATE_ALL);
                fixture.manager.changed(fixture.motor); fixture.manager.tick();
            }
            long newerGeneration = fixture.controller.controllerState().generation();
            helper.assertTrue(newerGeneration == olderGeneration + steps && fixture.controller.formed(), "Fixture did not establish the successor generations");
            // Formation adopts a direct successor; parts further ahead return to the restored controller's generation.
            long recoveredGeneration = controllerSavedFirst || steps == 1 ? newerGeneration : olderGeneration;
            var newerController = fixture.controller.saveWithFullMetadata(); var newerParts = new ArrayList<CompoundTag>();
            parts.forEach(hatch -> newerParts.add(hatch.saveWithFullMetadata()));
            helper.assertTrue(!new ChunkPos(fixture.origin).equals(new ChunkPos(fixture.inputPosition)), "Skew fixture did not cross a chunk boundary");
            fixture.controller.load(controllerSavedFirst ? newerController : olderController);
            var savedParts = controllerSavedFirst ? olderParts : newerParts;
            for (int i = 0; i < parts.size(); i++) { parts.get(i).load(savedParts.get(i)); }
            fixture.manager.observe(fixture.controller); fixture.manager.tick();
            helper.assertTrue(fixture.controller.formed() && ownerId.equals(fixture.controller.controllerState().machineInstanceId())
                    && fixture.controller.controllerState().generation() == recoveredGeneration, "Mixed save ordering did not recover exact owner generation");
            helper.assertTrue(originalResources.equals(ClassicResourcesCodec.encode(fixture.controller.checkpoint().resources()))
                    && fixture.input().getStackInSlot(0).getCount() == 3
                    && fluidHatch.getCapability(ForgeCapabilities.FLUID_HANDLER).orElseThrow(() -> new IllegalStateException("Fresh fluid")).getFluidInTank(0).getAmount() == 9_000,
                    "Generation reconciliation altered Item/Fluid banks");
            helper.assertTrue(!oldInputOptional.isPresent() && oldInput.getStackInSlot(0).isEmpty()
                    && oldInput.insertItem(0, new ItemStack(Items.IRON_INGOT), false).getCount() == 1
                    && oldFluid.getFluidInTank(0).isEmpty(), "Old facade revived after mixed-snapshot rebind");
            for (var hatch : parts) {
                helper.assertTrue(hatch.multiblockBinding().orElseThrow().generation() == recoveredGeneration
                        && hatch.ownerClaim().orElseThrow().machineInstanceId().equals(ownerId), "Recovered binding is not exact");
            }
        }
    }
    private ClassicGenerationRecoveryGameTests() { }
}
