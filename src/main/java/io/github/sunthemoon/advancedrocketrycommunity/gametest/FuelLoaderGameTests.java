package io.github.sunthemoon.advancedrocketrycommunity.gametest;

import com.mojang.authlib.GameProfile;
import io.github.sunthemoon.advancedrocketrycommunity.AdvancedRocketryCommunity;
import io.github.sunthemoon.advancedrocketrycommunity.registry.ModBlocks;
import io.github.sunthemoon.advancedrocketrycommunity.registry.ModEntities;
import io.github.sunthemoon.advancedrocketrycommunity.registry.ModItems;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.RocketLimits;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.entity.RocketEntity;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.forge.RocketBlockEntityAdapters;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.forge.ServerLevelRocketScanWorld;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.fuel.*;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.model.RocketPosition;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.scan.RocketStructureScanTask;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(AdvancedRocketryCommunity.MOD_ID)
@PrefixGameTestTemplate(false)
public final class FuelLoaderGameTests {
    static final BlockPos LOADER = new BlockPos(7, 2, 3);
    static final String KEY = FuelLoaderStorage.DATA_KEY;
    private FuelLoaderGameTests() { }

    @GameTest(template = "rocket_test", timeoutTicks = 20)
    public static void queuedInputRetainsMetadataAndSimulationNeverMutates(GameTestHelper helper) {
        var loader = loader(helper); loader.assignOwner(UUID.randomUUID());
        var input = new ItemStack(ModItems.ROCKET_FUEL_CELL.get(), 3);
        input.getOrCreateTag().putString("fixture", "native metadata");
        var handler = loader.itemHandler();
        helper.assertTrue(handler.insertItem(0, input, true).getCount() == 2 && input.getCount() == 3
                && handler.getStackInSlot(0).isEmpty(), "Input simulation changed inventory");
        helper.assertTrue(handler.insertItem(0, input, false).getCount() == 2 && input.getCount() == 3, "One-item insertion differs");
        var read = handler.getStackInSlot(0); read.getOrCreateTag().putString("fixture", "mutated");
        helper.assertTrue(handler.getStackInSlot(0).getTag().getString("fixture").equals("native metadata"), "Handler leaked mutable NBT");
        var before = loader.saveWithoutMetadata();
        helper.assertTrue(handler.extractItem(0, 1, true).getCount() == 1 && before.equals(loader.saveWithoutMetadata()), "Extraction simulation mutated state");
        loader.load(before);
        helper.assertTrue(before.equals(loader.saveWithoutMetadata()), "Queued item changed on native save/load");
        loader.invalidateCaps(); loader.reviveCaps();
        helper.assertTrue(handler.extractItem(0, 1, false).isEmpty() && handler.getStackInSlot(0).isEmpty(), "Stale handler remained usable");
        helper.assertTrue(loader.itemHandler().extractItem(0, 1, false).getTag().getString("fixture").equals("native metadata"), "Revived view lost input");
        helper.succeed();
    }

    @GameTest(template = "rocket_test", timeoutTicks = 20)
    public static void legacyPartialBufferAndPendingRemainderSurviveReloadAndFullTank(GameTestHelper helper) {
        UUID owner = UUID.randomUUID(); var loader = loader(helper); var rocket = rocket(helper, owner);
        var flight = rocket.flightData().orElseThrow();
        rocket.updateFlightData(flight.withFuel(flight.fuel().fill(990).state(), 0));
        load(loader, FuelLoaderPersistence.encode(FuelLoaderPersistence.ItemState.EMPTY, 48, owner, null));
        tick(loader);
        helper.assertTrue(rocket.flightData().orElseThrow().fuel().amount() == 1000 && loader.bufferedUnits() == 38,
                "Partial capacity did not conserve the remaining buffer");
        tick(loader); var saved = loader.saveWithoutMetadata(); loader.load(saved); tick(loader);
        helper.assertTrue(loader.bufferedUnits() == 38 && loader.itemHandler().getStackInSlot(0).isEmpty(), "Full tank issued or lost remainder");
        helper.assertTrue(loader.saveWithoutMetadata().getCompound(KEY).getCompound("batch").getLong("total_units") == 500,
                "Legacy batch total was not captured");
        rocket.discard(); var replacement = rocket(helper, owner);
        tick(loader); tick(loader);
        helper.assertTrue(replacement.flightData().orElseThrow().fuel().amount() == 38 && loader.bufferedUnits() == 0
                && loader.itemHandler().extractItem(0, 1, false).is(ModItems.EMPTY_CANISTER.get()), "Migrated remainder or units differ");
        helper.assertTrue(loader.itemHandler().extractItem(0, 1, false).isEmpty(), "Duplicate remainder");
        replacement.discard(); helper.succeed();
    }

    @GameTest(template = "rocket_test", timeoutTicks = 20)
    public static void outputThatIsFuelDoesNotAutomaticallyBurnAndNoRemainderEmptiesSlot(GameTestHelper helper) {
        UUID owner = UUID.randomUUID(); var loader = loader(helper); var rocket = rocket(helper, owner);
        var pending = new ItemStack(ModItems.ROCKET_FUEL_CELL.get()).save(new CompoundTag());
        var batch = new FuelLoaderData.Batch("removed:fuel", 1, pending);
        load(loader, FuelLoaderStorage.encode(new FuelLoaderData(FuelLoaderData.Role.EMPTY, new CompoundTag(), 1, owner, null, batch)));
        tick(loader); tick(loader);
        helper.assertTrue(rocket.flightData().orElseThrow().fuel().amount() == 1
                && loader.itemHandler().getStackInSlot(0).is(ModItems.ROCKET_FUEL_CELL.get()), "Remainder was automatically consumed");
        var noRemainder = new FuelLoaderData.Batch("removed:without_remainder", 9, new CompoundTag());
        load(loader, FuelLoaderStorage.encode(new FuelLoaderData(FuelLoaderData.Role.EMPTY, new CompoundTag(), 9, owner, null, noRemainder)));
        tick(loader);
        helper.assertTrue(rocket.flightData().orElseThrow().fuel().amount() == 10 && loader.bufferedUnits() == 0
                && loader.itemHandler().getStackInSlot(0).isEmpty(), "No-remainder batch did not finish empty");
        rocket.discard(); helper.succeed();
    }

    @GameTest(template = "rocket_test", timeoutTicks = 20)
    public static void missingDefinitionKeepsQueuedItemExtractableByOwnerAndAutomation(GameTestHelper helper) {
        var loader = loader(helper); var owner = player(helper, UUID.randomUUID());
        var bread = new ItemStack(Items.BREAD).save(new CompoundTag());
        bread.put("tag", new CompoundTag()); bread.getCompound("tag").putString("fixture", "keep");
        var data = FuelLoaderStorage.encode(new FuelLoaderData(FuelLoaderData.Role.INPUT, bread, 0, owner.getUUID(), null, null));
        load(loader, data); tick(loader);
        helper.assertTrue(loader.status() == FuelLoaderStatus.UNSUPPORTED_FUEL && loader.bufferedUnits() == 0, "Missing definition burned/defaulted fuel");
        helper.assertTrue(loader.takeOutput(owner) && owner.getInventory().countItem(Items.BREAD) == 1, "Owner cannot recover unregistered input");
        load(loader, data);
        helper.assertTrue(loader.itemHandler().extractItem(0, 1, false).save(new CompoundTag()).equals(bread), "Automation lost input metadata");
        helper.succeed();
    }

    @GameTest(template = "rocket_test", timeoutTicks = 20)
    public static void playerAuthorityDistanceCreativeAndItemBoundsAreEnforced(GameTestHelper helper) {
        var loader = loader(helper); var owner = player(helper, UUID.randomUUID());
        loader.assignOwner(owner.getUUID());
        var stranger = player(helper, UUID.randomUUID());
        stranger.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ModItems.ROCKET_FUEL_CELL.get()));
        helper.assertTrue(!loader.insertFuelFromPlayer(stranger, InteractionHand.MAIN_HAND), "Wrong owner inserted fuel");
        owner.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ModItems.ROCKET_FUEL_CELL.get(), 2));
        owner.setPos(owner.getX() + 30, owner.getY(), owner.getZ());
        helper.assertTrue(!loader.insertFuelFromPlayer(owner, InteractionHand.MAIN_HAND), "Remote player inserted fuel");
        owner.setPos(loader.getBlockPos().getX(), loader.getBlockPos().getY(), loader.getBlockPos().getZ());
        helper.assertTrue(loader.insertFuelFromPlayer(owner, InteractionHand.MAIN_HAND)
                && owner.getMainHandItem().getCount() == 1, "Survival insertion did not consume one");
        helper.assertTrue(!loader.takeOutput(stranger), "Wrong owner extracted item");
        loader.itemHandler().extractItem(0, 1, false);
        owner.getAbilities().instabuild = true;
        helper.assertTrue(loader.insertFuelFromPlayer(owner, InteractionHand.MAIN_HAND) && owner.getMainHandItem().getCount() == 1,
                "Creative insertion changed existing instabuild convention");
        loader.itemHandler().extractItem(0, 1, false);
        var oversized = new ItemStack(ModItems.ROCKET_FUEL_CELL.get()); oversized.getOrCreateTag().putByteArray("huge", new byte[4096]);
        helper.assertTrue(loader.itemHandler().insertItem(0, oversized, false) == oversized
                && loader.itemHandler().getStackInSlot(0).isEmpty(), "Oversized input was accepted");
        helper.succeed();
    }

    @GameTest(template = "rocket_test", timeoutTicks = 20)
    public static void unknownItemsAndNormalizedDamageBlockWithoutLosingRawData(GameTestHelper helper) {
        var loader = loader(helper);
        for (boolean unknown : new boolean[]{true, false}) {
            var item = new ItemStack(unknown ? ModItems.ROCKET_FUEL_CELL.get() : Items.IRON_PICKAXE).save(new CompoundTag());
            if (unknown) { item.putString("id", "missing:item"); }
            else { item.getCompound("tag").putInt("Damage", -1); }
            var raw = FuelLoaderStorage.encode(new FuelLoaderData(FuelLoaderData.Role.INPUT, item, 0, UUID.randomUUID(), null, null));
            load(loader, raw); tick(loader); loader.assignOwner(UUID.randomUUID());
            helper.assertTrue(loader.status() == FuelLoaderStatus.INVALID_DATA && loader.saveWithoutMetadata().get(KEY).equals(raw)
                    && loader.itemHandler().extractItem(0, 1, false).isEmpty(), "Unknown item/invalid damage normalized or became operational");
        }
        helper.succeed();
    }

    @GameTest(template = "rocket_test", timeoutTicks = 20)
    public static void nativeLazyCapabilityPayloadRemainsExactWhileQueuedAndExtracted(GameTestHelper helper) {
        var loader = loader(helper);
        var item = new ItemStack(ModItems.ROCKET_FUEL_CELL.get()).save(new CompoundTag());
        var caps = new CompoundTag(); caps.putInt("missing:capability", 81); item.put("ForgeCaps", caps);
        var raw = FuelLoaderStorage.encode(new FuelLoaderData(FuelLoaderData.Role.INPUT, item, 0, UUID.randomUUID(), null, null));
        load(loader, raw);
        helper.assertTrue(loader.saveWithoutMetadata().get(KEY).equals(raw), "Lazy capabilities changed while queued");
        helper.assertTrue(loader.itemHandler().extractItem(0, 1, false).save(new CompoundTag()).equals(item),
                "Native lazy capability payload changed on extraction");
        helper.succeed();
    }

    static FuelLoaderBlockEntity loader(GameTestHelper helper) {
        helper.setBlock(LOADER, ModBlocks.FUEL_LOADER.get());
        return (FuelLoaderBlockEntity) helper.getBlockEntity(LOADER);
    }

    static FakePlayer player(GameTestHelper helper, UUID id) {
        var player = new FakePlayer(helper.getLevel(), new GameProfile(id, "FuelFixture"));
        BlockPos pos = helper.absolutePos(LOADER);
        player.setPos(pos.getX(), pos.getY(), pos.getZ());
        return player;
    }

    static void tick(FuelLoaderBlockEntity loader) {
        FuelLoaderBlockEntity.serverTick(loader.getLevel(), loader.getBlockPos(), loader.getBlockState(), loader);
    }

    static void load(FuelLoaderBlockEntity loader, net.minecraft.nbt.Tag root) {
        var parent = new CompoundTag(); parent.put(KEY, root); loader.load(parent);
    }

    static RocketEntity rocket(GameTestHelper helper, UUID owner) {
        BlockPos origin = new BlockPos(3, 2, 3);
        helper.setBlock(origin, ModBlocks.ROCKET_MOTOR.get()); helper.setBlock(origin.west(), ModBlocks.ROCKET_FUEL_TANK.get());
        helper.setBlock(origin.above(), ModBlocks.ROCKET_SEAT.get()); helper.setBlock(origin.above(2), ModBlocks.GUIDANCE_COMPUTER.get());
        BlockPos pos = helper.absolutePos(origin);
        var level = helper.getLevel();
        var task = new RocketStructureScanTask(new ServerLevelRocketScanWorld(level, RocketBlockEntityAdapters.defaults()),
                level.dimension().location(), new RocketPosition(pos.getX(), pos.getY(), pos.getZ()), UUID.randomUUID(), level.getGameTime());
        var result = task.step(RocketLimits.MAX_SCAN_INSPECTIONS_PER_TICK);
        helper.assertTrue(result.snapshot().isPresent(), "Fuel fixture rocket scan failed");
        var rocket = ModEntities.ROCKET.get().create(level);
        helper.assertTrue(rocket != null, "Rocket factory failed");
        rocket.initialize(result.snapshot().orElseThrow(), UUID.randomUUID(), owner);
        helper.assertTrue(level.addFreshEntity(rocket), "Fuel fixture rocket spawn failed");
        return rocket;
    }
}
