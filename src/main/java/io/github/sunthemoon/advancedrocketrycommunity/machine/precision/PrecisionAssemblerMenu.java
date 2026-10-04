package io.github.sunthemoon.advancedrocketrycommunity.machine.precision;

import io.github.sunthemoon.advancedrocketrycommunity.machine.menu.MachineMenuOpening;
import io.github.sunthemoon.advancedrocketrycommunity.machine.menu.RecipeMenuReason;
import io.github.sunthemoon.advancedrocketrycommunity.machine.multiblock.lifecycle.MultiblockFormationState;
import io.github.sunthemoon.advancedrocketrycommunity.machine.multiblock.pattern.PatternDiagnosticReason;
import io.github.sunthemoon.advancedrocketrycommunity.machine.multiblock.pattern.PatternPosition;
import io.github.sunthemoon.advancedrocketrycommunity.machine.multiblock.pattern.PatternValidationStatus;
import io.github.sunthemoon.advancedrocketrycommunity.machine.process.ProcessFailureCode;
import io.github.sunthemoon.advancedrocketrycommunity.machine.process.ProcessMachineState;
import io.github.sunthemoon.advancedrocketrycommunity.registry.ModBlocks;
import io.github.sunthemoon.advancedrocketrycommunity.registry.ModMenuTypes;
import java.util.Optional;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.IItemHandlerModifiable;
import net.minecraftforge.items.ItemHandlerHelper;
import net.minecraftforge.items.ItemStackHandler;
import net.minecraftforge.items.SlotItemHandler;

/** Seven Item channels, one Energy gauge and server-owned process diagnostics. */
public final class PrecisionAssemblerMenu extends AbstractContainerMenu {
    public static final int SLOT_INPUT_FIRST = 0;
    public static final int SLOT_OUTPUT_FIRST = PrecisionAssemblerChannels.INPUT_COUNT;
    public static final int MACHINE_SLOT_COUNT = PrecisionAssemblerChannels.INPUT_COUNT
            + PrecisionAssemblerChannels.OUTPUT_COUNT;

    private static final int PLAYER_SLOT_START = MACHINE_SLOT_COUNT;
    private static final int PLAYER_SLOT_END = PLAYER_SLOT_START + 27;
    private static final int HOTBAR_SLOT_END = PLAYER_SLOT_END + 9;

    private final BlockPos controllerPosition;
    private final ContainerData data;
    private final IItemHandler machineItems;
    @Nullable
    private final PrecisionAssemblerBlockEntity openedController;
    @Nullable
    private final UUID openedInstanceId;
    private final long openedGeneration;

    public PrecisionAssemblerMenu(int containerId, Inventory playerInventory, FriendlyByteBuf buffer) {
        this(containerId, playerInventory, MachineMenuOpening.read(buffer, PrecisionAssemblerMenuData.COUNT),
                new ItemStackHandler(MACHINE_SLOT_COUNT),
                new SimpleContainerData(PrecisionAssemblerMenuData.COUNT), null);
    }

    PrecisionAssemblerMenu(int containerId, Inventory playerInventory, PrecisionAssemblerBlockEntity controller) {
        this(containerId, playerInventory, controller.getBlockPos(),
                new PrecisionAssemblerMenuItemHandler(controller),
                new PrecisionAssemblerMenuData(controller), controller);
    }

    private PrecisionAssemblerMenu(
            int containerId,
            Inventory playerInventory,
            BlockPos controllerPosition,
            IItemHandler machineItems,
            ContainerData data,
            @Nullable PrecisionAssemblerBlockEntity openedController
    ) {
        super(ModMenuTypes.PRECISION_ASSEMBLER.get(), containerId);
        checkContainerDataCount(data, PrecisionAssemblerMenuData.COUNT);
        MachineMenuOpening.requireExactDataCount(data, PrecisionAssemblerMenuData.COUNT);
        this.controllerPosition = controllerPosition.immutable();
        this.machineItems = machineItems;
        this.data = data;
        this.openedController = openedController;
        this.openedInstanceId = openedController == null
                ? null : openedController.controllerState().machineInstanceId();
        this.openedGeneration = openedController == null ? -1L : openedController.generation();

        for (int slot = 0; slot < PrecisionAssemblerChannels.INPUT_COUNT; slot++) {
            addSlot(new PortSlot(machineItems, slot, 18 + slot * 22, 36));
        }
        for (int slot = 0; slot < PrecisionAssemblerChannels.OUTPUT_COUNT; slot++) {
            addSlot(new OutputSlot(machineItems, SLOT_OUTPUT_FIRST + slot, 187 + slot * 22, 36));
        }
        addPlayerInventory(playerInventory);
        addDataSlots(data);
    }

    static boolean canOpen(PrecisionAssemblerBlockEntity controller, Player player) {
        if (!(controller.getLevel() instanceof ServerLevel level)
                || player.level() != level || player.isSpectator() || controller.isRemoved()) {
            return false;
        }
        BlockPos position = controller.getBlockPos();
        return level.hasChunkAt(position)
                && level.getBlockEntity(position) == controller
                && level.getBlockState(position).is(ModBlocks.PRECISION_ASSEMBLER.get())
                && player.distanceToSqr(
                        position.getX() + 0.5D,
                        position.getY() + 0.5D,
                        position.getZ() + 0.5D
                ) <= 64.0D;
    }

    @Override
    public boolean stillValid(Player player) {
        if (openedController != null) {
            return canOpen(openedController, player)
                    && openedController.generation() == openedGeneration
                    && openedController.controllerState().machineInstanceId().equals(openedInstanceId);
        }
        Level level = player.level();
        return !player.isSpectator() && level.hasChunkAt(controllerPosition)
                && stillValid(ContainerLevelAccess.create(level, controllerPosition),
                        player, ModBlocks.PRECISION_ASSEMBLER.get());
    }

    @Override
    public void clicked(int slotId, int button, ClickType clickType, Player player) {
        if (stillValid(player)) {
            super.clicked(slotId, button, clickType, player);
        }
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        if (!stillValid(player) || index < 0 || index >= slots.size()) {
            return ItemStack.EMPTY;
        }
        Slot sourceSlot = slots.get(index);
        if (!sourceSlot.hasItem() || !sourceSlot.mayPickup(player)) {
            return ItemStack.EMPTY;
        }
        ItemStack source = sourceSlot.getItem();
        ItemStack original = source.copy();
        boolean moved;
        if (index < MACHINE_SLOT_COUNT) {
            moved = moveItemStackTo(source, PLAYER_SLOT_START, HOTBAR_SLOT_END, true);
        } else {
            ItemStack remainder = source.copy();
            for (int slot = SLOT_INPUT_FIRST; slot < SLOT_OUTPUT_FIRST && !remainder.isEmpty(); slot++) {
                remainder = machineItems.insertItem(slot, remainder, false);
            }
            int inserted = source.getCount() - remainder.getCount();
            moved = inserted > 0;
            if (moved) {
                source.shrink(inserted);
            } else if (index < PLAYER_SLOT_END) {
                moved = moveItemStackTo(source, PLAYER_SLOT_END, HOTBAR_SLOT_END, false);
            } else {
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

    public MultiblockFormationState formationState() {
        return PrecisionAssemblerMenuWire.formation(data.get(PrecisionAssemblerMenuData.FORMATION));
    }

    public Optional<PatternValidationStatus> validationStatus() {
        return PrecisionAssemblerMenuWire.validation(data.get(PrecisionAssemblerMenuData.VALIDATION));
    }

    public Optional<PatternDiagnosticReason> diagnosticReason() {
        return hasDiagnostic()
                ? PrecisionAssemblerMenuWire.diagnostic(data.get(PrecisionAssemblerMenuData.DIAGNOSTIC_REASON))
                : Optional.empty();
    }

    public Optional<PatternPosition> diagnosticLocalPosition() {
        return hasDiagnostic() ? Optional.of(new PatternPosition(
                data.get(PrecisionAssemblerMenuData.DIAGNOSTIC_LOCAL_X),
                data.get(PrecisionAssemblerMenuData.DIAGNOSTIC_LOCAL_Y),
                data.get(PrecisionAssemblerMenuData.DIAGNOSTIC_LOCAL_Z)
        )) : Optional.empty();
    }

    public Optional<BlockPos> diagnosticWorldPosition() {
        return hasDiagnostic() ? Optional.of(new BlockPos(
                joined(PrecisionAssemblerMenuData.DIAGNOSTIC_WORLD_X_LOW,
                        PrecisionAssemblerMenuData.DIAGNOSTIC_WORLD_X_HIGH),
                joined(PrecisionAssemblerMenuData.DIAGNOSTIC_WORLD_Y_LOW,
                        PrecisionAssemblerMenuData.DIAGNOSTIC_WORLD_Y_HIGH),
                joined(PrecisionAssemblerMenuData.DIAGNOSTIC_WORLD_Z_LOW,
                        PrecisionAssemblerMenuData.DIAGNOSTIC_WORLD_Z_HIGH)
        )) : Optional.empty();
    }

    public ProcessMachineState processState() {
        return PrecisionAssemblerMenuWire.processState(data.get(PrecisionAssemblerMenuData.PROCESS_STATE));
    }

    public ProcessFailureCode processFailure() {
        return PrecisionAssemblerMenuWire.failure(data.get(PrecisionAssemblerMenuData.PROCESS_FAILURE));
    }

    public RecipeMenuReason recipeReason() {
        if (formationState() == MultiblockFormationState.UNSUPPORTED_DATA
                || processState() == ProcessMachineState.UNSUPPORTED_DATA
                || processFailure() == ProcessFailureCode.NONE) {
            return RecipeMenuReason.NONE;
        }
        return RecipeMenuReason.fromNetworkId(data.get(PrecisionAssemblerMenuData.RECIPE_REASON));
    }

    public int progress() {
        return joined(PrecisionAssemblerMenuData.PROGRESS_LOW, PrecisionAssemblerMenuData.PROGRESS_HIGH);
    }

    public int totalProcessingTicks() {
        return joined(PrecisionAssemblerMenuData.TOTAL_LOW, PrecisionAssemblerMenuData.TOTAL_HIGH);
    }

    public int energyStored() {
        return data.get(PrecisionAssemblerMenuData.ENERGY) & 0xFFFF;
    }

    public int energyCapacity() {
        return data.get(PrecisionAssemblerMenuData.ENERGY_CAPACITY) & 0xFFFF;
    }

    public int inspectedCells() {
        return data.get(PrecisionAssemblerMenuData.INSPECTED_CELLS) & 0xFFFF;
    }

    private boolean hasDiagnostic() {
        return data.get(PrecisionAssemblerMenuData.DIAGNOSTIC_PRESENT) != 0;
    }

    private int joined(int lowIndex, int highIndex) {
        return PrecisionAssemblerMenuWire.joinInt(data.get(lowIndex), data.get(highIndex));
    }

    private void addPlayerInventory(Inventory inventory) {
        for (int row = 0; row < 3; row++) {
            for (int column = 0; column < 9; column++) {
                addSlot(new Slot(inventory, column + row * 9 + 9,
                        18 + column * 18, 145 + row * 18));
            }
        }
        for (int column = 0; column < 9; column++) {
            addSlot(new Slot(inventory, column, 18 + column * 18, 203));
        }
    }

    private static class PortSlot extends SlotItemHandler {
        private PortSlot(IItemHandler handler, int slot, int x, int y) {
            super(handler, slot, x, y);
        }

        @Override
        public boolean mayPickup(Player player) {
            return !getItemHandler().extractItem(getSlotIndex(), 1, true).isEmpty();
        }

        @Override
        public boolean mayPlace(ItemStack stack) {
            IItemHandler handler = getItemHandler();
            return handler.isItemValid(getSlotIndex(), stack)
                    && handler.insertItem(getSlotIndex(), stack, true).getCount() < stack.getCount();
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
        private OutputSlot(IItemHandler handler, int slot, int x, int y) {
            super(handler, slot, x, y);
        }

        @Override
        public boolean mayPlace(ItemStack stack) {
            return false;
        }
    }
}
