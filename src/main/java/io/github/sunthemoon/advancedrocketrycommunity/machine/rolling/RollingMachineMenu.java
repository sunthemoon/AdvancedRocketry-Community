package io.github.sunthemoon.advancedrocketrycommunity.machine.rolling;

import io.github.sunthemoon.advancedrocketrycommunity.machine.multiblock.lifecycle.MultiblockFormationState;
import io.github.sunthemoon.advancedrocketrycommunity.machine.multiblock.pattern.PatternDiagnosticReason;
import io.github.sunthemoon.advancedrocketrycommunity.machine.multiblock.pattern.PatternPosition;
import io.github.sunthemoon.advancedrocketrycommunity.machine.multiblock.pattern.PatternValidationStatus;
import io.github.sunthemoon.advancedrocketrycommunity.machine.process.ProcessFailureCode;
import io.github.sunthemoon.advancedrocketrycommunity.machine.process.ProcessMachineState;
import io.github.sunthemoon.advancedrocketrycommunity.registry.ModBlocks;
import io.github.sunthemoon.advancedrocketrycommunity.registry.ModMenuTypes;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.IItemHandlerModifiable;
import net.minecraftforge.items.ItemHandlerHelper;
import net.minecraftforge.items.ItemStackHandler;
import net.minecraftforge.items.SlotItemHandler;

/** Server-authoritative Rolling Machine menu with two generation-aware port slots. */
public final class RollingMachineMenu extends AbstractContainerMenu {
    public static final int SLOT_INPUT = 0;
    public static final int SLOT_OUTPUT = 1;
    public static final int MACHINE_SLOT_COUNT = 2;

    private static final int PLAYER_SLOT_START = MACHINE_SLOT_COUNT;
    private static final int PLAYER_SLOT_END = PLAYER_SLOT_START + 27;
    private static final int HOTBAR_SLOT_END = PLAYER_SLOT_END + 9;

    private final ContainerLevelAccess access;
    private final ContainerData data;
    private final IItemHandler machineItems;

    public RollingMachineMenu(int containerId, Inventory playerInventory, FriendlyByteBuf buffer) {
        this(
                containerId,
                playerInventory,
                new ItemStackHandler(MACHINE_SLOT_COUNT),
                new SimpleContainerData(RollingMachineMenuData.COUNT),
                requireAccess(playerInventory, buffer.readBlockPos())
        );
    }

    RollingMachineMenu(
            int containerId,
            Inventory playerInventory,
            RollingMachineBlockEntity controller,
            ContainerData data
    ) {
        this(
                containerId,
                playerInventory,
                new RollingMachineMenuItemHandler(controller),
                data,
                ContainerLevelAccess.create(
                        java.util.Objects.requireNonNull(controller.getLevel(), "controller level"),
                        controller.getBlockPos()
                )
        );
    }

    private RollingMachineMenu(
            int containerId,
            Inventory playerInventory,
            IItemHandler machineItems,
            ContainerData data,
            ContainerLevelAccess access
    ) {
        super(ModMenuTypes.ROLLING_MACHINE.get(), containerId);
        checkContainerDataCount(data, RollingMachineMenuData.COUNT);
        this.data = data;
        this.access = access;
        this.machineItems = machineItems;

        addSlot(new PortSlot(machineItems, SLOT_INPUT, 27, 39));
        addSlot(new OutputSlot(machineItems, SLOT_OUTPUT, 151, 39));
        addPlayerInventory(playerInventory);
        addDataSlots(data);
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        if (index < 0 || index >= slots.size()) {
            return ItemStack.EMPTY;
        }
        net.minecraft.world.inventory.Slot sourceSlot = slots.get(index);
        if (!sourceSlot.hasItem()) {
            return ItemStack.EMPTY;
        }

        ItemStack source = sourceSlot.getItem();
        ItemStack original = source.copy();
        boolean moved;
        if (index < MACHINE_SLOT_COUNT) {
            moved = moveItemStackTo(source, PLAYER_SLOT_START, HOTBAR_SLOT_END, true);
        } else {
            ItemStack remainder = machineItems.insertItem(SLOT_INPUT, source.copy(), false);
            int inserted = source.getCount() - remainder.getCount();
            moved = inserted > 0;
            if (moved) {
                source.shrink(inserted);
            }
            if (!moved && index < PLAYER_SLOT_END) {
                moved = moveItemStackTo(source, PLAYER_SLOT_END, HOTBAR_SLOT_END, false);
            } else if (!moved) {
                moved = moveItemStackTo(source, PLAYER_SLOT_START, PLAYER_SLOT_END, false);
            }
        }
        if (!moved) {
            return ItemStack.EMPTY;
        }
        sourceSlot.set(source.isEmpty() ? ItemStack.EMPTY : source);
        sourceSlot.onTake(player, source);
        return original;
    }

    @Override
    public boolean stillValid(Player player) {
        return stillValid(access, player, ModBlocks.ROLLING_MACHINE.get());
    }

    public MultiblockFormationState formationState() {
        return RollingMachineMenuWire.formation(data.get(RollingMachineMenuData.FORMATION));
    }

    public Optional<PatternValidationStatus> validationStatus() {
        return RollingMachineMenuWire.validation(data.get(RollingMachineMenuData.VALIDATION));
    }

    public Optional<PatternDiagnosticReason> diagnosticReason() {
        if (data.get(RollingMachineMenuData.DIAGNOSTIC_PRESENT) == 0) {
            return Optional.empty();
        }
        return RollingMachineMenuWire.diagnostic(data.get(RollingMachineMenuData.DIAGNOSTIC_REASON));
    }

    public Optional<PatternPosition> diagnosticLocalPosition() {
        if (data.get(RollingMachineMenuData.DIAGNOSTIC_PRESENT) == 0) {
            return Optional.empty();
        }
        return Optional.of(new PatternPosition(
                data.get(RollingMachineMenuData.DIAGNOSTIC_LOCAL_X),
                data.get(RollingMachineMenuData.DIAGNOSTIC_LOCAL_Y),
                data.get(RollingMachineMenuData.DIAGNOSTIC_LOCAL_Z)
        ));
    }

    public Optional<BlockPos> diagnosticWorldPosition() {
        if (data.get(RollingMachineMenuData.DIAGNOSTIC_PRESENT) == 0) {
            return Optional.empty();
        }
        return Optional.of(new BlockPos(
                joined(RollingMachineMenuData.DIAGNOSTIC_WORLD_X_LOW,
                        RollingMachineMenuData.DIAGNOSTIC_WORLD_X_HIGH),
                joined(RollingMachineMenuData.DIAGNOSTIC_WORLD_Y_LOW,
                        RollingMachineMenuData.DIAGNOSTIC_WORLD_Y_HIGH),
                joined(RollingMachineMenuData.DIAGNOSTIC_WORLD_Z_LOW,
                        RollingMachineMenuData.DIAGNOSTIC_WORLD_Z_HIGH)
        ));
    }

    public ProcessMachineState processState() {
        return RollingMachineMenuWire.processState(data.get(RollingMachineMenuData.PROCESS_STATE));
    }

    public ProcessFailureCode processFailure() {
        return RollingMachineMenuWire.failure(data.get(RollingMachineMenuData.PROCESS_FAILURE));
    }

    public int progress() {
        return joined(RollingMachineMenuData.PROGRESS_LOW, RollingMachineMenuData.PROGRESS_HIGH);
    }

    public int totalProcessingTicks() {
        return joined(RollingMachineMenuData.TOTAL_LOW, RollingMachineMenuData.TOTAL_HIGH);
    }

    public int energyStored() {
        return data.get(RollingMachineMenuData.ENERGY) & 0xFFFF;
    }

    public int energyCapacity() {
        return data.get(RollingMachineMenuData.ENERGY_CAPACITY) & 0xFFFF;
    }

    public int waterAmount() {
        return data.get(RollingMachineMenuData.WATER) & 0xFFFF;
    }

    public int waterCapacity() {
        return data.get(RollingMachineMenuData.WATER_CAPACITY) & 0xFFFF;
    }

    public int inspectedCells() {
        return data.get(RollingMachineMenuData.INSPECTED_CELLS) & 0xFFFF;
    }

    private void addPlayerInventory(Inventory inventory) {
        for (int row = 0; row < 3; row++) {
            for (int column = 0; column < 9; column++) {
                addSlot(new net.minecraft.world.inventory.Slot(
                        inventory,
                        column + row * 9 + 9,
                        17 + column * 18,
                        129 + row * 18
                ));
            }
        }
        for (int column = 0; column < 9; column++) {
            addSlot(new net.minecraft.world.inventory.Slot(inventory, column, 17 + column * 18, 187));
        }
    }

    private int joined(int lowIndex, int highIndex) {
        return RollingMachineMenuWire.joinInt(data.get(lowIndex), data.get(highIndex));
    }

    private static ContainerLevelAccess requireAccess(Inventory inventory, BlockPos position) {
        Level level = inventory.player.level();
        BlockEntity blockEntity = level.getBlockEntity(position);
        if (!(blockEntity instanceof RollingMachineBlockEntity)) {
            throw new IllegalStateException(
                    "Rolling Machine menu opened without its block entity at " + position
            );
        }
        return ContainerLevelAccess.create(level, position);
    }

    private static class PortSlot extends SlotItemHandler {
        private PortSlot(IItemHandler handler, int index, int x, int y) {
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

    private static final class OutputSlot extends PortSlot {
        private OutputSlot(IItemHandler handler, int index, int x, int y) {
            super(handler, index, x, y);
        }

        @Override
        public boolean mayPlace(ItemStack stack) {
            return false;
        }
    }
}
