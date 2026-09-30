package io.github.sunthemoon.advancedrocketrycommunity.satellite.receiver;

import io.github.sunthemoon.advancedrocketrycommunity.registry.ModBlocks;
import io.github.sunthemoon.advancedrocketrycommunity.registry.ModMenuTypes;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.service.SolarLinks;
import javax.annotation.Nullable;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.ItemStackHandler;
import net.minecraftforge.items.SlotItemHandler;

/**
 * Receiver menu (ADR-049 section 10): open data format 1 carries the position, the four link statuses and the
 * output; live values follow as read-only container data. It has no button.
 */
public final class MicrowaveReceiverMenu extends AbstractContainerMenu {
    public static final int FORMAT_MARKER = -1;
    public static final int FORMAT_VERSION = 1;

    private static final int PLAYER_SLOT_START = MicrowaveReceiverBlockEntity.SLOT_COUNT;
    private static final int HOTBAR_SLOT_END = PLAYER_SLOT_START + 36;

    private final ContainerLevelAccess access;
    private final ContainerData data;
    @Nullable
    private final MicrowaveReceiverBlockEntity receiver;

    public MicrowaveReceiverMenu(int id, Inventory playerInventory, FriendlyByteBuf buffer) {
        this(id, playerInventory, new ItemStackHandler(MicrowaveReceiverBlockEntity.SLOT_COUNT),
                readOpenData(buffer), ContainerLevelAccess.NULL, null);
    }

    public MicrowaveReceiverMenu(int id, Inventory playerInventory, MicrowaveReceiverBlockEntity receiver) {
        this(id, playerInventory, receiver.menuInventory(), new MenuData(receiver),
                ContainerLevelAccess.create(receiver.getLevel(), receiver.getBlockPos()), receiver);
    }

    private MicrowaveReceiverMenu(int id, Inventory playerInventory, IItemHandler machine, ContainerData data,
                                  ContainerLevelAccess access, @Nullable MicrowaveReceiverBlockEntity receiver) {
        super(ModMenuTypes.MICROWAVE_RECEIVER.get(), id);
        checkContainerDataCount(data, MicrowaveReceiverBlockEntity.MENU_DATA_COUNT);
        this.data = data;
        this.access = access;
        this.receiver = receiver;
        for (int slot = 0; slot < MicrowaveReceiverBlockEntity.SLOT_COUNT; slot++) {
            addSlot(new SlotItemHandler(machine, slot, 44 + slot * 24, 30));
        }
        for (int row = 0; row < 3; row++) {
            for (int column = 0; column < 9; column++) {
                addSlot(new Slot(playerInventory, column + row * 9 + 9, 8 + column * 18, 84 + row * 18));
            }
        }
        for (int column = 0; column < 9; column++) {
            addSlot(new Slot(playerInventory, column, 8 + column * 18, 142));
        }
        addDataSlots(data);
    }

    /** Open data format 1: marker, version, position (display only), output and the four link statuses. */
    public static void writeOpenData(FriendlyByteBuf buffer, MicrowaveReceiverBlockEntity receiver) {
        buffer.writeVarInt(FORMAT_MARKER);
        buffer.writeVarInt(FORMAT_VERSION);
        buffer.writeBlockPos(receiver.getBlockPos());
        buffer.writeVarInt(receiver.output());
        for (int slot = 0; slot < MicrowaveReceiverBlockEntity.SLOT_COUNT; slot++) {
            buffer.writeByte(receiver.status(slot).ordinal());
        }
    }

    private static ContainerData readOpenData(FriendlyByteBuf buffer) {
        if (buffer.readVarInt() != FORMAT_MARKER || buffer.readVarInt() != FORMAT_VERSION) {
            throw new IllegalArgumentException("Unsupported microwave receiver menu format; update both host and client");
        }
        buffer.readBlockPos();
        SimpleContainerData data = new SimpleContainerData(MicrowaveReceiverBlockEntity.MENU_DATA_COUNT);
        int output = buffer.readVarInt();
        if (output < 0 || output > SolarLinks.MAX_OUTPUT_PER_TICK) {
            throw new IllegalArgumentException("Microwave receiver output is outside its bound");
        }
        data.set(2, output);
        for (int slot = 0; slot < MicrowaveReceiverBlockEntity.SLOT_COUNT; slot++) {
            int status = buffer.readUnsignedByte();
            if (status >= SolarLinks.LinkStatus.values().length) {
                throw new IllegalArgumentException("Unknown microwave receiver link status");
            }
            data.set(3 + slot, status);
        }
        return data;
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        if (!stillValid(player) || index < 0 || index >= slots.size() || !slots.get(index).hasItem()) {
            return ItemStack.EMPTY;
        }
        Slot source = slots.get(index);
        ItemStack stack = source.getItem();
        ItemStack original = stack.copy();
        boolean moved = index < PLAYER_SLOT_START
                ? moveItemStackTo(stack, PLAYER_SLOT_START, HOTBAR_SLOT_END, true)
                : moveItemStackTo(stack, 0, PLAYER_SLOT_START, false);
        if (!moved) {
            return ItemStack.EMPTY;
        }
        if (stack.isEmpty()) {
            source.set(ItemStack.EMPTY);
        } else {
            source.setChanged();
        }
        source.onTake(player, stack);
        return original;
    }

    @Override
    public boolean stillValid(Player player) {
        return (receiver == null || !receiver.isRemoved() && receiver.getLevel() != null
                && receiver.getLevel().hasChunkAt(receiver.getBlockPos())
                && receiver.getLevel().getBlockEntity(receiver.getBlockPos()) == receiver)
                && stillValid(access, player, ModBlocks.MICROWAVE_RECEIVER.get());
    }

    public int energyStored() {
        return (data.get(0) & 0xFFFF) | ((data.get(1) & 0xFFFF) << 16);
    }

    public int output() {
        return data.get(2);
    }

    public SolarLinks.LinkStatus status(int slot) {
        int value = data.get(3 + slot);
        return value >= 0 && value < SolarLinks.LinkStatus.values().length
                ? SolarLinks.LinkStatus.values()[value] : SolarLinks.LinkStatus.UNAVAILABLE;
    }

    /** Read-only, server-authoritative values; the energy is split into two 16-bit halves. */
    private static final class MenuData implements ContainerData {
        private final MicrowaveReceiverBlockEntity receiver;

        private MenuData(MicrowaveReceiverBlockEntity receiver) {
            this.receiver = receiver;
        }

        @Override
        public int get(int index) {
            return switch (index) {
                case 0 -> receiver.energyStored() & 0xFFFF;
                case 1 -> receiver.energyStored() >>> 16;
                case 2 -> receiver.output();
                default -> index < MicrowaveReceiverBlockEntity.MENU_DATA_COUNT ? receiver.status(index - 3).ordinal() : 0;
            };
        }

        @Override
        public void set(int index, int value) {
            // Read-only.
        }

        @Override
        public int getCount() {
            return MicrowaveReceiverBlockEntity.MENU_DATA_COUNT;
        }
    }
}
