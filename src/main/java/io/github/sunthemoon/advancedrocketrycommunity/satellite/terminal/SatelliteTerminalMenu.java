package io.github.sunthemoon.advancedrocketrycommunity.satellite.terminal;

import io.github.sunthemoon.advancedrocketrycommunity.registry.ModBlocks;
import io.github.sunthemoon.advancedrocketrycommunity.registry.ModItems;
import io.github.sunthemoon.advancedrocketrycommunity.registry.ModMenuTypes;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.mission.MissionStatus;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.mission.SatelliteOperationCode;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.service.SatelliteRuntime;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.service.SatellitePayloadRuntime;
import java.util.List;
import java.util.Optional;
import javax.annotation.Nullable;
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
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.SlotItemHandler;
import net.minecraftforge.items.ItemStackHandler;

public final class SatelliteTerminalMenu extends AbstractContainerMenu {
    public static final int BUTTON_PREVIOUS = 0;
    public static final int BUTTON_NEXT = 1;
    public static final int BUTTON_ASSEMBLE = 2;
    public static final int BUTTON_LAUNCH = 3;
    public static final int BUTTON_CLAIM = 4;
    public static final int BUTTON_CANCEL = 5;
    /** ADR-049 section 7: remove an idle satellite and blank its chip. */
    public static final int BUTTON_DECOMMISSION = 6;
    /** ADR-049 section 9: clear the link of a solar satellite whose receiver is confirmed missing. */
    public static final int BUTTON_UNLINK = 7;

    private static final int PLAYER_SLOT_START = SatelliteTerminalBlockEntity.SLOT_COUNT;
    private static final int PLAYER_SLOT_END = PLAYER_SLOT_START + 27;
    private static final int HOTBAR_SLOT_END = PLAYER_SLOT_END + 9;

    private final ContainerLevelAccess access;
    private final ContainerData data;
    private final SatelliteTerminalTargets catalog;
    private final boolean viewFollows;
    @Nullable
    private final SatelliteTerminalBlockEntity terminal;
    @Nullable
    private final SatelliteTerminalViews views;

    public SatelliteTerminalMenu(int id, Inventory playerInventory, FriendlyByteBuf buffer) {
        this(id, playerInventory, new ItemStackHandler(SatelliteTerminalBlockEntity.SLOT_COUNT),
                new SimpleContainerData(SatelliteTerminalBlockEntity.MENU_DATA_COUNT),
                ContainerLevelAccess.NULL, null, readOpenData(buffer));
    }

    public SatelliteTerminalMenu(
            int id,
            Inventory playerInventory,
            SatelliteTerminalBlockEntity terminal,
            SatelliteTerminalTargets catalog
    ) {
        this(
                id,
                playerInventory,
                terminal.menuInventory(),
                new SatelliteTerminalMenuData(terminal, playerInventory.player.getUUID(), catalog),
                ContainerLevelAccess.create(terminal.getLevel(), terminal.getBlockPos()),
                terminal,
                new OpenData(catalog, true)
        );
    }

    private SatelliteTerminalMenu(
            int id,
            Inventory playerInventory,
            IItemHandler machineInventory,
            ContainerData data,
            ContainerLevelAccess access,
            @Nullable SatelliteTerminalBlockEntity terminal,
            OpenData openData
    ) {
        super(ModMenuTypes.SATELLITE_TERMINAL.get(), id);
        checkContainerDataCount(data, SatelliteTerminalBlockEntity.MENU_DATA_COUNT);
        this.data = data;
        this.access = access;
        this.terminal = terminal;
        this.catalog = openData.catalog();
        this.viewFollows = openData.viewFollows();
        this.views = terminal != null && playerInventory.player instanceof ServerPlayer serverPlayer
                ? new SatelliteTerminalViews(serverPlayer, id) : null;

        addSlot(new SlotItemHandler(machineInventory, SatelliteTerminalBlockEntity.SLOT_CHASSIS, 18, 54));
        addSlot(new SlotItemHandler(machineInventory, SatelliteTerminalBlockEntity.SLOT_SOLAR_MODULE, 44, 54));
        addSlot(new SlotItemHandler(machineInventory, SatelliteTerminalBlockEntity.SLOT_DATA_STORAGE, 70, 54));
        addSlot(new SlotItemHandler(machineInventory, SatelliteTerminalBlockEntity.SLOT_CONTROL_CHIP, 108, 54));
        addSlot(new SlotItemHandler(machineInventory, SatelliteTerminalBlockEntity.SLOT_PACKAGE, 134, 54));
        addSlot(new SlotItemHandler(machineInventory, SatelliteTerminalBlockEntity.SLOT_CHARGE, 188, 54));
        addPlayerInventory(playerInventory);
        addDataSlots(data);
    }

    private static OpenData readOpenData(FriendlyByteBuf buffer) {
        buffer.readBlockPos(); // Position is display metadata only; no client chunk/BE lookup.
        SatelliteTerminalTargets catalog = SatelliteTerminalTargets.read(buffer);
        byte flag = buffer.readByte();
        if (flag != 0 && flag != 1) {
            throw new IllegalArgumentException("Invalid satellite terminal view flag");
        }
        return new OpenData(catalog, flag == 1);
    }

    @Override
    public void broadcastChanges() {
        super.broadcastChanges();
        if (views != null && terminal != null && !terminal.isRemoved()) {
            views.tick(terminal);
        }
    }

    /** Whether the host announced a terminal view for this menu (open-data format 2). */
    public boolean viewFollows() {
        return viewFollows;
    }

    private void addPlayerInventory(Inventory inventory) {
        for (int row = 0; row < 3; row++) {
            for (int column = 0; column < 9; column++) {
                addSlot(new net.minecraft.world.inventory.Slot(
                        inventory,
                        column + row * 9 + 9,
                        31 + column * 18,
                        150 + row * 18
                ));
            }
        }
        for (int column = 0; column < 9; column++) {
            addSlot(new net.minecraft.world.inventory.Slot(inventory, column, 31 + column * 18, 208));
        }
    }

    @Override
    public boolean clickMenuButton(Player player, int buttonId) {
        if (!(player instanceof ServerPlayer serverPlayer) || terminal == null || !stillValid(player)) {
            return false;
        }
        boolean selection = buttonId == BUTTON_PREVIOUS || buttonId == BUTTON_NEXT;
        if (!SatelliteRuntime.allowIntent(serverPlayer, selection)) {
            terminal.reportRateLimited(serverPlayer);
            return false;
        }
        return terminal.handleButton(serverPlayer, buttonId);
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        if (!stillValid(player) || index < 0 || index >= slots.size() || !slots.get(index).hasItem()) {
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
        if (SatellitePayloadRuntime.definitionFor(stack) != null) {
            return SatelliteTerminalBlockEntity.SLOT_DATA_STORAGE;
        }
        if (stack.is(ModItems.SATELLITE_CONTROL_CHIP.get())) {
            return SatelliteTerminalBlockEntity.SLOT_CONTROL_CHIP;
        }
        if (stack.is(ModItems.DATA_SATELLITE_PACKAGE.get()) || stack.is(ModItems.SATELLITE_PACKAGE.get())) {
            return SatelliteTerminalBlockEntity.SLOT_PACKAGE;
        }
        return stack.is(Items.REDSTONE) ? SatelliteTerminalBlockEntity.SLOT_CHARGE : -1;
    }

    @Override
    public boolean stillValid(Player player) {
        return (terminal == null || !terminal.isRemoved() && terminal.getLevel() != null
                && terminal.getLevel().hasChunkAt(terminal.getBlockPos())
                && terminal.getLevel().getBlockEntity(terminal.getBlockPos()) == terminal
                && terminal.canAccess(player) && catalog.generation() == SatelliteRuntime.catalogGeneration())
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
        return catalog.targets(data.get(11));
    }

    public Optional<ResourceLocation> selectedTarget() {
        int index = selectedTargetIndex();
        List<ResourceLocation> targets = targets();
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

    private record OpenData(SatelliteTerminalTargets catalog, boolean viewFollows) {
    }
}
