package io.github.sunthemoon.advancedrocketrycommunity.rocket.bay;

import io.github.sunthemoon.advancedrocketrycommunity.registry.ModItems;
import io.github.sunthemoon.advancedrocketrycommunity.registry.ModMenuTypes;
import javax.annotation.Nullable;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.items.IItemHandler;

/**
 * One bay slot over the block entity's own item handler, so the handler's locks are the slot's locks: placing is
 * {@code isItemValid}, taking is a simulated {@code extractItem}. Two status slots carry the phase and reason codes.
 */
public final class SatelliteBayMenu extends AbstractContainerMenu {
    public static final int DATA_COUNT = 2;
    private static final int FORMAT = 1;
    @Nullable private final SatelliteBayBlockEntity bay;
    private final Player viewer;
    private final ContainerData data;

    public SatelliteBayMenu(int id, Inventory inventory, FriendlyByteBuf buffer) {
        this(id, inventory, null, readOpenData(buffer));
    }

    public SatelliteBayMenu(int id, Inventory inventory, SatelliteBayBlockEntity bay) {
        this(id, inventory, bay, liveData(bay));
    }

    private SatelliteBayMenu(int id, Inventory inventory, @Nullable SatelliteBayBlockEntity bay, ContainerData data) {
        super(ModMenuTypes.ROCKET_SATELLITE_BAY.get(), id);
        this.bay = bay;
        this.viewer = inventory.player;
        this.data = data;
        checkContainerDataCount(data, DATA_COUNT);
        addSlot(new BaySlot(80, 35));
        for (int row = 0; row < 3; row++) {
            for (int column = 0; column < 9; column++) {
                addSlot(new Slot(inventory, 9 + row * 9 + column, 8 + column * 18, 84 + row * 18));
            }
        }
        for (int column = 0; column < 9; column++) {
            addSlot(new Slot(inventory, column, 8 + column * 18, 142));
        }
        addDataSlots(data);
    }

    public static void writeOpenData(FriendlyByteBuf buffer) {
        buffer.writeVarInt(FORMAT);
    }

    public static ContainerData readOpenData(FriendlyByteBuf buffer) {
        if (buffer.readVarInt() != FORMAT || buffer.isReadable()) {
            throw new IllegalArgumentException("Unsupported satellite bay menu format");
        }
        return new SimpleContainerData(DATA_COUNT);
    }

    public static ContainerData liveData(SatelliteBayBlockEntity bay) {
        return new ContainerData() {
            @Override
            public int get(int index) {
                return switch (index) {
                    case 0 -> bay.phase().code();
                    case 1 -> bay.reason().code();
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

    /** The synchronized status; an unknown code shows the fixed reason rather than a client decision. */
    public record View(SatelliteBayStatus.Phase phase, SatelliteBayStatus.Reason reason) {
    }

    public View view() {
        return new View(SatelliteBayStatus.Phase.fromCode(data.get(0)).orElse(SatelliteBayStatus.Phase.NONE),
                SatelliteBayStatus.Reason.fromCode(data.get(1)).orElse(SatelliteBayStatus.Reason.DEPLOYMENT_UNAVAILABLE));
    }

    @Nullable
    public SatelliteBayBlockEntity bay() {
        return bay;
    }

    @Override
    public boolean stillValid(Player player) {
        return player == viewer && (bay == null || bay.admitted(player));
    }

    @Override
    public void clicked(int slot, int button, ClickType type, Player player) {
        if (stillValid(player)) {
            super.clicked(slot, button, type, player);
        }
    }

    @Override
    public boolean canDragTo(Slot slot) {
        return !(slot instanceof BaySlot);
    }

    /** Shift-click moves exactly one package through the handler, into or out of the bay. */
    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        if (!(player instanceof ServerPlayer) || bay == null || !stillValid(player)
                || index < 0 || index >= slots.size()) {
            return ItemStack.EMPTY;
        }
        IItemHandler handler = bay.itemHandler();
        if (index == 0) {
            ItemStack preview = handler.extractItem(0, 1, true);
            if (preview.isEmpty()) {
                return ItemStack.EMPTY;
            }
            for (int target = 0; target < 36; target++) {
                if (player.getInventory().getItem(target).isEmpty()) {
                    ItemStack taken = handler.extractItem(0, 1, false);
                    player.getInventory().setItem(target, taken);
                    player.getInventory().setChanged();
                    return ItemStack.EMPTY;
                }
            }
            return ItemStack.EMPTY;
        }
        Slot source = slots.get(index);
        ItemStack offered = source.getItem();
        if (offered.isEmpty() || !handler.isItemValid(0, offered)) {
            return ItemStack.EMPTY;
        }
        ItemStack remainder = handler.insertItem(0, offered, false);
        if (remainder.getCount() < offered.getCount()) {
            source.set(remainder);
        }
        return ItemStack.EMPTY;
    }

    private final class BaySlot extends Slot {
        BaySlot(int x, int y) {
            super(new SimpleContainer(1), 0, x, y);
        }

        @Override
        public ItemStack getItem() {
            return bay == null ? super.getItem() : bay.itemHandler().getStackInSlot(0);
        }

        @Override
        public boolean mayPlace(ItemStack stack) {
            if (bay == null) {
                return stack.is(ModItems.SATELLITE_PACKAGE.get()) || stack.is(ModItems.DATA_SATELLITE_PACKAGE.get());
            }
            return stillValid(viewer) && bay.itemHandler().isItemValid(0, stack);
        }

        @Override
        public boolean mayPickup(Player player) {
            return bay == null ? super.mayPickup(player)
                    : stillValid(player) && !bay.itemHandler().extractItem(0, 1, true).isEmpty();
        }

        @Override
        public void set(ItemStack stack) {
            if (bay == null) {
                super.set(stack);
                return;
            }
            if (stack.isEmpty()) {
                bay.itemHandler().extractItem(0, 1, false);
            } else {
                bay.itemHandler().insertItem(0, stack, false);
            }
        }

        @Override
        public ItemStack remove(int amount) {
            return bay == null ? super.remove(amount) : bay.itemHandler().extractItem(0, amount, false);
        }

        @Override
        public int getMaxStackSize() {
            return 1;
        }

        @Override
        public int getMaxStackSize(ItemStack stack) {
            return 1;
        }

        @Override
        public boolean isSameInventory(Slot other) {
            return other instanceof BaySlot;
        }
    }
}
