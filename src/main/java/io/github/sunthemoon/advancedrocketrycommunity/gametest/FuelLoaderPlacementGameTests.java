package io.github.sunthemoon.advancedrocketrycommunity.gametest;

import io.github.sunthemoon.advancedrocketrycommunity.AdvancedRocketryCommunity;
import io.github.sunthemoon.advancedrocketrycommunity.registry.ModItems;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.fuel.FuelLoaderBlockEntity;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.fuel.FuelLoaderData;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.fuel.FuelLoaderStorage;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.fuel.FuelLoaderStatus;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(AdvancedRocketryCommunity.MOD_ID)
@PrefixGameTestTemplate(false)
public final class FuelLoaderPlacementGameTests {
    private FuelLoaderPlacementGameTests() { }

    @GameTest(template = "rocket_test", timeoutTicks = 20)
    public static void queuedNativeItemDropAndActualBlockItemPlacementConserveOwnerAndMetadata(GameTestHelper helper) {
        var item = new ItemStack(ModItems.ROCKET_FUEL_CELL.get()); item.getOrCreateTag().putString("fixture", "keep");
        UUID owner = UUID.randomUUID();
        var raw = FuelLoaderStorage.encode(new FuelLoaderData(FuelLoaderData.Role.INPUT, item.save(new CompoundTag()), 0, owner, null, null));
        var placed = roundTrip(helper, raw);
        helper.assertTrue(placed.ownerId().orElseThrow().equals(owner)
                && placed.itemHandler().extractItem(0, 1, false).save(new CompoundTag()).equals(item.save(new CompoundTag())),
                "Drop/place replaced owner or native item");
        helper.succeed();
    }

    @GameTest(template = "rocket_test", timeoutTicks = 20)
    public static void unfinishedBatchDropAndActualPlacementRetainUnitsAndPendingRemainder(GameTestHelper helper) {
        var batch = new FuelLoaderData.Batch("removed:definition", 73, new ItemStack(ModItems.EMPTY_CANISTER.get()).save(new CompoundTag()));
        var raw = FuelLoaderStorage.encode(new FuelLoaderData(FuelLoaderData.Role.EMPTY, new CompoundTag(), 48,
                UUID.randomUUID(), UUID.randomUUID(), batch));
        var placed = roundTrip(helper, raw);
        helper.assertTrue(placed.bufferedUnits() == 48 && placed.itemHandler().getStackInSlot(0).isEmpty(), "Buffered work became an item");
        helper.succeed();
    }

    @GameTest(template = "rocket_test", timeoutTicks = 20)
    public static void futureCompoundSurvivesNativePlacementWithoutDefaultFieldMerge(GameTestHelper helper) {
        var raw = new CompoundTag(); raw.putInt("schema_version", 3); raw.putString("opaque", "keep");
        var placed = roundTrip(helper, raw);
        helper.assertTrue(placed.status() == FuelLoaderStatus.UNSUPPORTED_DATA, "Future root became operational");
        helper.succeed();
    }

    @GameTest(template = "rocket_test", timeoutTicks = 20)
    public static void malformedCompoundSurvivesNativePlacementWithoutRepairingMissingFields(GameTestHelper helper) {
        var raw = FuelLoaderStorage.encode(new FuelLoaderData(FuelLoaderData.Role.INPUT,
                new ItemStack(ModItems.ROCKET_FUEL_CELL.get()).save(new CompoundTag()), 0, UUID.randomUUID(), null, null));
        raw.remove("buffered_units");
        var placed = roundTrip(helper, raw);
        helper.assertTrue(placed.status() == FuelLoaderStatus.INVALID_DATA && placed.itemHandler().getStackInSlot(0).isEmpty(),
                "Native merge repaired an incomplete root");
        helper.succeed();
    }

    @GameTest(template = "rocket_test", timeoutTicks = 20)
    public static void nonCompoundRootSurvivesNativeDropAndPlacementVerbatim(GameTestHelper helper) {
        var placed = roundTrip(helper, StringTag.valueOf("opaque malformed root"));
        helper.assertTrue(placed.status() == FuelLoaderStatus.INVALID_DATA, "Scalar root became operational");
        helper.succeed();
    }

    @GameTest(template = "rocket_test", timeoutTicks = 20)
    public static void oversizedRootPassesThroughNativeSaveAndRefusesSurvivalBeforeRemoval(GameTestHelper helper) {
        var loader = FuelLoaderGameTests.loader(helper);
        var raw = new CompoundTag(); raw.putByteArray("large", new byte[16384]);
        FuelLoaderGameTests.load(loader, raw);
        helper.assertTrue(!loader.canCarryData() && loader.saveWithoutMetadata().get(FuelLoaderStorage.DATA_KEY) == raw,
                "Oversized root was copied, omitted or normalized");
        var player = FuelLoaderGameTests.player(helper, UUID.randomUUID());
        var state = loader.getBlockState(); var level = helper.getLevel(); var pos = loader.getBlockPos();
        helper.assertTrue(!state.getBlock().onDestroyedByPlayer(state, level, pos, player, true, level.getFluidState(pos))
                && level.getBlockEntity(pos) == loader, "Quarantined loader was removed before refusal");
        FuelLoaderGameTests.tick(loader);
        helper.assertTrue(loader.status() == FuelLoaderStatus.INVALID_DATA
                && loader.itemHandler().insertItem(0, new ItemStack(ModItems.ROCKET_FUEL_CELL.get()), false).getCount() == 1,
                "Quarantine permitted mutation");
        helper.succeed();
    }

    private static FuelLoaderBlockEntity roundTrip(GameTestHelper helper, Tag raw) {
        var loader = FuelLoaderGameTests.loader(helper);
        FuelLoaderGameTests.load(loader, raw);
        var level = helper.getLevel(); var pos = loader.getBlockPos();
        helper.assertTrue(level.destroyBlock(pos, true), "Native destruction failed");
        var drops = level.getEntitiesOfClass(ItemEntity.class, new AABB(pos).inflate(1.5));
        helper.assertTrue(drops.size() == 1, "Native drop duplicated visible inventory or omitted loader count=" + drops.size());
        ItemStack carried = drops.get(0).getItem().copy(); drops.get(0).discard();
        helper.assertTrue(carried.getItem() instanceof BlockItem && carried.getCount() == 1, "Expected one loader BlockItem");
        helper.assertTrue(BlockItem.getBlockEntityData(carried).get(FuelLoaderStorage.DATA_KEY).equals(raw), "Dropped root differs");
        BlockPos dest = pos.south(3);
        level.setBlockAndUpdate(dest.below(), Blocks.STONE.defaultBlockState());
        level.setBlockAndUpdate(dest, Blocks.AIR.defaultBlockState());
        var player = FuelLoaderGameTests.player(helper, UUID.randomUUID());
        player.setPos(dest.getX() + 2, dest.getY(), dest.getZ());
        player.setItemInHand(InteractionHand.MAIN_HAND, carried);
        var hit = new BlockHitResult(Vec3.atCenterOf(dest.below()).add(0, 0.5, 0), Direction.UP, dest.below(), false);
        var context = new BlockPlaceContext(level, player, InteractionHand.MAIN_HAND, carried, hit);
        helper.assertTrue(((BlockItem) carried.getItem()).place(context).consumesAction(), "Native BlockItem placement failed");
        helper.assertTrue(level.getBlockEntity(dest) instanceof FuelLoaderBlockEntity, "Placement did not restore loader");
        var placed = (FuelLoaderBlockEntity) level.getBlockEntity(dest);
        helper.assertTrue(placed.saveWithoutMetadata().get(FuelLoaderStorage.DATA_KEY).equals(raw), "Placed root was merged or normalized");
        return placed;
    }
}
