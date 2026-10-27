package io.github.sunthemoon.advancedrocketrycommunity.rocket.server;

import io.github.sunthemoon.advancedrocketrycommunity.ModIdentity;
import io.github.sunthemoon.advancedrocketrycommunity.gametest.GameTestTickPacer;
import io.github.sunthemoon.advancedrocketrycommunity.registry.ModBlocks;
import io.github.sunthemoon.advancedrocketrycommunity.registry.ModItems;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.bay.SatelliteBayRoot;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.forge.*;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.model.*;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.persistence.RocketTransactionSavedData;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.sealed.*;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.stats.RocketStats;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.transaction.*;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.content.*;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.entity.HopperBlockEntity;
import net.minecraft.world.level.block.state.properties.ChestType;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.IFluidHandler.FluidAction;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(ModIdentity.MOD_ID)
@PrefixGameTestTemplate(false)
public final class SealedSpecialHostAutomationGameTests {
    @GameTest(template = "rocket_test", batch = "w152_special_containers", timeoutTicks = 400)
    public static void doubleChestBoundaryAndLazyLootStaySealedUnderTickingAutomation(GameTestHelper helper) { run(helper, false); }

    @GameTest(template = "rocket_test", batch = "w152_special_resources", timeoutTicks = 400)
    public static void tankAndBayStaySealedUnderTickingAutomation(GameTestHelper helper) { run(helper, true); }

    private static void run(GameTestHelper helper, boolean resourceHosts) {
        var level = helper.getLevel(); var origin = helper.absolutePos(new BlockPos(2, 3, 2));
        var targets = List.of(origin, origin.east(4)); var fence = origin.south(3); var owner = UUID.randomUUID();
        var water = new FluidStack(Fluids.WATER, 1000); water.getOrCreateTag().putString("W152", "original-fluid");
        var originalPackage = pkg(owner, "original"); var foreignPackage = pkg(owner, "pipe");
        var chest = new ChestBlockEntity(origin, Blocks.CHEST.defaultBlockState()); chest.setItem(0, item(Items.DIAMOND, 7, "original"));
        var loot = new CompoundTag(); loot.putString("LootTable", "minecraft:empty"); loot.putLong("LootTableSeed", 1234L);
        var states = resourceHosts
                ? List.of(ModBlocks.PRESSURIZED_TANK.get().defaultBlockState(), ModBlocks.ROCKET_SATELLITE_BAY.get().defaultBlockState())
                : List.of(Blocks.CHEST.defaultBlockState().setValue(ChestBlock.TYPE, ChestType.LEFT), Blocks.BARREL.defaultBlockState());
        var payloads = resourceHosts
                ? List.of(PressurizedTankRocketAdapter.payload(water), new RocketBlockEntityPayload(SatelliteBayRocketAdapter.ID,
                        SatelliteBayRoot.encode(new SatelliteBayRoot.Contents(owner, originalPackage.save(new CompoundTag()), null))))
                : List.of(new VanillaContainerRocketAdapter().capture(chest), new RocketBlockEntityPayload(VanillaContainerRocketAdapter.ID, loot));
        var blocks = new ArrayList<RocketBlock>();
        for (int i = 0; i < 2; i++) { blocks.add(new RocketBlock(new RocketPosition(i * 4, 0, 0), RocketBlockStateAdapter.capture(states.get(i)), payloads.get(i))); }
        blocks.add(new RocketBlock(new RocketPosition(0, 0, 3), RocketBlockStateAdapter.capture(Blocks.IRON_BLOCK.defaultBlockState())));
        var snapshot = RocketStructureSnapshot.create(UUID.randomUUID(), level.dimension().location(), SealedUnsealGameTests.position(origin), blocks,
                List.of(new RocketPosition(0, 0, 0)), new RocketStats(3, 300, 1000, 1000, 1, 1, 1, 2), level.getGameTime());
        var id = UUID.randomUUID(); var entity = UUID.randomUUID(); var data = RocketTransactionSavedData.get(level.getServer());
        data.journalFor(snapshot, owner).write(new RocketTransactionRecord(id, RocketTransactionType.SEALED_DISASSEMBLY, RocketTransactionPhase.RESTORED,
                snapshot.snapshotId(), snapshot.contentHash(), RocketRegion.fromSnapshot(snapshot), 2, entity));
        // W129aM transition (ADR-074 item 8): a request; the held recovery waits for its acknowledgement by itself.
        data.requestCheckpoint(level.getServer()); level.setBlock(fence, Blocks.BEDROCK.defaultBlockState(), 2);
        var carriers = new ArrayList<SealedRocketBlockEntity>(); var roots = new ArrayList<CompoundTag>();
        var inventoryPositions = new ArrayList<BlockPos>(); var recipient = new SimpleContainer(2);
        for (int i = 0; i < 2; i++) {
            var at = targets.get(i); int dx = i * 4;
            var block = snapshot.blocks().stream().filter(value -> value.position().equals(new RocketPosition(dx, 0, 0))).findFirst().orElseThrow();
            var carrier = SealedUnsealGameTests.install(level, at, new SealedRocketMember(id, snapshot.snapshotId(), entity, snapshot.contentHash(),
                    snapshot.sourceDimension(), SealedUnsealGameTests.position(at), snapshot.blocks().indexOf(block), block.state(), payloads.get(i)));
            carriers.add(carrier); roots.add(carrier.saveWithoutMetadata());
            level.setBlock(at.below(), Blocks.HOPPER.defaultBlockState().setValue(HopperBlock.FACING, Direction.EAST), 3);
            level.setBlock(at.west(), Blocks.HOPPER.defaultBlockState().setValue(HopperBlock.FACING, Direction.EAST), 3);
            ((Container) level.getBlockEntity(at.west())).setItem(0, item(Items.GOLD_INGOT, 3, "push"));
            level.setBlock(at.north(), Blocks.DROPPER.defaultBlockState().setValue(DispenserBlock.FACING, Direction.SOUTH), 3);
            ((Container) level.getBlockEntity(at.north())).setItem(0, item(Items.EMERALD, 3, "dropper"));
            level.setBlock(at.north(2), Blocks.REDSTONE_BLOCK.defaultBlockState(), 3);
            inventoryPositions.addAll(List.of(at, at.below(), at.west(), at.north()));
        }
        // The second half is outside U. Its own inventory must remain usable without exposing the sealed half.
        var outside = origin.east();
        if (!resourceHosts) {
            level.setBlock(outside, Blocks.CHEST.defaultBlockState().setValue(ChestBlock.TYPE, ChestType.RIGHT), 18);
            ((Container) level.getBlockEntity(outside)).setItem(0, item(Items.IRON_INGOT, 9, "neighbor")); inventoryPositions.add(outside);
        }
        var cachedOutside = resourceHosts ? null : level.getBlockEntity(outside).getCapability(ForgeCapabilities.ITEM_HANDLER).orElseThrow(IllegalStateException::new);
        var bounds = new AABB(origin.offset(-2, -3, -3), origin.offset(7, 3, 5)); var began = level.getGameTime();
        var used = new boolean[2]; var done = new boolean[1]; var published = new long[] {-1};
        // W166 (R123): the proofs' store, force and readback, and the journal writes, take wall-clock time. Unpaced ticks
        // let the second proof finish only at about tick 400 of the 400-tick budget, so each tick lasts a 20 TPS server tick.
        var pacer = new GameTestTickPacer(level.getServer());
        helper.onEachTick(() -> {
            if (done[0]) { return; }
            pacer.pace();
            long age = level.getGameTime() - began;
            if (age == 399) {
                io.github.sunthemoon.advancedrocketrycommunity.AdvancedRocketryCommunity.LOGGER.info(
                        "W152_SPECIAL_HOST_PENDING transaction={} state={} absent={} used={} published={}", id,
                        RocketRuntime.sealedDiagnosticForReleaseTest(level.getServer(), id),
                        data.acknowledgedAbsent(id, snapshot.snapshotId(), entity), java.util.Arrays.toString(used), published[0]);
            }
            for (int i = 0; i < 2; i++) {
                var at = targets.get(i); var live = level.getBlockEntity(at);
                if (age < 60) {
                    helper.assertTrue(live == carriers.get(i) && roots.get(i).equals(live.saveWithoutMetadata()), "Automation changed special-host C");
                    for (int face = -1; face < Direction.values().length; face++) {
                        var side = face < 0 ? null : Direction.values()[face];
                        helper.assertTrue(!live.getCapability(ForgeCapabilities.ITEM_HANDLER, side).isPresent()
                                && !live.getCapability(ForgeCapabilities.FLUID_HANDLER, side).isPresent(), "Held host exposes a side/null automation port");
                    }
                    if (age == 20) { live.invalidateCaps(); live.reviveCaps(); }
                    helper.assertTrue(((Container) level.getBlockEntity(at.west())).getItem(0).getCount() == 3, "Push hopper inserted into C");
                    if (age == 59) {
                        helper.assertTrue(((Container) level.getBlockEntity(at.north())).getItem(0).getCount() == 2, "Powered dropper did not tick");
                        helper.assertTrue(((HopperBlockEntity) level.getBlockEntity(at.west())).getLastUpdateTime() >= level.getGameTime() - 1
                                && ((HopperBlockEntity) level.getBlockEntity(at.below())).getLastUpdateTime() >= level.getGameTime() - 1,
                                "Adjacent pull/push hopper did not tick");
                    }
                } else if (live != null && !(live instanceof SealedRocketBlockEntity) && !used[i]) {
                    helper.assertTrue(data.acknowledgedAbsent(id, snapshot.snapshotId(), entity), "Special host exposed resources before checked U absence");
                    if (resourceHosts && i == 0) {
                        var port = live.getCapability(ForgeCapabilities.FLUID_HANDLER).orElseThrow(IllegalStateException::new);
                        helper.assertTrue(port.drain(1000, FluidAction.SIMULATE).isFluidStackIdentical(water)
                                && port.drain(1000, FluidAction.EXECUTE).isFluidStackIdentical(water), "Original fluid identity/count changed");
                        var foreign = new FluidStack(Fluids.LAVA, 333); foreign.getOrCreateTag().putString("W152", "foreign-fluid");
                        helper.assertTrue(port.fill(foreign, FluidAction.SIMULATE) == 333 && port.fill(foreign, FluidAction.EXECUTE) == 333,
                                "Published tank refused pipe insertion");
                    } else {
                        var port = live.getCapability(ForgeCapabilities.ITEM_HANDLER).orElseThrow(IllegalStateException::new);
                        var preview = port.extractItem(0, 64, true); var taken = port.extractItem(0, 64, false);
                        helper.assertTrue(ItemStack.matches(preview, taken), "Pipe extraction simulation changed resources"); recipient.setItem(i, taken);
                        var foreign = resourceHosts ? foreignPackage : item(Items.LAPIS_LAZULI, 3, "pipe"); int slot = resourceHosts ? 0 : 2;
                        helper.assertTrue(port.insertItem(slot, foreign.copy(), true).isEmpty() && port.insertItem(slot, foreign.copy(), false).isEmpty(),
                                "Published host refused pipe insertion");
                    }
                    used[i] = true;
                }
            }
            if (age < 60) {
                helper.assertTrue(count(level, inventoryPositions, bounds, recipient, "original") == 0, "Retained host leaked original items");
                if (cachedOutside != null) {
                    helper.assertTrue(cachedOutside.getSlots() == 27 && cachedOutside.getStackInSlot(0).getCount() == 9,
                            "Cached double-chest boundary exposed C or lost foreign inventory");
                }
            }
            helper.assertTrue(count(level, inventoryPositions, bounds, recipient, "push") == 6
                    && count(level, inventoryPositions, bounds, recipient, "dropper") == 6, "Foreign hopper/dropper resources changed");
            if (!resourceHosts) { helper.assertTrue(count(level, inventoryPositions, bounds, recipient, "neighbor") == 9, "Outside chest contents changed"); }
            if (age == 60) { level.setBlock(fence, Blocks.AIR.defaultBlockState(), 3); }
            if (used[0] && used[1]) {
                if (published[0] < 0) { published[0] = level.getGameTime(); }
                helper.assertTrue(count(level, inventoryPositions, bounds, recipient, "original") == (resourceHosts ? 1 : 7), "Published host lost/replayed original items");
                helper.assertTrue(count(level, inventoryPositions, bounds, recipient, "pipe") == (resourceHosts ? 1 : 6), "Foreign pipe contents changed");
                for (var carrier : carriers) {
                    helper.assertTrue(carrier.isRemoved() && !RocketRuntime.unsealForReleaseTest(level, carrier)
                            && !carrier.getCapability(ForgeCapabilities.ITEM_HANDLER).isPresent(), "Cached C reference replayed or exposed resources");
                }
                if (resourceHosts) {
                    var fluid = level.getBlockEntity(origin).getCapability(ForgeCapabilities.FLUID_HANDLER).orElseThrow(IllegalStateException::new).getFluidInTank(0);
                    helper.assertTrue(fluid.getFluid() == Fluids.LAVA && fluid.getAmount() == 333 && "foreign-fluid".equals(fluid.getTag().getString("W152")),
                            "Published tank refilled original fluid");
                }
                if (level.getGameTime() - published[0] >= 20) { done[0] = true; helper.succeed(); }
            }
        });
    }
    private static int count(net.minecraft.server.level.ServerLevel level, List<BlockPos> positions, AABB bounds, Container recipient, String label) {
        int total = 0;
        for (var at : positions) {
            var be = level.getBlockEntity(at);
            if (be instanceof Container container) { total += count(container, label); }
            else if (be != null) {
                var port = be.getCapability(ForgeCapabilities.ITEM_HANDLER).orElse(null);
                if (port != null) { for (int slot = 0; slot < port.getSlots(); slot++) { total += count(port.getStackInSlot(slot), label); } }
            }
        }
        total += count(recipient, label);
        for (var entity : level.getEntitiesOfClass(ItemEntity.class, bounds)) { total += count(entity.getItem(), label); }
        return total;
    }
    private static int count(Container container, String label) {
        int total = 0; for (int slot = 0; slot < container.getContainerSize(); slot++) { total += count(container.getItem(slot), label); } return total;
    }
    private static int count(ItemStack item, String label) { return item.hasTag() && label.equals(item.getTag().getString("W152")) ? item.getCount() : 0; }
    private static ItemStack item(net.minecraft.world.item.Item type, int amount, String label) {
        var item = new ItemStack(type, amount); item.getOrCreateTag().putString("W152", label); return item;
    }
    private static ItemStack pkg(UUID owner, String label) {
        var item = new ItemStack(ModItems.DATA_SATELLITE_PACKAGE.get());
        SatelliteItemData.write(item, new SatelliteIdentity(UUID.randomUUID(), owner, ModIdentity.id("w152_data")));
        item.getOrCreateTag().putString("W152", label); return item;
    }
    private SealedSpecialHostAutomationGameTests() { }
}
