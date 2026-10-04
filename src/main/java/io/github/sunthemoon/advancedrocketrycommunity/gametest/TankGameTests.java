package io.github.sunthemoon.advancedrocketrycommunity.gametest;

import io.github.sunthemoon.advancedrocketrycommunity.ModIdentity;
import io.github.sunthemoon.advancedrocketrycommunity.fluid.ClassicFluids;
import io.github.sunthemoon.advancedrocketrycommunity.machine.tank.PressurizedTankBlock;
import io.github.sunthemoon.advancedrocketrycommunity.machine.tank.PressurizedTankBlockEntity;
import io.github.sunthemoon.advancedrocketrycommunity.machine.tank.PressurizedTankItem;
import io.github.sunthemoon.advancedrocketrycommunity.machine.tank.TankProtection;
import io.github.sunthemoon.advancedrocketrycommunity.machine.tank.TankSave;
import io.github.sunthemoon.advancedrocketrycommunity.registry.ModItems;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.IntTag;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.event.level.ChunkDataEvent;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.IFluidHandler;
import net.minecraftforge.fluids.capability.IFluidHandler.FluidAction;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import net.minecraftforge.registries.ForgeRegistries;

/** Authored A1 coverage; execution requires root's registered/data-generated integration. */
@GameTestHolder(ModIdentity.MOD_ID)
@PrefixGameTestTemplate(false)
public final class TankGameTests {
    private static final BlockPos POSITION = new BlockPos(1, 2, 1);
    private static PressurizedTankBlockEntity place(GameTestHelper helper, BlockPos position) {
        Block block = ForgeRegistries.BLOCKS.getValue(ModIdentity.id("pressurized_tank"));
        helper.assertTrue(block instanceof PressurizedTankBlock, "Tank registration unavailable");
        helper.setBlock(position, block);
        return (PressurizedTankBlockEntity) helper.getBlockEntity(position);
    }
    private static IFluidHandler port(PressurizedTankBlockEntity tank) {
        return tank.getCapability(ForgeCapabilities.FLUID_HANDLER).orElseThrow(IllegalStateException::new);
    }
    private static Player player(GameTestHelper helper, PressurizedTankBlockEntity tank) {
        Player player = helper.makeMockPlayer();
        player.setPos(tank.getBlockPos().getX() + 0.5, tank.getBlockPos().getY(), tank.getBlockPos().getZ() + 0.5);
        return player;
    }

    @GameTest(template = "empty", timeoutTicks = 20)
    public static void everySideSimulatesAndViewsCannotMutateOrMixFluids(GameTestHelper helper) {
        var tank = place(helper, POSITION);
        for (Direction side : Direction.values()) {
            var handler = tank.getCapability(ForgeCapabilities.FLUID_HANDLER, side).orElseThrow(IllegalStateException::new);
            helper.assertTrue(handler.fill(new FluidStack(Fluids.WATER, 1_000), FluidAction.SIMULATE) == 1_000
                    && tank.fluidState().isEmpty(), "Side simulation consumed fluid");
        }
        helper.assertTrue(port(tank).fill(new FluidStack(Fluids.WATER, 1_000), FluidAction.EXECUTE) == 1_000, "Fill refused");
        FluidStack view = port(tank).getFluidInTank(0); view.setAmount(2_000_000);
        helper.assertTrue(tank.fluidState().getAmount() == 1_000, "View aliases fluid");
        helper.assertTrue(port(tank).fill(new FluidStack(Fluids.LAVA, 1_000), FluidAction.EXECUTE) == 0, "Mixed fluids");
        helper.assertTrue(port(tank).drain(1_000, FluidAction.SIMULATE).getAmount() == 1_000
                && tank.fluidState().getAmount() == 1_000, "Drain simulation changed fluid");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 20)
    public static void realCanisterStacksStowOneAndFullInventoryRefuses(GameTestHelper helper) {
        var tank = place(helper, POSITION); Player player = player(helper, tank);
        for (int slot = 0; slot < player.getInventory().items.size(); slot++) {
            player.getInventory().setItem(slot, new ItemStack(Items.STONE, 64));
        }
        ItemStack held = new ItemStack(ModItems.OXYGEN_CANISTER.get(), 16);
        held.getOrCreateTag().putString("marker", "tank"); player.setItemInHand(InteractionHand.MAIN_HAND, held);
        helper.assertTrue(!tank.exchange(player, InteractionHand.MAIN_HAND) && tank.fluidState().isEmpty()
                && held.getCount() == 16, "Full inventory consumed a canister or oxygen");
        player.getInventory().setItem(5, ItemStack.EMPTY);
        helper.assertTrue(tank.exchange(player, InteractionHand.MAIN_HAND), "Whole-unit pour refused");
        helper.assertTrue(tank.fluidState().getAmount() == 1_000
                && tank.fluidState().getFluid() == ClassicFluids.OXYGEN.get()
                && player.getItemInHand(InteractionHand.MAIN_HAND).getCount() == 15
                && player.getInventory().getItem(5).is(ModItems.EMPTY_CANISTER.get()), "Wrong one-unit pour");
        helper.assertTrue("tank".equals(player.getInventory().getItem(5).getTag().getString("marker")), "Metadata lost");
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ModItems.EMPTY_CANISTER.get(), 16));
        player.getInventory().setItem(6, ItemStack.EMPTY);
        helper.assertTrue(tank.exchange(player, InteractionHand.MAIN_HAND) && tank.fluidState().isEmpty()
                && player.getInventory().getItem(6).is(ModItems.OXYGEN_CANISTER.get()), "Canister fill lost oxygen");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 20)
    public static void bucketsKeepMetadataAndPartialOrRemoteRequestsRefuse(GameTestHelper helper) {
        var tank = place(helper, POSITION); Player player = player(helper, tank);
        ItemStack held = new ItemStack(Items.LAVA_BUCKET); held.getOrCreateTag().putString("marker", "bucket");
        player.setItemInHand(InteractionHand.MAIN_HAND, held);
        helper.assertTrue(tank.exchange(player, InteractionHand.MAIN_HAND) && tank.fluidState().getAmount() == 1_000,
                "Lava bucket pour failed");
        helper.assertTrue(player.getItemInHand(InteractionHand.MAIN_HAND).is(Items.BUCKET)
                && "bucket".equals(player.getItemInHand(InteractionHand.MAIN_HAND).getTag().getString("marker")),
                "Bucket metadata lost");
        helper.assertTrue(tank.exchange(player, InteractionHand.MAIN_HAND) && tank.fluidState().isEmpty()
                && player.getItemInHand(InteractionHand.MAIN_HAND).is(Items.LAVA_BUCKET), "Bucket refill failed");
        player.setPos(tank.getBlockPos().getX() + 100, tank.getBlockPos().getY(), tank.getBlockPos().getZ());
        helper.assertTrue(!tank.exchange(player, InteractionHand.MAIN_HAND) && tank.fluidState().isEmpty(), "Remote fill accepted");
        player.setPos(tank.getBlockPos().getX(), tank.getBlockPos().getY(), tank.getBlockPos().getZ());
        port(tank).fill(new FluidStack(Fluids.LAVA, tank.capacity() - 1), FluidAction.EXECUTE);
        helper.assertTrue(!tank.exchange(player, InteractionHand.MAIN_HAND)
                && tank.fluidState().getAmount() == tank.capacity() - 1, "Partial bucket silently consumed");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 30)
    public static void verticalTransferIsLaterAndConservesWithoutColumnScan(GameTestHelper helper) {
        var lower = place(helper, POSITION); var upper = place(helper, POSITION.above());
        port(upper).fill(new FluidStack(Fluids.WATER, 2_500), FluidAction.EXECUTE);
        helper.assertTrue(lower.fluidState().isEmpty(), "Reentrant/same-callback vertical transfer");
        helper.runAfterDelay(10, () -> {
            helper.assertTrue(lower.fluidState().getAmount() == 2_500 && upper.fluidState().isEmpty(), "Vertical transfer stalled/lost fluid");
            helper.assertTrue(lower.fluidState().getAmount() + upper.fluidState().getAmount() == 2_500, "Transfer duplicated fluid");
            helper.succeed();
        });
    }

    @GameTest(template = "empty", timeoutTicks = 20)
    public static void overflowSurvivesRootAndItemRoundTripAndRemainsDrainable(GameTestHelper helper) {
        var tank = place(helper, POSITION);
        CompoundTag outer = new CompoundTag();
        outer.put(TankSave.ROOT, TankSave.encode(new FluidStack(Fluids.WATER, 300_000))); tank.load(outer);
        helper.assertTrue(tank.overCapacity() && port(tank).fill(new FluidStack(Fluids.WATER, 1), FluidAction.EXECUTE) == 0,
                "Overflow was clamped or filled");
        var state = tank.getBlockState();
        var params = new LootParams.Builder(helper.getLevel()).withParameter(LootContextParams.BLOCK_STATE, state)
                .withParameter(LootContextParams.BLOCK_ENTITY, tank).withParameter(LootContextParams.TOOL, ItemStack.EMPTY)
                .withParameter(LootContextParams.ORIGIN, Vec3.atCenterOf(tank.getBlockPos()));
        var drops = state.getBlock().getDrops(state, params);
        helper.assertTrue(drops.size() == 1, "Tank did not have exactly one carrier");
        ItemStack item = ItemStack.of(drops.get(0).save(new CompoundTag()));
        helper.assertTrue(PressurizedTankItem.placeable(item)
                && TankSave.decode(item.getTag().get(TankSave.ROOT)).getAmount() == 300_000, "Dropped item changed overflow");
        port(tank).drain(1_000, FluidAction.EXECUTE);
        helper.assertTrue(tank.fluidState().getAmount() == 299_000, "Overflow cannot drain");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 20)
    public static void unsupportedRootRetainsVerbatimAndRefusesCapabilityAndOrdinaryRemoval(GameTestHelper helper) {
        var tank = place(helper, POSITION);
        CompoundTag future = TankSave.encode(new FluidStack(Fluids.WATER, 9_999));
        future.putInt("schema", 2); future.putString("extension", "retain");
        for (var raw : new net.minecraft.nbt.Tag[]{future, IntTag.valueOf(7)}) {
            CompoundTag outer = new CompoundTag(); outer.put(TankSave.ROOT, raw); tank.load(outer);
            helper.assertTrue(tank.repairRequired() && !tank.getCapability(ForgeCapabilities.FLUID_HANDLER).isPresent(),
                    "Unsupported tank exposes capability");
            helper.assertTrue(tank.saveWithoutMetadata().get(TankSave.ROOT).equals(raw), "Raw unsupported data changed");
            Player player = player(helper, tank);
            helper.assertTrue(!tank.getBlockState().getBlock().onDestroyedByPlayer(tank.getBlockState(), helper.getLevel(),
                    tank.getBlockPos(), player, true, tank.getBlockState().getFluidState()), "Ordinary break erased raw data");
        }
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 20)
    public static void stalePortAndOversizedNativeChunkWriteRefuse(GameTestHelper helper) {
        var tank = place(helper, POSITION); var stale = port(tank);
        tank.invalidateCaps(); tank.reviveCaps();
        helper.assertTrue(stale.fill(new FluidStack(Fluids.WATER, 1_000), FluidAction.EXECUTE) == 0, "Stale port still mutates");
        CompoundTag huge = new CompoundTag(); huge.putByteArray("payload", new byte[TankSave.MAX_BYTES]);
        CompoundTag outer = new CompoundTag(); outer.put(TankSave.ROOT, huge); tank.load(outer);
        var chunk = helper.getLevel().getChunkAt(tank.getBlockPos());
        CompoundTag outgoing = new CompoundTag(); var entries = new net.minecraft.nbt.ListTag();
        var entry = tank.saveWithFullMetadata(); entries.add(entry); outgoing.put("block_entities", entries);
        chunk.setUnsaved(false);
        try {
            TankProtection.chunkSave(new ChunkDataEvent.Save(chunk, helper.getLevel(), outgoing));
            helper.fail("Oversized native chunk write was admitted");
        } catch (IllegalStateException expected) {
            helper.assertTrue(chunk.isUnsaved() && tank.repairRequired(), "Chunk retry/raw state not retained");
        }
        // Explicit test-only repair after asserting the refusal; do not leave oversized data for GT shutdown saves.
        CompoundTag repaired = new CompoundTag(); repaired.put(TankSave.ROOT, TankSave.encode(FluidStack.EMPTY));
        tank.load(repaired); tank.setChanged();
        io.github.sunthemoon.advancedrocketrycommunity.persistence.GuardedChunkSaves.restoreGameTestFixture(helper.getLevel(), chunk.getPos());
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 20)
    public static void serializationCallbacksCannotReenterSimulationValidationOrExecution(GameTestHelper helper) {
        var tank = place(helper, POSITION); var handler = port(tank);
        handler.fill(new FluidStack(Fluids.WATER, 1_000), FluidAction.EXECUTE);
        FluidStack callbacks = new FluidStack(Fluids.WATER, 1_000) {
            @Override public CompoundTag writeToNBT(CompoundTag root) {
                helper.assertTrue(handler.drain(1_000, FluidAction.EXECUTE).isEmpty(), "Reentrant drain spent fluid");
                return super.writeToNBT(root);
            }
        };
        helper.assertTrue(handler.fill(callbacks, FluidAction.SIMULATE) == 1_000
                && tank.fluidState().getAmount() == 1_000, "Simulate callback changed snapshot");
        helper.assertTrue(handler.isFluidValid(0, callbacks) && tank.fluidState().getAmount() == 1_000,
                "Validation callback changed snapshot");
        helper.assertTrue(handler.fill(callbacks, FluidAction.EXECUTE) == 1_000
                && tank.fluidState().getAmount() == 2_000, "Execution callback changed debit");
        helper.succeed();
    }
}
