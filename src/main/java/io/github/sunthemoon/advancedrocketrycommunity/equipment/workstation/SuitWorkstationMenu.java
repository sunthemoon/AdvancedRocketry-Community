package io.github.sunthemoon.advancedrocketrycommunity.equipment.workstation;

import io.github.sunthemoon.advancedrocketrycommunity.equipment.suit.ArmorEquipment;
import io.github.sunthemoon.advancedrocketrycommunity.equipment.suit.EquipmentNbt;
import io.github.sunthemoon.advancedrocketrycommunity.equipment.suit.SuitEquipmentContent;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/** Two detached slot views and fixed vanilla menu button intents. */
public final class SuitWorkstationMenu extends AbstractContainerMenu {
    private final SuitWorkstationBlockEntity owner;
    private final Player viewer;
    private long lastIntent = Long.MIN_VALUE;
    public SuitWorkstationMenu(int id, Inventory inventory, FriendlyByteBuf data) {
        this(id, inventory, (SuitWorkstationBlockEntity) null);
        if (data.readByte() != 1 || data.isReadable()) { throw new IllegalArgumentException("Unsupported workstation menu"); }
    }
    public SuitWorkstationMenu(int id, Inventory inventory, SuitWorkstationBlockEntity owner) {
        super(SuitEquipmentContent.MENU.get(), id); this.owner = owner; viewer = inventory.player;
        SimpleContainer client = new SimpleContainer(2);
        addSlot(new HeldSlot(client, 0, 26, 30)); addSlot(new HeldSlot(client, 1, 134, 30));
        for (int row = 0; row < 3; row++) { for (int column = 0; column < 9; column++) {
            addSlot(new Slot(inventory, 9 + row * 9 + column, 8 + column * 18, 108 + row * 18));
        } }
        for (int column = 0; column < 9; column++) { addSlot(new Slot(inventory, column, 8 + column * 18, 166)); }
    }
    public SuitWorkstationBlockEntity owner() { return owner; }
    @Override public boolean stillValid(Player player) { return player == viewer && (owner == null || owner.admitted(player)); }
    @Override public void clicked(int slot, int button, ClickType type, Player player) {
        if (stillValid(player)) { super.clicked(slot, button, type, player); }
    }
    @Override public boolean clickMenuButton(Player player, int id) {
        if (!(player instanceof ServerPlayer server) || owner == null || !stillValid(player) || id < 0 || id > 6) { return false; }
        long tick = player.level().getGameTime();
        if (lastIntent != Long.MIN_VALUE && tick >= lastIntent && tick - lastIntent < 2) { return false; }
        lastIntent = tick;
        return owner.edit(server, id == 0 ? -1 : (id - 1) % 3, id >= 4);
    }
    @Override public ItemStack quickMoveStack(Player player, int index) {
        if (!(player instanceof ServerPlayer server) || owner == null || !stillValid(player)
                || index < 0 || index >= slots.size()) { return ItemStack.EMPTY; }
        ItemStack source = slots.get(index).getItem();
        if (source.isEmpty() || !EquipmentNbt.safe(source)) { return ItemStack.EMPTY; }
        ItemStack original = source.copy();
        if (index < 2) {
            for (int i = 35; i >= 0; i--) {
                if (player.getInventory().getItem(i).isEmpty() && owner.transfer(server, index, i, false)) { return original; }
            }
        } else {
            int heldSlot = ArmorEquipment.part(source) != null ? 0 : ArmorEquipment.kind(source) != null ? 1 : -1;
            if (heldSlot != -1 && owner.transfer(server, heldSlot, slots.get(index).getContainerSlot(), true)) { return original; }
        }
        return ItemStack.EMPTY;
    }
    private final class HeldSlot extends Slot {
        HeldSlot(SimpleContainer client, int slot, int x, int y) { super(client, slot, x, y); }
        @Override public ItemStack getItem() { return owner == null ? super.getItem() : owner.stack(getContainerSlot()); }
        @Override public int getMaxStackSize() { return 1; }
        @Override public int getMaxStackSize(ItemStack stack) { return 1; }
        @Override public boolean mayPlace(ItemStack stack) {
            return owner == null ? EquipmentNbt.safe(stack) && (getContainerSlot() == 0
                    ? ArmorEquipment.part(stack) != null : ArmorEquipment.kind(stack) != null)
                    : owner.accepts(getContainerSlot(), stack) && stillValid(viewer);
        }
        @Override public boolean mayPickup(Player player) { return stillValid(player); }
        @Override public void set(ItemStack stack) {
            if (owner == null) { super.set(stack); } else { owner.replace(viewer, getContainerSlot(), stack); }
        }
        @Override public ItemStack remove(int amount) {
            if (owner == null) { return super.remove(amount); }
            ItemStack stack = getItem();
            return amount > 0 && owner.replace(viewer, getContainerSlot(), ItemStack.EMPTY) ? stack : ItemStack.EMPTY;
        }
    }
}
