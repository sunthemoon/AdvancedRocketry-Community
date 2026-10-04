package io.github.sunthemoon.advancedrocketrycommunity.machine.combustion;

import io.github.sunthemoon.advancedrocketrycommunity.registry.ModBlocks;
import io.github.sunthemoon.advancedrocketrycommunity.registry.ModMenuTypes;
import javax.annotation.Nullable;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.common.util.FakePlayer;

/** Fixed versioned open data; split burn fields survive the vanilla signed-short transport. */
public final class CombustionGeneratorMenu extends AbstractContainerMenu {
    public static final int DATA_COUNT = 6;
    private final ContainerData data;
    private final ContainerLevelAccess access;
    @Nullable private final CombustionGeneratorBlockEntity generator;

    public CombustionGeneratorMenu(int id, Inventory inventory, FriendlyByteBuf buffer) {
        this(id, inventory, null, readOpenData(buffer), ContainerLevelAccess.NULL);
    }

    public CombustionGeneratorMenu(int id, Inventory inventory, CombustionGeneratorBlockEntity generator) {
        this(id, inventory, generator, liveData(generator),
                ContainerLevelAccess.create(generator.getLevel(), generator.getBlockPos()));
    }

    private CombustionGeneratorMenu(int id, Inventory inventory, @Nullable CombustionGeneratorBlockEntity generator,
            ContainerData data, ContainerLevelAccess access) {
        super(ModMenuTypes.COMBUSTION_GENERATOR.get(), id);
        this.generator = generator;
        this.data = data;
        this.access = access;
        checkContainerDataCount(data, DATA_COUNT);
        addSlot(new FuelSlot(new SimpleContainer(1)));
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
        buffer.writeVarInt(-1);
        buffer.writeVarInt(1);
    }

    public static ContainerData readOpenData(FriendlyByteBuf buffer) {
        if (buffer.readVarInt() != -1 || buffer.readVarInt() != 1 || buffer.isReadable()) {
            throw new IllegalArgumentException("Unsupported combustion menu format");
        }
        return new SimpleContainerData(DATA_COUNT);
    }

    public static ContainerData liveData(CombustionGeneratorBlockEntity generator) {
        return new ContainerData() {
            @Override public int getCount() { return DATA_COUNT; }
            @Override public void set(int index, int value) { }
            @Override public int get(int index) {
                CombustionBurn.State state = generator.burnState();
                return switch (index) {
                    case 0 -> state.energy();
                    case 1 -> state.duration() & 0xFFFF;
                    case 2 -> state.duration() >>> 16;
                    case 3 -> state.remaining() & 0xFFFF;
                    case 4 -> state.remaining() >>> 16;
                    case 5 -> generator.status().ordinal();
                    default -> 0;
                };
            }
        };
    }

    public int energy() { return Math.max(0, Math.min(CombustionBurn.CAPACITY, data.get(0) & 0xFFFF)); }
    public int duration() { return joined(1); }
    public int remaining() { return joined(3); }
    private int joined(int first) {
        long value = (data.get(first) & 0xFFFFL) | ((data.get(first + 1) & 0xFFFFL) << 16);
        return (int) Math.min(CombustionBurn.MAX_BURN_TICKS, value);
    }
    public CombustionBurn.Status status() {
        int value = data.get(5);
        return value >= 0 && value < CombustionBurn.Status.values().length
                ? CombustionBurn.Status.values()[value] : CombustionBurn.Status.REPAIR_REQUIRED;
    }

    @Override public boolean stillValid(Player player) {
        return !player.isSpectator() && !(player instanceof FakePlayer)
                && (generator == null || (generator.available() && generator.getLevel() == player.level()))
                && stillValid(access, player, ModBlocks.COMBUSTION_GENERATOR.get());
    }

    @Override public void clicked(int slot, int button, ClickType type, Player player) {
        if (stillValid(player)) {
            super.clicked(slot, button, type, player);
        }
    }

    @Override public ItemStack quickMoveStack(Player player, int index) {
        if (!stillValid(player) || index < 0 || index >= slots.size()) {
            return ItemStack.EMPTY;
        }
        Slot slot = slots.get(index);
        ItemStack stack = slot.getItem();
        if (stack.isEmpty() || !slot.mayPickup(player)) {
            return ItemStack.EMPTY;
        }
        if (index != 0 && !CombustionSave.safeStack(stack)) {
            return ItemStack.EMPTY;
        }
        ItemStack before = stack.copy();
        if (index != 0 && generator != null) {
            // Vanilla's occupied-slot merge mutates getItem() and only calls setChanged(). Our fuel view is a copy:
            // commit insertion explicitly, then retain the exact unaccepted player remainder.
            ItemStack remainder = generator.insertFuel(stack, false);
            if (remainder.getCount() == stack.getCount()) {
                return ItemStack.EMPTY;
            }
            slot.set(remainder);
            slot.onTake(player, remainder);
            return before;
        }
        boolean moved = index == 0 ? moveItemStackTo(stack, 1, 37, true)
                : moveItemStackTo(stack, 0, 1, false);
        if (!moved) {
            return ItemStack.EMPTY;
        }
        slot.set(stack.isEmpty() ? ItemStack.EMPTY : stack);
        slot.onTake(player, stack);
        return before;
    }

    /** Unlike SlotItemHandler, querying the slot limit never temporarily empties the authoritative bank. */
    private final class FuelSlot extends Slot {
        private FuelSlot(SimpleContainer client) { super(client, 0, 44, 34); }
        @Override public ItemStack getItem() {
            return generator == null ? super.getItem() : generator.fuelStack();
        }
        @Override public void set(ItemStack stack) {
            if (generator == null) {
                super.set(stack);
            } else {
                generator.setFuelFromMenu(stack);
            }
        }
        @Override public boolean mayPlace(ItemStack stack) {
            return generator == null || generator.canInsert(stack);
        }
        @Override public boolean mayPickup(Player player) {
            return stillValid(player) && (generator == null || !generator.fuelStack().isEmpty());
        }
        @Override public ItemStack remove(int amount) {
            return generator == null ? super.remove(amount) : generator.extractFuel(amount, false);
        }
        @Override public int getMaxStackSize(ItemStack stack) { return Math.min(64, stack.getMaxStackSize()); }
    }
}
