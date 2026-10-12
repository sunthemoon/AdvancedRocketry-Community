package io.github.sunthemoon.advancedrocketrycommunity.machine.classic.runtime;

import io.github.sunthemoon.advancedrocketrycommunity.ModIdentity;
import io.github.sunthemoon.advancedrocketrycommunity.registry.ModBlocks;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
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
public final class ClassicClaimRecoveryGameTests {
    @GameTest(template = "empty", batch = "w01f_orphan_claims", timeoutTicks = 30)
    public static void savedAbsentControllerRetiresOldHatchAndRemotePlugClaimsWithoutLosingFe(GameTestHelper helper) {
        try (var fixture = new ClassicLatheGameTests.Fixture(helper, Direction.NORTH, false, true, true)) {
            fixture.seed(7, 1_234);
            var hatchSaved = fixture.inputHatch.saveWithFullMetadata(); var powerSaved = fixture.power.saveWithFullMetadata();
            var oldInput = fixture.input(); var oldPower = fixture.power.getCapability(ForgeCapabilities.ENERGY).orElseThrow(() -> new IllegalStateException("Plug"));
            UUID oldOwner = fixture.controller.controllerState().machineInstanceId();
            helper.assertTrue(!new ChunkPos(fixture.origin).equals(new ChunkPos(fixture.inputPosition))
                    && !new ChunkPos(fixture.origin).equals(new ChunkPos(fixture.power.getBlockPos())), "Saved parts are not in a separate chunk");
            helper.getLevel().destroyBlock(fixture.origin, false); fixture.manager.forget(fixture.controller);
            // Restore only the old part snapshots: the controller chunk has persisted its removal.
            fixture.inputHatch.load(hatchSaved); fixture.power.load(powerSaved);
            helper.assertTrue(!ClassicRemovalPolicy.blocks(helper.getLevel(), fixture.inputPosition)
                    && !ClassicRemovalPolicy.blocks(helper.getLevel(), fixture.power.getBlockPos())
                    && fixture.inputHatch.ownerClaim().isEmpty() && fixture.power.ownerClaim().isEmpty()
                    && fixture.inputHatch.multiblockBinding().isEmpty() && fixture.power.multiblockBinding().isEmpty(), "Loaded absent owner left orphaned claims");
            helper.assertTrue(fixture.power.energyStored() == 1_234 && oldInput.getStackInSlot(0).isEmpty()
                    && oldPower.receiveEnergy(1, false) == 0, "Retirement lost plug FE or revived old facades");
            helper.assertTrue(fixture.inputHatch.saveWithFullMetadata().getCompound(ClassicHatchBlockEntity.ROOT).getCompound("claim").isEmpty()
                    && fixture.power.saveWithFullMetadata().getCompound(ClassicHatchBlockEntity.ROOT).getInt("energy") == 1_234, "Retirement did not persist");
            helper.getLevel().setBlock(fixture.origin, ModBlocks.LATHE.get().defaultBlockState(), Block.UPDATE_ALL);
            var replacement = (ClassicControllerBlockEntity) helper.getLevel().getBlockEntity(fixture.origin);
            fixture.manager.observe(replacement); fixture.manager.tick();
            helper.assertTrue(replacement.formed() && !oldOwner.equals(replacement.controllerState().machineInstanceId())
                    && fixture.input().getStackInSlot(0).isEmpty() && fixture.power.energyStored() == 1_234, "Replacement adopted removed resources or stranded FE");
            fixture.power.load(powerSaved); fixture.inputHatch.load(hatchSaved);
            helper.assertTrue(fixture.power.ownerClaim().isEmpty() && fixture.inputHatch.ownerClaim().isEmpty()
                    && fixture.power.energyStored() == 1_234, "Different loaded UUID did not retire stale claims");
            helper.assertTrue(fixture.dropped(Items.IRON_INGOT) == 7, "Controller removal bank accounting changed");
        }
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "w01f_unloaded_claim", timeoutTicks = 30)
    public static void unloadedClaimedPositionIsNeverLoadedOrRetired(GameTestHelper helper) {
        try (var fixture = new ClassicLatheGameTests.Fixture(helper, Direction.NORTH, false)) {
            var remote = new net.minecraft.core.BlockPos(24_000_000, 80, -24_000_000);
            var source = helper.getLevel().getChunkSource();
            helper.assertTrue(source.getChunkNow(remote.getX() >> 4, remote.getZ() >> 4) == null, "Remote chunk already loaded");
            fixture.inputHatch.clearClaim();
            var claim = new io.github.sunthemoon.advancedrocketrycommunity.machine.multiblock.lifecycle.MultiblockPartBinding(
                    1, helper.getLevel().dimension(), remote, UUID.randomUUID(), 1);
            fixture.inputHatch.setMultiblockBinding(java.util.Optional.of(claim));
            helper.assertTrue(ClassicRemovalPolicy.blocks(helper.getLevel(), fixture.inputPosition)
                    && fixture.inputHatch.ownerClaim().orElseThrow().equals(claim)
                    && source.getChunkNow(remote.getX() >> 4, remote.getZ() >> 4) == null, "Unloaded claim was retired or force loaded");
            fixture.inputHatch.clearClaim();
        }
        helper.succeed();
    }
    private ClassicClaimRecoveryGameTests() { }
}
