package io.github.sunthemoon.advancedrocketrycommunity.rocket.server;

import io.github.sunthemoon.advancedrocketrycommunity.ModIdentity;
import io.github.sunthemoon.advancedrocketrycommunity.registry.ModBlocks;
import io.github.sunthemoon.advancedrocketrycommunity.registry.ModItems;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.bay.SatelliteBayRoot;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.forge.PressurizedTankRocketAdapter;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.forge.SatelliteBayRocketAdapter;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.model.RocketBlockEntityPayload;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.persistence.RocketTransactionSavedData;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.sealed.SealedRocketMember;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.transaction.*;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.content.SatelliteIdentity;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.content.SatelliteItemData;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.material.Fluids;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.IFluidHandler.FluidAction;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(ModIdentity.MOD_ID)
@PrefixGameTestTemplate(false)
public final class SealedResourceHostGameTests {
    @GameTest(template = "rocket_test", batch = "w152_resource_hosts", timeoutTicks = 100)
    public static void tankAndBayCapabilitiesOnlyMoveResourcesAfterCheckedUnseal(GameTestHelper helper) {
        var level = helper.getLevel(); var server = level.getServer(); var owner = UUID.randomUUID();
        var water = new FluidStack(Fluids.WATER, 1000); water.getOrCreateTag().putString("W152", "original-fluid");
        var original = pkg(owner, "original-package"); var foreign = pkg(owner, "foreign-package");
        var states = List.of(ModBlocks.PRESSURIZED_TANK.get().defaultBlockState(), ModBlocks.ROCKET_SATELLITE_BAY.get().defaultBlockState());
        var payloads = List.of(PressurizedTankRocketAdapter.payload(water), new RocketBlockEntityPayload(SatelliteBayRocketAdapter.ID,
                SatelliteBayRoot.encode(new SatelliteBayRoot.Contents(owner, original.save(new CompoundTag()), null))));
        for (int index = 0; index < states.size(); index++) {
            var at = helper.absolutePos(new BlockPos(2 + index * 4, 3, 2));
            var snapshot = SealedUnsealGameTests.snapshot(level, at, states.get(index), payloads.get(index));
            var id = UUID.randomUUID(); var entity = UUID.randomUUID(); var data = RocketTransactionSavedData.get(server);
            var journal = data.journalFor(snapshot, owner);
            journal.write(new RocketTransactionRecord(id, RocketTransactionType.SEALED_DISASSEMBLY, RocketTransactionPhase.RESTORED,
                    snapshot.snapshotId(), snapshot.contentHash(), RocketRegion.fromSnapshot(snapshot), 1, entity));
            data.checkpoint(server);
            var carrier = SealedUnsealGameTests.install(level, at, new SealedRocketMember(id, snapshot.snapshotId(), entity, snapshot.contentHash(),
                    snapshot.sourceDimension(), SealedUnsealGameTests.position(at), 0, snapshot.blocks().get(0).state(), payloads.get(index)));
            var held = carrier.saveWithoutMetadata();
            for (int face = -1; face < Direction.values().length; face++) {
                var side = face < 0 ? null : Direction.values()[face];
                helper.assertTrue(!carrier.getCapability(ForgeCapabilities.ITEM_HANDLER, side).isPresent()
                        && !carrier.getCapability(ForgeCapabilities.FLUID_HANDLER, side).isPresent(), "Retained resource host exposes automation");
            }
            helper.assertTrue(!RocketRuntime.unsealForReleaseTest(level, carrier), "Retained U exposed native tank/bay");
            journal.remove(id);
            helper.assertTrue(!RocketRuntime.unsealForReleaseTest(level, carrier) && held.equals(carrier.saveWithoutMetadata()),
                    "Unacknowledged removal changed resource host");
            data.checkpoint(server);
            helper.assertTrue(RocketRuntime.unsealForReleaseTest(level, carrier), "Checked resource host did not publish");
            var target = level.getBlockEntity(at);
            if (index == 0) {
                var handler = target.getCapability(ForgeCapabilities.FLUID_HANDLER).orElseThrow(IllegalStateException::new);
                var preview = handler.drain(1000, FluidAction.SIMULATE); var extracted = handler.drain(1000, FluidAction.EXECUTE);
                helper.assertTrue(preview.isFluidStackIdentical(water) && extracted.isFluidStackIdentical(water), "Tank lost or normalized original fluid");
                var lava = new FluidStack(Fluids.LAVA, 333); lava.getOrCreateTag().putString("W152", "foreign-fluid");
                helper.assertTrue(handler.fill(lava, FluidAction.SIMULATE) == 333 && handler.fill(lava, FluidAction.EXECUTE) == 333,
                        "Published tank refused matching simulated/real insertion");
                helper.assertTrue(!RocketRuntime.unsealForReleaseTest(level, carrier) && handler.getFluidInTank(0).isFluidStackIdentical(lava),
                        "Stale carrier replayed original fluid over foreign fluid");
            } else {
                var handler = target.getCapability(ForgeCapabilities.ITEM_HANDLER).orElseThrow(IllegalStateException::new);
                helper.assertTrue(ItemStack.matches(handler.extractItem(0, 1, true), original)
                        && ItemStack.matches(handler.extractItem(0, 1, false), original), "Bay lost or normalized its package");
                helper.assertTrue(handler.insertItem(0, foreign, true).isEmpty() && handler.insertItem(0, foreign, false).isEmpty(),
                        "Published bay refused foreign package");
                helper.assertTrue(!RocketRuntime.unsealForReleaseTest(level, carrier) && ItemStack.matches(handler.getStackInSlot(0), foreign),
                        "Stale carrier replayed the original package");
            }
            helper.assertTrue(carrier.isRemoved() && held.equals(carrier.saveWithoutMetadata()), "Removed C changed its saved shadow");
        }
        helper.succeed();
    }
    private static ItemStack pkg(UUID owner, String label) {
        var stack = new ItemStack(ModItems.DATA_SATELLITE_PACKAGE.get());
        SatelliteItemData.write(stack, new SatelliteIdentity(UUID.randomUUID(), owner, ModIdentity.id("w152_data")));
        stack.getOrCreateTag().putString("W152", label); return stack;
    }
    private SealedResourceHostGameTests() { }
}
