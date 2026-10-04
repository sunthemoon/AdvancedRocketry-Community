package io.github.sunthemoon.advancedrocketrycommunity.machine.electrolyzer;

import io.github.sunthemoon.advancedrocketrycommunity.machine.menu.MachineMenuOpening;
import io.github.sunthemoon.advancedrocketrycommunity.machine.menu.RecipeMenuReason;
import io.github.sunthemoon.advancedrocketrycommunity.registry.ModBlocks;
import io.github.sunthemoon.advancedrocketrycommunity.registry.ModItems;
import io.github.sunthemoon.advancedrocketrycommunity.registry.ModMenuTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.IItemHandlerModifiable;
import net.minecraftforge.items.ItemHandlerHelper;
import net.minecraftforge.items.ItemStackHandler;
import net.minecraftforge.items.SlotItemHandler;

public final class ElectrolyzerMenu extends AbstractContainerMenu {
    private static final int MACHINE_SLOT_COUNT = ElectrolyzerBlockEntity.SLOT_COUNT;
    private static final int PLAYER_SLOT_START = MACHINE_SLOT_COUNT;
    private static final int PLAYER_SLOT_END = PLAYER_SLOT_START + 27;
    private static final int HOTBAR_SLOT_END = PLAYER_SLOT_END + 9;

    private final ContainerLevelAccess access;
    private final ContainerData data;
    private final IItemHandler machineInventory;

    public ElectrolyzerMenu(int containerId, Inventory playerInventory, FriendlyByteBuf buffer) {
        this(containerId, playerInventory, requireMachine(playerInventory,
                MachineMenuOpening.read(buffer, ElectrolyzerBlockEntity.MENU_DATA_COUNT)));
    }

    private ElectrolyzerMenu(
            int containerId,
            Inventory playerInventory,
            ElectrolyzerBlockEntity machine
    ) {
        this(containerId, playerInventory, machine, new SimpleContainerData(ElectrolyzerBlockEntity.MENU_DATA_COUNT));
    }

    public ElectrolyzerMenu(
            int containerId,
            Inventory playerInventory,
            ElectrolyzerBlockEntity machine,
            ContainerData data
    ) {
        this(
                containerId,
                playerInventory,
                machine.menuInventory(),
                data,
                ContainerLevelAccess.create(machine.getLevel(), machine.getBlockPos())
        );
    }

    private ElectrolyzerMenu(
            int containerId,
            Inventory playerInventory,
            IItemHandler machineInventory,
            ContainerData data,
            ContainerLevelAccess access
    ) {
        super(ModMenuTypes.ELECTROLYZER.get(), containerId);
        checkContainerDataCount(data, ElectrolyzerBlockEntity.MENU_DATA_COUNT);
        MachineMenuOpening.requireExactDataCount(data, ElectrolyzerBlockEntity.MENU_DATA_COUNT);
        this.data = data;
        this.access = access;
        this.machineInventory = machineInventory;

        addSlot(new MachineSlot(machineInventory, ElectrolyzerBlockEntity.SLOT_INPUT, 44, 35));
        addSlot(new MachineSlot(machineInventory, ElectrolyzerBlockEntity.SLOT_CHARGE, 44, 59));
        addSlot(new OutputSlot(machineInventory, ElectrolyzerBlockEntity.SLOT_HYDROGEN, 116, 35));
        addSlot(new OutputSlot(machineInventory, ElectrolyzerBlockEntity.SLOT_OXYGEN, 140, 35));
        addPlayerInventory(playerInventory);
        addDataSlots(data);
    }

    private static ElectrolyzerBlockEntity requireMachine(Inventory playerInventory, BlockPos position) {
        BlockEntity blockEntity = playerInventory.player.level().getBlockEntity(position);
        if (blockEntity instanceof ElectrolyzerBlockEntity machine) {
            return machine;
        }
        throw new IllegalStateException("Electrolyzer menu opened without its block entity at " + position);
    }

    private void addPlayerInventory(Inventory inventory) {
        for (int row = 0; row < 3; row++) {
            for (int column = 0; column < 9; column++) {
                addSlot(new net.minecraft.world.inventory.Slot(
                        inventory,
                        column + row * 9 + 9,
                        8 + column * 18,
                        94 + row * 18
                ));
            }
        }
        for (int column = 0; column < 9; column++) {
            addSlot(new net.minecraft.world.inventory.Slot(inventory, column, 8 + column * 18, 152));
        }
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        ItemStack empty = ItemStack.EMPTY;
        if (index < 0 || index >= slots.size()) {
            return empty;
        }
        net.minecraft.world.inventory.Slot sourceSlot = slots.get(index);
        if (!sourceSlot.hasItem() || !sourceSlot.mayPickup(player)) {
            return empty;
        }

        ItemStack source = sourceSlot.getItem();
        ItemStack original = source.copy();
        boolean moved;
        if (index < MACHINE_SLOT_COUNT) {
            moved = moveItemStackTo(source, PLAYER_SLOT_START, HOTBAR_SLOT_END, true);
        } else if (source.is(ModItems.EMPTY_CANISTER.get())) {
            moved = insertIntoMachine(source, ElectrolyzerBlockEntity.SLOT_INPUT);
        } else if (source.is(Items.REDSTONE)) {
            moved = insertIntoMachine(source, ElectrolyzerBlockEntity.SLOT_CHARGE);
        } else if (index < PLAYER_SLOT_END) {
            moved = moveItemStackTo(source, PLAYER_SLOT_END, HOTBAR_SLOT_END, false);
        } else {
            moved = moveItemStackTo(source, PLAYER_SLOT_START, PLAYER_SLOT_END, false);
        }
        if (!moved) {
            return empty;
        }

        sourceSlot.set(source.isEmpty() ? ItemStack.EMPTY : source);
        sourceSlot.onTake(player, source);
        return original;
    }

    private boolean insertIntoMachine(ItemStack source, int slot) {
        ItemStack remainder = machineInventory.insertItem(slot, source.copy(), false);
        int inserted = source.getCount() - remainder.getCount();
        if (inserted <= 0) {
            return false;
        }
        source.shrink(inserted);
        return true;
    }

    @Override
    public boolean stillValid(Player player) {
        return stillValid(access, player, ModBlocks.ELECTROLYZER.get());
    }

    public int progress() {
        return data.get(0);
    }

    public int totalProcessingTicks() {
        return data.get(1);
    }

    public int energyStored() {
        return data.get(2);
    }

    public int energyCapacity() {
        return data.get(3);
    }

    public int waterAmount() {
        return data.get(4);
    }

    public int waterCapacity() {
        return data.get(5);
    }

    public ElectrolyzerStatus status() {
        return ElectrolyzerStatus.fromNetworkId(data.get(6));
    }

    public RecipeMenuReason recipeReason() {
        if (status() == ElectrolyzerStatus.UNSUPPORTED_DATA) {
            return RecipeMenuReason.NONE;
        }
        return RecipeMenuReason.fromNetworkId(data.get(7));
    }

    private static class MachineSlot extends SlotItemHandler {
        private MachineSlot(IItemHandler handler, int index, int x, int y) {
            super(handler, index, x, y);
        }

        @Override
        public void set(ItemStack replacement) {
            replace(replacement);
            setChanged();
        }

        @Override
        public void initialize(ItemStack replacement) {
            replace(replacement);
            setChanged();
        }

        private void replace(ItemStack replacement) {
            IItemHandler handler = getItemHandler();
            int slot = getSlotIndex();
            if (handler instanceof IItemHandlerModifiable modifiable) {
                modifiable.setStackInSlot(slot, replacement);
                return;
            }

            ItemStack current = handler.getStackInSlot(slot);
            if (ItemStack.matches(current, replacement)) {
                return;
            }
            if (replacement.isEmpty()) {
                handler.extractItem(slot, current.getCount(), false);
                return;
            }
            if (current.isEmpty()) {
                handler.insertItem(slot, replacement, false);
                return;
            }
            if (!ItemHandlerHelper.canItemStacksStack(current, replacement)) {
                return;
            }
            int difference = replacement.getCount() - current.getCount();
            if (difference > 0) {
                ItemStack addition = replacement.copy();
                addition.setCount(difference);
                handler.insertItem(slot, addition, false);
            } else if (difference < 0) {
                handler.extractItem(slot, -difference, false);
            }
        }
    }

    private static final class OutputSlot extends MachineSlot {
        private OutputSlot(IItemHandler handler, int index, int x, int y) {
            super(handler, index, x, y);
        }

        @Override
        public boolean mayPlace(ItemStack stack) {
            return false;
        }
    }
}
