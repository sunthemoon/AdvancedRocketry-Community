package io.github.sunthemoon.advancedrocketrycommunity.satellite.terminal;

import io.github.sunthemoon.advancedrocketrycommunity.registry.ModBlocks;
import io.github.sunthemoon.advancedrocketrycommunity.registry.ModItems;
import io.github.sunthemoon.advancedrocketrycommunity.registry.ModMenuTypes;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.mission.MissionStatus;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.mission.SatelliteOperationCode;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.model.SatelliteLimits;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
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
import net.minecraftforge.items.SlotItemHandler;

public final class SatelliteTerminalMenu extends AbstractContainerMenu {
    public static final int BUTTON_PREVIOUS = 0;
    public static final int BUTTON_NEXT = 1;
    public static final int BUTTON_ASSEMBLE = 2;
    public static final int BUTTON_LAUNCH = 3;
    public static final int BUTTON_CLAIM = 4;
    public static final int BUTTON_CANCEL = 5;

    private static final int PLAYER_SLOT_START = SatelliteTerminalBlockEntity.SLOT_COUNT;
    private static final int PLAYER_SLOT_END = PLAYER_SLOT_START + 27;
    private static final int HOTBAR_SLOT_END = PLAYER_SLOT_END + 9;

    private final ContainerLevelAccess access;
    private final ContainerData data;
    private final List<ResourceLocation> targets;
    @Nullable
    private final SatelliteTerminalBlockEntity terminal;

    public SatelliteTerminalMenu(int id, Inventory playerInventory, FriendlyByteBuf buffer) {
        this(
                id,
                playerInventory,
                requireTerminal(playerInventory, buffer.readBlockPos()),
                new SimpleContainerData(SatelliteTerminalBlockEntity.MENU_DATA_COUNT),
                readTargets(buffer)
        );
    }

    public SatelliteTerminalMenu(
            int id,
            Inventory playerInventory,
            SatelliteTerminalBlockEntity terminal,
            ContainerData data,
            List<ResourceLocation> targets
    ) {
        this(
                id,
                playerInventory,
                terminal.menuInventory(),
                data,
                ContainerLevelAccess.create(terminal.getLevel(), terminal.getBlockPos()),
                terminal,
                targets
        );
    }

    private SatelliteTerminalMenu(
            int id,
            Inventory playerInventory,
            IItemHandler machineInventory,
            ContainerData data,
            ContainerLevelAccess access,
            @Nullable SatelliteTerminalBlockEntity terminal,
            List<ResourceLocation> targets
    ) {
        super(ModMenuTypes.SATELLITE_TERMINAL.get(), id);
        checkContainerDataCount(data, SatelliteTerminalBlockEntity.MENU_DATA_COUNT);
        this.data = data;
        this.access = access;
        this.terminal = terminal;
        this.targets = List.copyOf(targets);

        addSlot(new SlotItemHandler(machineInventory, SatelliteTerminalBlockEntity.SLOT_CHASSIS, 18, 54));
        addSlot(new SlotItemHandler(machineInventory, SatelliteTerminalBlockEntity.SLOT_SOLAR_MODULE, 44, 54));
        addSlot(new SlotItemHandler(machineInventory, SatelliteTerminalBlockEntity.SLOT_DATA_STORAGE, 70, 54));
        addSlot(new SlotItemHandler(machineInventory, SatelliteTerminalBlockEntity.SLOT_CONTROL_CHIP, 108, 54));
        addSlot(new SlotItemHandler(machineInventory, SatelliteTerminalBlockEntity.SLOT_PACKAGE, 134, 54));
        addSlot(new SlotItemHandler(machineInventory, SatelliteTerminalBlockEntity.SLOT_CHARGE, 188, 54));
        addPlayerInventory(playerInventory);
        addDataSlots(data);
    }

    private static SatelliteTerminalBlockEntity requireTerminal(Inventory inventory, BlockPos position) {
        BlockEntity blockEntity = inventory.player.level().getBlockEntity(position);
        if (blockEntity instanceof SatelliteTerminalBlockEntity terminal) {
            return terminal;
        }
        throw new IllegalStateException("Satellite Terminal menu opened without its block entity at " + position);
    }

    private static List<ResourceLocation> readTargets(FriendlyByteBuf buffer) {
        int count = buffer.readVarInt();
        if (count < 0 || count > SatelliteLimits.MAX_TARGETS_PER_DEFINITION) {
            throw new IllegalArgumentException("Satellite menu target count exceeds its fixed bound");
        }
        List<ResourceLocation> targets = new ArrayList<>(count);
        for (int index = 0; index < count; index++) {
            targets.add(buffer.readResourceLocation());
        }
        return List.copyOf(targets);
    }

    private void addPlayerInventory(Inventory inventory) {
        for (int row = 0; row < 3; row++) {
            for (int column = 0; column < 9; column++) {
                addSlot(new net.minecraft.world.inventory.Slot(
                        inventory,
                        column + row * 9 + 9,
                        31 + column * 18,
                        132 + row * 18
                ));
            }
        }
        for (int column = 0; column < 9; column++) {
            addSlot(new net.minecraft.world.inventory.Slot(inventory, column, 31 + column * 18, 190));
        }
    }

    @Override
    public boolean clickMenuButton(Player player, int buttonId) {
        return player instanceof ServerPlayer serverPlayer
                && terminal != null
                && terminal.handleButton(serverPlayer, buttonId);
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        if (index < 0 || index >= slots.size() || !slots.get(index).hasItem()) {
            return ItemStack.EMPTY;
        }
        var sourceSlot = slots.get(index);
        ItemStack source = sourceSlot.getItem();
        ItemStack original = source.copy();
        boolean moved;
        if (index < PLAYER_SLOT_START) {
            moved = moveItemStackTo(source, PLAYER_SLOT_START, HOTBAR_SLOT_END, true);
        } else {
            int targetSlot = targetMachineSlot(source);
            moved = targetSlot >= 0
                    && moveItemStackTo(source, targetSlot, targetSlot + 1, false);
            if (!moved) {
                moved = index < PLAYER_SLOT_END
                        ? moveItemStackTo(source, PLAYER_SLOT_END, HOTBAR_SLOT_END, false)
                        : moveItemStackTo(source, PLAYER_SLOT_START, PLAYER_SLOT_END, false);
            }
        }
        if (!moved) {
            return ItemStack.EMPTY;
        }
        if (source.isEmpty()) {
            sourceSlot.set(ItemStack.EMPTY);
        } else {
            sourceSlot.setChanged();
        }
        sourceSlot.onTake(player, source);
        return original;
    }

    private static int targetMachineSlot(ItemStack stack) {
        if (stack.is(ModItems.SATELLITE_CHASSIS.get())) {
            return SatelliteTerminalBlockEntity.SLOT_CHASSIS;
        }
        if (stack.is(ModItems.SATELLITE_SOLAR_MODULE.get())) {
            return SatelliteTerminalBlockEntity.SLOT_SOLAR_MODULE;
        }
        if (stack.is(ModItems.DATA_STORAGE_UNIT.get())) {
            return SatelliteTerminalBlockEntity.SLOT_DATA_STORAGE;
        }
        if (stack.is(ModItems.SATELLITE_CONTROL_CHIP.get())) {
            return SatelliteTerminalBlockEntity.SLOT_CONTROL_CHIP;
        }
        if (stack.is(ModItems.DATA_SATELLITE_PACKAGE.get())) {
            return SatelliteTerminalBlockEntity.SLOT_PACKAGE;
        }
        return stack.is(Items.REDSTONE) ? SatelliteTerminalBlockEntity.SLOT_CHARGE : -1;
    }

    @Override
    public boolean stillValid(Player player) {
        return (terminal == null || terminal.canAccess(player))
                && stillValid(access, player, ModBlocks.SATELLITE_TERMINAL.get());
    }

    public int energyStored() {
        return data.get(0);
    }

    public int energyCapacity() {
        return data.get(1);
    }

    public SatelliteOperationCode status() {
        int value = data.get(2);
        return value >= 0 && value < SatelliteOperationCode.values().length
                ? SatelliteOperationCode.values()[value]
                : SatelliteOperationCode.SERVER_ERROR;
    }

    public int selectedTargetIndex() {
        return data.get(3);
    }

    public List<ResourceLocation> targets() {
        return targets;
    }

    public Optional<ResourceLocation> selectedTarget() {
        int index = selectedTargetIndex();
        return index >= 0 && index < targets.size() ? Optional.of(targets.get(index)) : Optional.empty();
    }

    public int researchBalance() {
        return (data.get(5) & 0xFFFF) | ((data.get(10) & 0xFFFF) << 16);
    }

    public Optional<MissionStatus> missionStatus() {
        int value = data.get(6) - 1;
        return value >= 0 && value < MissionStatus.values().length
                ? Optional.of(MissionStatus.values()[value])
                : Optional.empty();
    }

    public int missionDurationSeconds() {
        return data.get(4) & 0xFFFF;
    }

    public int remainingSeconds() {
        return data.get(7);
    }

    public boolean targetDiscovered() {
        return data.get(8) != 0;
    }

    public boolean ownedByViewer() {
        return data.get(9) != 0;
    }
}
