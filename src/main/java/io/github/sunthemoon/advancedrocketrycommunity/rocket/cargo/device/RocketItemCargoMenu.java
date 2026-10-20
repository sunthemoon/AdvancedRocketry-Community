package io.github.sunthemoon.advancedrocketrycommunity.rocket.cargo.device;

import io.github.sunthemoon.advancedrocketrycommunity.registry.ModMenuTypes;
import io.github.sunthemoon.advancedrocketrycommunity.resourceescrow.EscrowItemBuffer;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/**
 * W11CD section 5.1's item menu: the device's four slots and the player inventory, usable both ways by the owner
 * within 8 blocks, whatever the device's role. On the server every slot change goes through the device's lock and
 * section 5.3's bounds; a refused stack stays where it is. Eight scalar values (status, binding, role and the cursor's
 * x, y, z and slot) synchronize as 16-bit shorts. There are no buttons and the open data is a format number only.
 */
public final class RocketItemCargoMenu extends AbstractContainerMenu {
    public static final int DATA_COUNT = 8;
    public static final int DEVICE_SLOTS = EscrowItemBuffer.SLOTS;
    public static final int SLOT_X = 53;
    public static final int SLOT_Y = 20;
    public static final int INVENTORY_Y = 94;
    private static final int FORMAT = 1;
    @Nullable private final RocketItemCargoBlockEntity device;
    /** The device identity the menu opened on; a changed root closes it. */
    @Nullable private final UUID identity;
    private final Player viewer;
    private final ContainerData data;
    private final Container buffer;

    public RocketItemCargoMenu(int id, Inventory inventory, FriendlyByteBuf open) {
        this(id, inventory, null, new SimpleContainer(DEVICE_SLOTS), readOpenData(open));
    }

    public RocketItemCargoMenu(int id, Inventory inventory, RocketItemCargoBlockEntity device) {
        this(id, inventory, device, new Storage(device, inventory.player), liveData(device));
    }

    private RocketItemCargoMenu(int id, Inventory inventory, @Nullable RocketItemCargoBlockEntity device, Container buffer,
                                ContainerData data) {
        super(ModMenuTypes.ROCKET_ITEM_CARGO.get(), id);
        this.device = device;
        this.identity = device == null ? null : device.root().map(RocketItemCargoRoot::device).orElse(null);
        this.viewer = inventory.player;
        this.data = data;
        this.buffer = buffer;
        checkContainerSize(buffer, DEVICE_SLOTS);
        checkContainerDataCount(data, DATA_COUNT);
        for (int slot = 0; slot < DEVICE_SLOTS; slot++) {
            addSlot(new DeviceSlot(buffer, slot, SLOT_X + slot * 18, SLOT_Y));
        }
        for (int row = 0; row < 3; row++) {
            for (int column = 0; column < 9; column++) {
                addSlot(new Slot(inventory, 9 + row * 9 + column, 8 + column * 18, INVENTORY_Y + row * 18));
            }
        }
        for (int column = 0; column < 9; column++) {
            addSlot(new Slot(inventory, column, 8 + column * 18, INVENTORY_Y + 58));
        }
        addDataSlots(data);
    }

    public static void writeOpenData(FriendlyByteBuf buffer) {
        buffer.writeVarInt(FORMAT);
    }

    public static ContainerData readOpenData(FriendlyByteBuf buffer) {
        if (buffer.readVarInt() != FORMAT || buffer.isReadable()) {
            throw new IllegalArgumentException("Unsupported rocket item cargo menu format");
        }
        return new SimpleContainerData(DATA_COUNT);
    }

    public static ContainerData liveData(RocketItemCargoBlockEntity device) {
        return new ContainerData() {
            @Override
            public int get(int index) {
                return switch (index) {
                    case 0 -> device.displayStatus().ordinal();
                    case 1 -> device.root().filter(root -> root.binding() != null).isPresent() ? 1 : 0;
                    case 2 -> device.role().ordinal();
                    case 3 -> device.cursor().isPresent() ? 1 : 0;
                    case 4 -> device.cursor().map(BlockPos::getX).orElse(0);
                    case 5 -> device.cursor().map(BlockPos::getY).orElse(0);
                    case 6 -> device.cursor().map(BlockPos::getZ).orElse(0);
                    case 7 -> device.cursorSlot();
                    default -> 0;
                };
            }

            @Override
            public void set(int index, int value) {
            }

            @Override
            public int getCount() {
                return DATA_COUNT;
            }
        };
    }

    /** The synchronized values; an unknown status ordinal is null and shown with a generic label. */
    public record View(@Nullable RocketCargoStatus status, boolean bound, @Nullable RocketCargoRole role,
                       @Nullable BlockPos cursor, int cursorSlot) {
    }

    /** Rebuilds the values from the sign-extended shorts that vanilla delivers. */
    public View view() {
        int ordinal = data.get(0);
        RocketCargoStatus[] statuses = RocketCargoStatus.values();
        RocketCargoRole[] roles = RocketCargoRole.values();
        boolean cursor = data.get(3) == 1;
        return new View(ordinal >= 0 && ordinal < statuses.length ? statuses[ordinal] : null, data.get(1) == 1,
                data.get(2) >= 0 && data.get(2) < roles.length ? roles[data.get(2)] : null,
                cursor ? new BlockPos((short) data.get(4), (short) data.get(5), (short) data.get(6)) : null,
                cursor ? (short) data.get(7) : -1);
    }

    @Nullable
    public RocketItemCargoBlockEntity device() {
        return device;
    }

    /** The exact block entity, still loaded, still the same device and still admitting this owner. */
    @Override
    public boolean stillValid(Player player) {
        return player == viewer && (device == null || identity != null && device.admitted(player)
                && device.root().map(RocketItemCargoRoot::device).filter(identity::equals).isPresent());
    }

    @Override
    public boolean clickMenuButton(Player player, int id) {
        return false;
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        if (index < 0 || index >= slots.size() || player != viewer) {
            return ItemStack.EMPTY;
        }
        if (!(buffer instanceof Storage storage)) {
            return predictedMove(index);
        }
        return index < DEVICE_SLOTS ? toPlayer(storage, index) : toDevice(storage, slots.get(index));
    }

    /** Takes only what the player inventory can hold, so nothing leaves the device without a place to go. */
    private ItemStack toPlayer(Storage storage, int index) {
        ItemStack shown = storage.getItem(index);
        int room = shown.isEmpty() ? 0 : playerRoom(shown);
        if (room <= 0) {
            return ItemStack.EMPTY;
        }
        ItemStack taken = storage.removeItem(index, room);
        if (taken.isEmpty()) {
            return ItemStack.EMPTY;
        }
        moveItemStackTo(taken, DEVICE_SLOTS, slots.size(), true);
        if (!taken.isEmpty()) {
            viewer.getInventory().placeItemBackInInventory(taken);
        }
        return shown;
    }

    private int playerRoom(ItemStack stack) {
        int merge = 0;
        boolean empty = false;
        for (int index = DEVICE_SLOTS; index < slots.size(); index++) {
            Slot slot = slots.get(index);
            ItemStack present = slot.getItem();
            if (present.isEmpty()) {
                empty |= slot.mayPlace(stack);
            } else if (stack.isStackable() && ItemStack.isSameItemSameTags(stack, present)) {
                merge += Math.max(0, Math.min(slot.getMaxStackSize(), stack.getMaxStackSize()) - present.getCount());
            }
        }
        return empty ? stack.getCount() : Math.min(stack.getCount(), merge);
    }

    /** Offers the player's stack to matching slots, then empty ones; the remainder stays in the player's slot. */
    private ItemStack toDevice(Storage storage, Slot slot) {
        ItemStack source = slot.getItem();
        if (source.isEmpty()) {
            return ItemStack.EMPTY;
        }
        ItemStack before = source.copy();
        ItemStack rest = storage.insert(source);
        if (rest == source) {
            return ItemStack.EMPTY;
        }
        slot.setByPlayer(rest);
        slot.setChanged();
        return before;
    }

    /** Client-side prediction over the synchronized copy; the server's result replaces it. */
    private ItemStack predictedMove(int index) {
        Slot slot = slots.get(index);
        if (!slot.hasItem()) {
            return ItemStack.EMPTY;
        }
        ItemStack stack = slot.getItem();
        ItemStack before = stack.copy();
        boolean moved = index < DEVICE_SLOTS ? moveItemStackTo(stack, DEVICE_SLOTS, slots.size(), true)
                : moveItemStackTo(stack, 0, DEVICE_SLOTS, false);
        if (!moved) {
            return ItemStack.EMPTY;
        }
        if (stack.isEmpty()) {
            slot.setByPlayer(ItemStack.EMPTY);
        } else {
            slot.setChanged();
        }
        return before;
    }

    /** A device slot; on the server it asks the guarded storage before vanilla moves anything. */
    private static final class DeviceSlot extends Slot {
        DeviceSlot(Container container, int slot, int x, int y) {
            super(container, slot, x, y);
        }

        @Override
        public boolean mayPlace(ItemStack stack) {
            return !(container instanceof Storage storage) || storage.canPlace(getContainerSlot(), stack);
        }

        @Override
        public boolean mayPickup(Player player) {
            return !(container instanceof Storage storage) || storage.canTake(player);
        }
    }

    /**
     * The server's view of the device for one owner: copies out, and every change under the device's lock after the
     * owner's admission. It never implements {@code Clearable} and never hands out a live stack.
     */
    private static final class Storage implements Container {
        private final RocketItemCargoBlockEntity device;
        private final Player viewer;

        Storage(RocketItemCargoBlockEntity device, Player viewer) {
            this.device = device;
            this.viewer = viewer;
        }

        private boolean begin() {
            return device.admitted(viewer) && device.beginLocal();
        }

        boolean canPlace(int slot, ItemStack stack) {
            if (!begin()) { return false; }
            try { return RocketItemCargoLocal.placeable(device, slot, stack); }
            finally { device.endLocal(); }
        }

        boolean canTake(Player player) {
            if (player != viewer || !begin()) { return false; }
            device.endLocal();
            return true;
        }

        ItemStack insert(ItemStack offered) {
            if (!begin()) { return offered; }
            try {
                ItemStack rest = offered;
                // Matching slots first, then empty ones, as vanilla's shift-click does.
                for (int pass = 0; pass < 2; pass++) {
                    for (int slot = 0; slot < DEVICE_SLOTS && !rest.isEmpty(); slot++) {
                        if (device.buffer().slots().get(slot).isEmpty() == (pass == 1)) {
                            rest = RocketItemCargoLocal.insert(device, slot, rest, false);
                        }
                    }
                }
                return rest;
            } finally { device.endLocal(); }
        }

        @Override public int getContainerSize() { return DEVICE_SLOTS; }
        @Override public boolean isEmpty() { return device.buffer().slots().stream().allMatch(net.minecraft.nbt.CompoundTag::isEmpty); }
        @Override public ItemStack getItem(int slot) { return device.stack(slot); }

        @Override
        public ItemStack removeItem(int slot, int amount) {
            if (!begin()) { return ItemStack.EMPTY; }
            try { return RocketItemCargoLocal.extract(device, slot, amount, false); }
            finally { device.endLocal(); }
        }

        @Override public ItemStack removeItemNoUpdate(int slot) { return removeItem(slot, Integer.MAX_VALUE); }

        @Override
        public void setItem(int slot, ItemStack stack) {
            // Vanilla calls this only after mayPlace or mayPickup accepted, under the same state on the same tick.
            if (!begin()) { return; }
            try { RocketItemCargoLocal.replace(device, slot, stack); }
            finally { device.endLocal(); }
        }

        @Override public boolean canPlaceItem(int slot, ItemStack stack) { return canPlace(slot, stack); }
        @Override public void setChanged() { }
        @Override public boolean stillValid(Player player) { return player == viewer && device.admitted(player); }
        @Override public void clearContent() { }
    }
}
