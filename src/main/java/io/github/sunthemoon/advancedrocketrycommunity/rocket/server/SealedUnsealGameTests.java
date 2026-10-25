package io.github.sunthemoon.advancedrocketrycommunity.rocket.server;

import io.github.sunthemoon.advancedrocketrycommunity.ModIdentity;
import io.github.sunthemoon.advancedrocketrycommunity.registry.ModBlocks;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.bay.SatelliteBayBlockEntity;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.bay.SatelliteBayRoot;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.forge.*;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.model.*;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.persistence.RocketTransactionSavedData;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.sealed.*;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.stats.RocketStats;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.transaction.*;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluids;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(ModIdentity.MOD_ID)
@PrefixGameTestTemplate(false)
public final class SealedUnsealGameTests {
    @GameTest(template = "rocket_test", batch = "w152_unseal", timeoutTicks = 100)
    public static void exactCheckedAbsencePrecedesPublicationAndNativeInventoryIsNeverRefilled(GameTestHelper helper) {
        var level = helper.getLevel(); var at = helper.absolutePos(new BlockPos(1, 2, 1));
        var payload = inventory(); var snapshot = snapshot(level, at, Blocks.CHEST.defaultBlockState(), payload);
        var id = UUID.randomUUID(); var entity = UUID.randomUUID();
        var record = new RocketTransactionRecord(id, RocketTransactionType.SEALED_DISASSEMBLY, RocketTransactionPhase.RESTORED,
                snapshot.snapshotId(), snapshot.contentHash(), RocketRegion.fromSnapshot(snapshot), 1, entity);
        var data = RocketTransactionSavedData.get(level.getServer()); var journal = data.journalFor(snapshot, UUID.randomUUID());
        journal.write(record); data.checkpoint(level.getServer());
        var carrier = install(level, at, new SealedRocketMember(id, snapshot.snapshotId(), entity, snapshot.contentHash(),
                level.dimension().location(), position(at), 0, snapshot.blocks().get(0).state(), payload));
        helper.assertTrue(!unseal(level, carrier), "Retained U admitted unseal");
        helper.assertTrue(level.getBlockEntity(at) == carrier, "Held member was replaced");
        journal.remove(id);
        helper.assertTrue(!unseal(level, carrier), "RAM-only U removal admitted unseal");
        helper.assertTrue(level.getBlockEntity(at) == carrier, "Unacknowledged member was replaced");
        data.checkpoint(level.getServer());
        helper.assertTrue(data.acknowledgedAbsent(id, snapshot.snapshotId(), entity), "Fixture has no exact absence proof");
        helper.assertTrue(unseal(level, carrier), "Checked member did not publish");
        var target = (ChestBlockEntity) level.getBlockEntity(at);
        helper.assertTrue(target != null && target.getLevel() == level && !target.isRemoved(), "Restored target was not installed");
        helper.assertTrue(target.getItem(0).is(Items.DIAMOND) && target.getItem(0).getCount() == 7, "Publication lost original items");
        target.removeItemNoUpdate(0); target.setItem(1, new ItemStack(Items.GOLD_INGOT, 3));
        helper.assertTrue(!unseal(level, carrier), "Stale C was replayed over native terrain");
        helper.assertTrue(target.getItem(0).isEmpty() && target.getItem(1).getCount() == 3, "Native contents were refilled or erased");
        helper.succeed();
    }

    @GameTest(template = "rocket_test", batch = "w152_unseal", timeoutTicks = 100)
    public static void allBuiltInTargetsPrepareDetachedAndAbortEmpty(GameTestHelper helper) {
        var adapters = RocketBlockEntityAdapters.defaults(); var at = helper.absolutePos(new BlockPos(1, 2, 1));
        var bay = SatelliteBayRoot.encode(new SatelliteBayRoot.Contents(UUID.randomUUID(),
                new ItemStack(Items.DIAMOND).save(new CompoundTag()), null));
        var states = List.of(Blocks.CHEST.defaultBlockState(), Blocks.BARREL.defaultBlockState(),
                ModBlocks.PRESSURIZED_TANK.get().defaultBlockState(), ModBlocks.ROCKET_SATELLITE_BAY.get().defaultBlockState());
        var payloads = List.of(inventory(), inventory(), PressurizedTankRocketAdapter.payload(new FluidStack(Fluids.WATER, 1_000)),
                new RocketBlockEntityPayload(SatelliteBayRocketAdapter.ID, bay));
        for (int index = 0; index < states.size(); index++) {
            var prepared = SealedTargetPreparation.prepare(Thread.currentThread(), at, states.get(index), payloads.get(index), adapters).orElseThrow();
            var target = prepared.target();
            helper.assertTrue(target.getLevel() == null && !target.isRemoved(), "Preparation published a target");
            helper.assertTrue(adapters.capture(target).optionalPayload().filter(payloads.get(index)::equals).isPresent(), "Detached contents differ");
            prepared.close();
            helper.assertTrue(target.isRemoved(), "Abort retained a live target");
            if (target instanceof Container container) { helper.assertTrue(container.isEmpty(), "Abort retained owned items"); }
            else if (target instanceof SatelliteBayBlockEntity satellite) { helper.assertTrue(satellite.captureRoot().isEmpty(), "Abort retained bay authority"); }
            else {
                var captured = new PressurizedTankRocketAdapter().capture(target);
                helper.assertTrue(PressurizedTankRocketAdapter.fluid(captured.data()).isEmpty(), "Abort retained tank fluid");
            }
        }
        helper.succeed();
    }

    @GameTest(template = "rocket_test", batch = "w152_unseal", timeoutTicks = 100)
    public static void plainMemberPublishesWithoutNativeBlockEntity(GameTestHelper helper) {
        var level = helper.getLevel(); var at = helper.absolutePos(new BlockPos(1, 2, 1));
        var snapshot = snapshot(level, at, Blocks.IRON_BLOCK.defaultBlockState(), null);
        var member = new SealedRocketMember(UUID.randomUUID(), snapshot.snapshotId(), UUID.randomUUID(), snapshot.contentHash(),
                level.dimension().location(), position(at), 0, snapshot.blocks().get(0).state(), null);
        var carrier = install(level, at, member);
        RocketTransactionSavedData.get(level.getServer()).checkpoint(level.getServer());
        helper.assertTrue(unseal(level, carrier), "Plain member not published");
        helper.assertTrue(level.getBlockState(at).is(Blocks.IRON_BLOCK) && level.getBlockEntity(at) == null, "Plain publication retained C");
        helper.succeed();
    }

    private static boolean unseal(ServerLevel level, SealedRocketBlockEntity carrier) {
        try {
            return (boolean) RocketRuntime.class.getMethod("unsealForReleaseTest", ServerLevel.class, SealedRocketBlockEntity.class)
                    .invoke(null, level, carrier);
        } catch (ReflectiveOperationException failure) { throw new IllegalStateException("Production unsealer unavailable", failure); }
    }
    static SealedRocketBlockEntity install(ServerLevel level, BlockPos at, SealedRocketMember member) {
        var state = ModBlocks.SEALED_ROCKET_BLOCK.get().defaultBlockState();
        level.setBlock(at, state, 2);
        var carrier = new SealedRocketBlockEntity(at, state); carrier.initialize(SealedRocketRoot.stage(member));
        level.setBlockEntity(carrier); carrier.setChanged(); return carrier;
    }
    private static RocketBlockEntityPayload inventory() {
        var chest = new ChestBlockEntity(BlockPos.ZERO, Blocks.CHEST.defaultBlockState());
        chest.setItem(0, new ItemStack(Items.DIAMOND, 7)); return new VanillaContainerRocketAdapter().capture(chest);
    }
    static RocketPosition position(BlockPos at) { return new RocketPosition(at.getX(), at.getY(), at.getZ()); }
    static RocketStructureSnapshot snapshot(ServerLevel level, BlockPos at, BlockState state, RocketBlockEntityPayload payload) {
        return RocketStructureSnapshot.create(UUID.randomUUID(), level.dimension().location(), position(at),
                List.of(new RocketBlock(new RocketPosition(0, 0, 0), RocketBlockStateAdapter.capture(state), payload)),
                List.of(new RocketPosition(0, 0, 0)), new RocketStats(1, 100, 1000, 1000, 1, 1, 1, payload == null ? 0 : 1), level.getGameTime());
    }
    private SealedUnsealGameTests() { }
}
