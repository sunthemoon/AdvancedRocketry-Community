package io.github.sunthemoon.advancedrocketrycommunity.gametest;

import io.github.sunthemoon.advancedrocketrycommunity.ModIdentity;
import io.github.sunthemoon.advancedrocketrycommunity.machine.electrolyzer.ElectrolyzerBlockEntity;
import io.github.sunthemoon.advancedrocketrycommunity.machine.electrolyzer.ElectrolyzerMenu;
import io.github.sunthemoon.advancedrocketrycommunity.machine.menu.MachineMenuOpening;
import io.github.sunthemoon.advancedrocketrycommunity.registry.ModBlocks;
import io.github.sunthemoon.advancedrocketrycommunity.registry.ModItems;
import io.netty.buffer.Unpooled;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.NonNullList;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.ContainerSynchronizer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.material.Fluids;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.IFluidHandler;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/** Exercises both ends of vanilla menu synchronization without a client runtime or socket. */
@GameTestHolder(ModIdentity.MOD_ID)
@PrefixGameTestTemplate(false)
public final class ElectrolyzerMenuSyncGameTests {
    private ElectrolyzerMenuSyncGameTests() { }

    @GameTest(template = "empty", timeoutTicks = 20)
    public static void consumedChargeSynchronizesAndReopeningClearsStaleSlots(GameTestHelper helper) {
        ElectrolyzerBlockEntity machine = place(helper, BlockPos.ZERO);
        // A separate BE stands in for the client's loaded BE, never sharing the server's storage.
        ElectrolyzerBlockEntity clientMachine = place(helper, new BlockPos(2, 0, 0));
        Player player = helper.makeMockPlayer();
        Player clientPlayer = helper.makeMockPlayer();
        helper.assertTrue(machine.menuInventory().insertItem(
                ElectrolyzerBlockEntity.SLOT_CHARGE, new ItemStack(Items.REDSTONE, 2), false).isEmpty(),
                "Could not seed two charge items");
        ElectrolyzerMenu server = serverMenu(machine, player);
        ElectrolyzerMenu client = clientMenu(clientMachine, clientPlayer);
        Loopback sync = new Loopback(client);
        server.setSynchronizer(sync);
        assertSlots(helper, machine, server, client, "initial charge");

        tick(helper, machine, 1);
        helper.assertTrue(machine.energyStored() == ElectrolyzerBlockEntity.REDSTONE_ENERGY,
                "First charge item did not convert exactly once");
        server.broadcastChanges();
        assertSlots(helper, machine, server, client, "first consumed charge");
        tick(helper, machine, 1);
        helper.assertTrue(machine.energyStored() == 2 * ElectrolyzerBlockEntity.REDSTONE_ENERGY,
                "Second charge item did not convert exactly once");
        server.broadcastChanges();
        assertSlots(helper, machine, server, client, "empty charge");
        helper.assertTrue(sync.slotChanges >= 2, "Charge changes did not use incremental synchronization");

        // Leave a stale local prediction, then reopen on the same client BE.
        client.getSlot(ElectrolyzerBlockEntity.SLOT_CHARGE).set(new ItemStack(Items.REDSTONE, 2));
        ElectrolyzerMenu reopened = clientMenu(clientMachine, clientPlayer);
        server.removed(player);
        server = serverMenu(machine, player);
        server.setSynchronizer(new Loopback(reopened));
        assertSlots(helper, machine, server, reopened, "reopened empty charge");
        server.clicked(ElectrolyzerBlockEntity.SLOT_CHARGE, 0, ClickType.PICKUP, player);
        helper.assertTrue(server.getCarried().isEmpty() && player.getInventory().isEmpty(),
                "Clicking consumed charge created an item");
        helper.assertTrue(machine.energyStored() == 4_000, "Synchronization changed server energy");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 20)
    public static void producedOutputsSynchronizeInitiallyIncrementallyAndAfterPickup(GameTestHelper helper) {
        ElectrolyzerBlockEntity machine = place(helper, BlockPos.ZERO);
        ElectrolyzerBlockEntity clientMachine = place(helper, new BlockPos(2, 0, 0));
        Player player = helper.makeMockPlayer();
        Player clientPlayer = helper.makeMockPlayer();
        helper.assertTrue(machine.menuInventory().insertItem(ElectrolyzerBlockEntity.SLOT_INPUT,
                new ItemStack(ModItems.EMPTY_CANISTER.get(), 2), false).isEmpty(), "Could not seed canisters");
        helper.assertTrue(machine.getCapability(ForgeCapabilities.FLUID_HANDLER, Direction.NORTH)
                .orElseThrow(() -> new IllegalStateException("Missing water capability"))
                .fill(new FluidStack(Fluids.WATER, 1_000), IFluidHandler.FluidAction.EXECUTE) == 1_000,
                "Could not seed water");
        var energy = machine.getCapability(ForgeCapabilities.ENERGY, Direction.NORTH)
                .orElseThrow(() -> new IllegalStateException("Missing energy capability"));
        helper.assertTrue(energy.receiveEnergy(1_000, false) == 1_000
                && energy.receiveEnergy(1_000, false) == 1_000, "Could not seed process energy");
        ElectrolyzerMenu server = serverMenu(machine, player);
        ElectrolyzerMenu client = clientMenu(clientMachine, clientPlayer);
        Loopback sync = new Loopback(client);
        server.setSynchronizer(sync);
        assertSlots(helper, machine, server, client, "initial inputs");

        // Advance only the server machine: the stand-in client BE must not process its display state.
        tick(helper, machine, 110);
        helper.assertTrue(machine.menuInventory().getStackInSlot(ElectrolyzerBlockEntity.SLOT_HYDROGEN)
                .is(ModItems.HYDROGEN_CANISTER.get()), "Server did not produce hydrogen");
        helper.assertTrue(machine.menuInventory().getStackInSlot(ElectrolyzerBlockEntity.SLOT_OXYGEN)
                .is(ModItems.OXYGEN_CANISTER.get()), "Server did not produce oxygen");
        server.broadcastChanges();
        assertSlots(helper, machine, server, client, "produced outputs");
        helper.assertTrue(sync.slotChanges >= 3, "Input consumption and outputs did not use slot deltas");

        ElectrolyzerMenu reopened = clientMenu(clientMachine, clientPlayer);
        server.removed(player);
        server = serverMenu(machine, player);
        server.setSynchronizer(new Loopback(reopened));
        assertSlots(helper, machine, server, reopened, "reopened outputs");
        helper.assertTrue(machine.waterAmount() == 0 && machine.energyStored() == 0,
                "Process did not consume exactly 1,000 mB and 2,000 FE");

        server.clicked(ElectrolyzerBlockEntity.SLOT_HYDROGEN, 0, ClickType.PICKUP, player);
        helper.assertTrue(server.getCarried().is(ModItems.HYDROGEN_CANISTER.get())
                && server.getCarried().getCount() == 1, "Output pickup did not carry exactly one hydrogen canister");
        server.broadcastChanges();
        assertSlots(helper, machine, server, reopened, "picked-up hydrogen");
        helper.assertTrue(ItemStack.matches(server.getCarried(), reopened.getCarried()),
                "Carried output did not synchronize");
        server.clicked(ElectrolyzerBlockEntity.SLOT_HYDROGEN, 0, ClickType.PICKUP, player);
        helper.assertTrue(server.getCarried().getCount() == 1
                && machine.menuInventory().getStackInSlot(ElectrolyzerBlockEntity.SLOT_HYDROGEN).isEmpty(),
                "Output slot accepted reinsertion or duplicated hydrogen");
        helper.assertTrue(server.quickMoveStack(player, ElectrolyzerBlockEntity.SLOT_OXYGEN)
                .is(ModItems.OXYGEN_CANISTER.get()), "Output shift-click failed");
        helper.assertTrue(server.quickMoveStack(player, ElectrolyzerBlockEntity.SLOT_OXYGEN).isEmpty(),
                "Output shift-click extracted the same oxygen twice");
        server.broadcastChanges();
        assertSlots(helper, machine, server, reopened, "extracted outputs");
        helper.assertTrue(player.getInventory().countItem(ModItems.OXYGEN_CANISTER.get()) == 1
                && player.getInventory().countItem(ModItems.HYDROGEN_CANISTER.get()) == 0
                && server.getCarried().getCount() == 1, "Two input canisters were not conserved at pickup");

        ElectrolyzerMenu afterPickup = clientMenu(clientMachine, clientPlayer);
        server.setSynchronizer(new Loopback(afterPickup));
        assertSlots(helper, machine, server, afterPickup, "reopened extracted outputs");
        helper.assertTrue(machine.menuInventory().getStackInSlot(ElectrolyzerBlockEntity.SLOT_HYDROGEN).isEmpty()
                && machine.menuInventory().getStackInSlot(ElectrolyzerBlockEntity.SLOT_OXYGEN).isEmpty(),
                "Reopening changed authoritative output storage");
        helper.succeed();
    }

    private static ElectrolyzerBlockEntity place(GameTestHelper helper, BlockPos relative) {
        helper.setBlock(relative, ModBlocks.ELECTROLYZER.get());
        return (ElectrolyzerBlockEntity) helper.getBlockEntity(relative);
    }

    private static ElectrolyzerMenu serverMenu(ElectrolyzerBlockEntity machine, Player player) {
        return (ElectrolyzerMenu) machine.createMenu(1, player.getInventory(), player);
    }

    private static ElectrolyzerMenu clientMenu(ElectrolyzerBlockEntity machine, Player player) {
        FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());
        try {
            MachineMenuOpening.write(buffer, machine.getBlockPos(), ElectrolyzerBlockEntity.MENU_DATA_COUNT);
            return new ElectrolyzerMenu(1, player.getInventory(), buffer);
        } finally {
            buffer.release();
        }
    }

    private static void tick(GameTestHelper helper, ElectrolyzerBlockEntity machine, int count) {
        for (int tick = 0; tick < count; tick++) {
            ElectrolyzerBlockEntity.serverTick(helper.getLevel(), machine.getBlockPos(),
                    helper.getLevel().getBlockState(machine.getBlockPos()), machine);
        }
    }

    private static void assertSlots(GameTestHelper helper, ElectrolyzerBlockEntity machine,
                                   ElectrolyzerMenu server, ElectrolyzerMenu client, String stage) {
        for (int slot = 0; slot < ElectrolyzerBlockEntity.SLOT_COUNT; slot++) {
            ItemStack stored = machine.menuInventory().getStackInSlot(slot);
            helper.assertTrue(ItemStack.matches(stored, server.getSlot(slot).getItem()),
                    stage + ": server slot " + slot + " does not view machine storage");
            helper.assertTrue(ItemStack.matches(stored, client.getSlot(slot).getItem()),
                    stage + ": client slot " + slot + " is " + client.getSlot(slot).getItem()
                            + ", server stores " + stored);
        }
    }

    /** Same delivery methods used by the client packet listener for ServerPlayer's container synchronizer. */
    private static final class Loopback implements ContainerSynchronizer {
        private final ElectrolyzerMenu client;
        private int slotChanges;

        private Loopback(ElectrolyzerMenu client) {
            this.client = client;
        }

        @Override
        public void sendInitialData(AbstractContainerMenu source, NonNullList<ItemStack> items,
                                    ItemStack carried, int[] data) {
            client.initializeContents(source.incrementStateId(), items.stream().map(ItemStack::copy).toList(),
                    carried.copy());
            for (int index = 0; index < data.length; index++) {
                client.setData(index, data[index]);
            }
        }

        @Override
        public void sendSlotChange(AbstractContainerMenu source, int slot, ItemStack stack) {
            slotChanges++;
            client.setItem(slot, source.incrementStateId(), stack.copy());
        }

        @Override
        public void sendCarriedChange(AbstractContainerMenu source, ItemStack stack) {
            client.setCarried(stack.copy());
        }

        @Override
        public void sendDataChange(AbstractContainerMenu source, int index, int value) {
            client.setData(index, value);
        }
    }
}
