package io.github.sunthemoon.advancedrocketrycommunity.equipment.workstation;

import io.github.sunthemoon.advancedrocketrycommunity.ModIdentity;
import io.github.sunthemoon.advancedrocketrycommunity.atmosphere.device.GasChargePadBlockEntity;
import io.github.sunthemoon.advancedrocketrycommunity.atmosphere.device.GasPadState;
import io.github.sunthemoon.advancedrocketrycommunity.atmosphere.device.GasPadState.Canister;
import io.github.sunthemoon.advancedrocketrycommunity.equipment.suit.TankData.Gas;
import io.github.sunthemoon.advancedrocketrycommunity.fluid.ClassicFluids;
import io.github.sunthemoon.advancedrocketrycommunity.registry.ModBlocks;
import io.github.sunthemoon.advancedrocketrycommunity.registry.ModItems;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.IFluidHandler.FluidAction;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import net.minecraftforge.items.IItemHandler;

/** Exact adapter counts and complete pad roots; all loops and world queries are fixture-bounded. */
@GameTestHolder(ModIdentity.MOD_ID)
@PrefixGameTestTemplate(false)
public final class GasPadConservationGameTests {
    private static final List<Gas> GASES = List.of(Gas.OXYGEN, Gas.HYDROGEN);

    @GameTest(template = "empty", batch = "finite_pad_conservation", timeoutTicks = 100)
    public static void stackedInsertionConservesBothSlotsAndSimulation(GameTestHelper helper) {
        try (var f = new PadFixture(helper)) {
            for (Gas gas : GASES) {
                GasPadState expected = new GasPadState(gas, 3500, Canister.NONE, Canister.NONE);
                f.seed(expected);
                IItemHandler port = f.items();
                helper.assertTrue(port.getSlots() == 2, "Exactly two pad slots");
                for (int slot = 0; slot < 2; slot++) {
                    Item item = slot == 0 ? filled(gas) : ModItems.EMPTY_CANISTER.get();
                    ItemStack request = new ItemStack(item, 3);
                    helper.assertTrue(port.getSlotLimit(slot) == 1, "Exactly one item per slot");
                    exact(helper, port.insertItem(slot, request, true), item, 2, "Simulated remainder");
                    exact(helper, request, item, 3, "Simulation source");
                    root(helper, f.pad, expected);
                    exact(helper, port.insertItem(slot, request, false), item, 2, "Executed remainder");
                    exact(helper, request, item, 3, "Execution must not mutate caller stack");
                    expected = new GasPadState(gas, 3500, slot == 0 ? canister(gas) : expected.input(),
                            slot == 1 ? Canister.EMPTY : expected.output());
                    root(helper, f.pad, expected);
                    exact(helper, port.getStackInSlot(slot), item, 1, "Stored item");
                    port.getStackInSlot(slot).shrink(1);
                    root(helper, f.pad, expected);
                    for (boolean simulate : List.of(true, false)) {
                        exact(helper, port.insertItem(slot, request, simulate), item, 3, "Occupied slot remainder");
                        root(helper, f.pad, expected);
                    }
                }
            }
        }
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "finite_pad_conservation", timeoutTicks = 100)
    public static void extractionConservesRawAndProcessedSlotsAndCannotRepeat(GameTestHelper helper) {
        try (var f = new PadFixture(helper)) {
            for (Gas gas : GASES) {
                for (boolean processed : List.of(false, true)) {
                    GasPadState expected = new GasPadState(gas, 2500,
                            processed ? Canister.EMPTY : canister(gas), processed ? canister(gas) : Canister.EMPTY);
                    f.seed(expected);
                    IItemHandler port = f.items();
                    for (int slot = 0; slot < 2; slot++) {
                        Item item = (slot == 0) == processed ? ModItems.EMPTY_CANISTER.get() : filled(gas);
                        exact(helper, port.extractItem(slot, 0, false), item, 0, "Zero request");
                        root(helper, f.pad, expected);
                        exact(helper, port.extractItem(slot, 64, true), item, 1, "Simulated extraction");
                        root(helper, f.pad, expected);
                        exact(helper, port.extractItem(slot, 64, false), item, 1, "Executed extraction");
                        expected = new GasPadState(gas, 2500, slot == 0 ? Canister.NONE : expected.input(),
                                slot == 1 ? Canister.NONE : expected.output());
                        root(helper, f.pad, expected);
                        for (boolean simulate : List.of(true, false)) {
                            exact(helper, port.extractItem(slot, 64, simulate), item, 0, "Repeated extraction");
                            root(helper, f.pad, expected);
                        }
                    }
                }
            }
        }
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "finite_pad_conservation", timeoutTicks = 100)
    public static void survivalAndCreativeBlockUseConserveInsertionAndWithdrawal(GameTestHelper helper) {
        try (var f = new PadFixture(helper)) {
            for (GameType mode : List.of(GameType.SURVIVAL, GameType.CREATIVE)) {
                f.actor.player.gameMode.changeGameModeForPlayer(mode);
                for (Gas gas : GASES) {
                    for (int slot = 0; slot < 2; slot++) {
                        f.actor.player.getInventory().clearContent();
                        f.actor.player.setShiftKeyDown(false);
                        f.seed(new GasPadState(gas, 2500, Canister.NONE, Canister.NONE));
                        Item supplied = slot == 0 ? filled(gas) : ModItems.EMPTY_CANISTER.get();
                        f.actor.player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(supplied, 3));
                        helper.assertTrue(f.use(), "Block insertion refused: " + mode + " " + gas + " slot " + slot);
                        exact(helper, f.actor.player.getMainHandItem(), supplied, 2, "Player insertion remainder");
                        GasPadState expected = new GasPadState(gas, 2500, slot == 0 ? canister(gas) : Canister.NONE,
                                slot == 1 ? Canister.EMPTY : Canister.NONE);
                        root(helper, f.pad, expected);
                        helper.assertTrue(f.actor.player.getInventory().countItem(supplied) == 2, "Player inventory after insertion");
                        if (slot == 1) {
                            helper.assertTrue(f.pad.process(), "Output filling refused");
                            expected = new GasPadState(gas, 1500, Canister.NONE, canister(gas));
                            root(helper, f.pad, expected);
                        }
                        f.actor.player.getInventory().setItem(1, f.actor.player.getMainHandItem());
                        f.actor.player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
                        f.actor.player.setShiftKeyDown(slot == 0);
                        helper.assertTrue(f.use(), "Empty-hand block withdrawal refused");
                        exact(helper, f.actor.player.getMainHandItem(), filled(gas), 1, "Player withdrawal");
                        expected = new GasPadState(gas, slot == 0 ? 2500 : 1500, Canister.NONE, Canister.NONE);
                        root(helper, f.pad, expected);
                        helper.assertTrue(f.actor.player.getInventory().countItem(filled(gas)) == (slot == 0 ? 3 : 1)
                                && f.actor.player.getInventory().countItem(ModItems.EMPTY_CANISTER.get()) == (slot == 0 ? 0 : 2),
                                "Exact player inventory after withdrawal");
                        f.actor.player.getInventory().setItem(2, f.actor.player.getMainHandItem());
                        f.actor.player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
                        helper.assertTrue(!f.use(), "Empty slot withdrew twice");
                        exact(helper, f.actor.player.getMainHandItem(), supplied, 0, "Repeated player withdrawal");
                        root(helper, f.pad, expected);
                        helper.assertTrue(f.drops().isEmpty(), "Player interaction emitted an item entity");
                    }
                }
            }
        }
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "finite_pad_conservation", timeoutTicks = 100)
    public static void breakingDropsEachSlotOnceAndLosesOnlyResidualBank(GameTestHelper helper) {
        try (var f = new PadFixture(helper)) {
            for (Gas gas : GASES) {
                f.place();
                f.seed(new GasPadState(gas, 2500, canister(gas), Canister.EMPTY));
                helper.assertTrue(f.pad.process(), "Prepare distinct slot contents");
                root(helper, f.pad, new GasPadState(gas, 2500, Canister.EMPTY, canister(gas)));
                var block = f.pad.getBlockState();
                helper.assertTrue(f.actor.level.destroyBlock(f.pos, true, f.actor.player), "Actual block break refused");
                root(helper, f.pad, GasPadState.EMPTY);
                f.exactDrops(gas);
                f.pad.dropContents();
                block.getBlock().onRemove(block, f.actor.level, f.pos, Blocks.AIR.defaultBlockState(), false);
                helper.assertTrue(!f.actor.level.destroyBlock(f.pos, true, f.actor.player), "Removed block broke twice");
                f.exactDrops(gas);
                f.drops().forEach(ItemEntity::discard);
                f.place();
                root(helper, f.pad, GasPadState.EMPTY);
                helper.assertTrue(f.pad.getCapability(ForgeCapabilities.FLUID_HANDLER).orElseThrow(AssertionError::new)
                        .getFluidInTank(0).isEmpty(), "Residual bank was refunded into replacement");
            }
        }
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "finite_pad_conservation", timeoutTicks = 100)
    public static void staleCapabilitiesRefuseBothSlotsAndGasesAfterRevival(GameTestHelper helper) {
        try (var f = new PadFixture(helper)) {
            for (Gas gas : GASES) {
                IItemHandler stale = f.items();
                var fluid = f.pad.getCapability(ForgeCapabilities.FLUID_HANDLER).orElseThrow(AssertionError::new);
                f.pad.invalidateCaps();
                f.pad.reviveCaps();
                for (boolean occupied : List.of(false, true)) {
                    GasPadState expected = new GasPadState(gas, 2500, occupied ? canister(gas) : Canister.NONE,
                            occupied ? Canister.EMPTY : Canister.NONE);
                    f.seed(expected);
                    refuse(helper, f.pad, stale, gas, expected);
                    helper.assertTrue(fluid.fill(fluid(gas), FluidAction.EXECUTE) == 0, "Stale fluid fill");
                    root(helper, f.pad, expected);
                }
                exact(helper, f.items().extractItem(0, 1, false), filled(gas), 1, "Fresh capability must still work");
                root(helper, f.pad, new GasPadState(gas, 2500, Canister.NONE, Canister.EMPTY));
            }
        }
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "finite_pad_conservation", timeoutTicks = 100)
    public static void unloadedPositionCapabilitiesRefuseWithoutLoadingChunk(GameTestHelper helper) {
        var level = helper.getLevel();
        BlockPos unloaded = new BlockPos(16000000, 196, 16000000);
        helper.assertTrue(level.getChunkSource().getChunkNow(unloaded.getX() >> 4, unloaded.getZ() >> 4) == null,
                "Unloaded fixture coordinate already loaded");
        var pad = new GasChargePadBlockEntity(unloaded, ModBlocks.GAS_CHARGE_PAD.get().defaultBlockState());
        pad.setLevel(level);
        try {
            var items = pad.getCapability(ForgeCapabilities.ITEM_HANDLER).orElseThrow(AssertionError::new);
            var fluids = pad.getCapability(ForgeCapabilities.FLUID_HANDLER).orElseThrow(AssertionError::new);
            for (Gas gas : GASES) {
                for (boolean occupied : List.of(false, true)) {
                    GasPadState expected = new GasPadState(gas, 2500, occupied ? canister(gas) : Canister.NONE,
                            occupied ? Canister.EMPTY : Canister.NONE);
                    seed(pad, expected);
                    refuse(helper, pad, items, gas, expected);
                    for (FluidAction action : FluidAction.values()) {
                        helper.assertTrue(fluids.fill(fluid(gas), action) == 0, "Unloaded fluid fill");
                        root(helper, pad, expected);
                    }
                }
            }
            helper.assertTrue(level.getChunkSource().getChunkNow(unloaded.getX() >> 4, unloaded.getZ() >> 4) == null,
                    "Capability access loaded an arbitrary chunk");
        } finally { pad.invalidateCaps(); }
        helper.succeed();
    }

    private static void refuse(GameTestHelper helper, GasChargePadBlockEntity pad, IItemHandler port, Gas gas, GasPadState expected) {
        for (int slot = 0; slot < 2; slot++) {
            Item item = slot == 0 ? filled(gas) : ModItems.EMPTY_CANISTER.get();
            for (boolean simulate : List.of(true, false)) {
                ItemStack request = new ItemStack(item, 3);
                exact(helper, port.insertItem(slot, request, simulate), item, 3, "Unavailable insertion remainder");
                exact(helper, request, item, 3, "Unavailable source");
                exact(helper, port.extractItem(slot, 64, simulate), item, 0, "Unavailable extraction");
                root(helper, pad, expected);
            }
            exact(helper, port.getStackInSlot(slot), item, 0, "Unavailable inspection");
        }
    }
    private static Item filled(Gas gas) { return gas == Gas.OXYGEN ? ModItems.OXYGEN_CANISTER.get() : ModItems.HYDROGEN_CANISTER.get(); }
    private static Canister canister(Gas gas) { return Canister.valueOf(gas.name()); }
    private static FluidStack fluid(Gas gas) {
        return new FluidStack(gas == Gas.OXYGEN ? ClassicFluids.OXYGEN.get() : ClassicFluids.HYDROGEN.get(), 1000);
    }
    private static void exact(GameTestHelper helper, ItemStack actual, Item item, int count, String operation) {
        helper.assertTrue(actual.getCount() == count && (count == 0 ? actual.isEmpty() : actual.is(item) && !actual.hasTag()),
                operation + ": expected " + count + " of " + item + ", got " + actual);
    }
    private static void seed(GasChargePadBlockEntity pad, GasPadState state) {
        CompoundTag outer = new CompoundTag(); outer.put(GasChargePadBlockEntity.KEY, state.encode()); pad.load(outer);
    }
    private static void root(GameTestHelper helper, GasChargePadBlockEntity pad, GasPadState expected) {
        CompoundTag outer = new CompoundTag(); outer.put(GasChargePadBlockEntity.KEY, expected.encode());
        helper.assertTrue(pad.state().equals(expected) && pad.saveWithoutMetadata().equals(outer),
                "Complete bank/slot root mismatch: expected " + outer + ", got " + pad.saveWithoutMetadata());
    }

    private static final class PadFixture implements AutoCloseable {
        final EquipmentFixture actor;
        final BlockPos pos;
        final AABB bounds;
        GasChargePadBlockEntity pad;
        PadFixture(GameTestHelper helper) { actor = new EquipmentFixture(helper); pos = actor.position; bounds = actor.bounds; place(); }
        void place() {
            actor.level.setBlock(pos, ModBlocks.GAS_CHARGE_PAD.get().defaultBlockState(), Block.UPDATE_ALL);
            pad = (GasChargePadBlockEntity) actor.level.getBlockEntity(pos);
            actor.helper.assertTrue(pad != null, "Pad fixture placement");
        }
        void seed(GasPadState state) { GasPadConservationGameTests.seed(pad, state); root(actor.helper, pad, state); }
        IItemHandler items() { return pad.getCapability(ForgeCapabilities.ITEM_HANDLER).orElseThrow(AssertionError::new); }
        boolean use() {
            return actor.player.gameMode.useItemOn(actor.player, actor.level, actor.player.getMainHandItem(), InteractionHand.MAIN_HAND,
                    new BlockHitResult(pos.getCenter(), Direction.UP, pos, false)).consumesAction();
        }
        List<ItemEntity> drops() {
            return actor.level.getEntitiesOfClass(ItemEntity.class, bounds).stream()
                    .filter(entity -> !actor.initialDrops.contains(entity.getUUID())).toList();
        }
        void exactDrops(Gas gas) {
            var drops = drops();
            actor.helper.assertTrue(drops.size() == 3, "Exactly block, input and output entities");
            for (Item item : List.of(ModBlocks.GAS_CHARGE_PAD.get().asItem(), ModItems.EMPTY_CANISTER.get(), filled(gas))) {
                actor.helper.assertTrue(drops.stream().filter(entity -> entity.getItem().is(item)).count() == 1, "Exactly one entity for " + item);
                exact(actor.helper, drops.stream().filter(entity -> entity.getItem().is(item)).findFirst().orElseThrow().getItem(), item, 1, "Break drop");
            }
        }
        @Override public void close() { seed(GasPadState.EMPTY); actor.close(); }
    }
}
