package io.github.sunthemoon.advancedrocketrycommunity.equipment.workstation;

import io.github.sunthemoon.advancedrocketrycommunity.config.CommonConfig;
import io.github.sunthemoon.advancedrocketrycommunity.equipment.suit.ArmorEquipment;
import io.github.sunthemoon.advancedrocketrycommunity.equipment.suit.EquipmentNbt;
import io.github.sunthemoon.advancedrocketrycommunity.equipment.suit.SuitEquipmentContent;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.common.util.FakePlayer;

/** Menu-only holder. Every resource edit is prepared before one bounded publication. */
public final class SuitWorkstationBlockEntity extends BlockEntity implements MenuProvider {
    private final ItemStack[] held = {ItemStack.EMPTY, ItemStack.EMPTY};
    private Tag refused;
    private boolean operating;

    public SuitWorkstationBlockEntity(BlockPos pos, BlockState state) { super(SuitEquipmentContent.ENTITY.get(), pos, state); }
    public boolean repairRequired() { return refused != null; }
    public boolean available() {
        if (isRemoved() || operating || repairRequired() || !(level instanceof ServerLevel server)
                || !server.getServer().isSameThread() || server.getServer().getLevel(server.dimension()) != server) { return false; }
        var chunk = server.getChunkSource().getChunkNow(worldPosition.getX() >> 4, worldPosition.getZ() >> 4);
        return chunk != null && chunk.getBlockEntities().get(worldPosition) == this
                && getBlockState().is(SuitEquipmentContent.BLOCK.get());
    }
    public boolean admitted(Player player) {
        return available() && player instanceof ServerPlayer connected && !(player instanceof FakePlayer)
                && player.isAlive() && !player.isSpectator() && player.level() == level
                && connected.connection != null && connected.connection.connection.isConnected()
                && connected.server.getPlayerList().getPlayer(player.getUUID()) == player
                && player.distanceToSqr(worldPosition.getX() + .5, worldPosition.getY() + .5, worldPosition.getZ() + .5) <= 64
                && level.mayInteract(player, worldPosition);
    }
    private boolean menu(Player player) {
        return admitted(player) && player.containerMenu instanceof SuitWorkstationMenu menu && menu.owner() == this;
    }
    public ItemStack stack(int slot) { return slot >= 0 && slot < 2 && EquipmentNbt.safe(held[slot]) ? held[slot].copy() : ItemStack.EMPTY; }
    public boolean accepts(int slot, ItemStack stack) {
        return EquipmentNbt.safe(stack) && (stack.isEmpty() || (slot == 0
                ? ArmorEquipment.part(stack) != null : slot == 1 && ArmorEquipment.kind(stack) != null))
                && slot >= 0 && slot < 2 && SuitWorkstationSave.encode(
                        slot == 0 ? stack : held[0], slot == 1 ? stack : held[1]) != null;
    }
    public boolean replace(Player player, int slot, ItemStack stack) {
        if (slot < 0 || slot > 1 || !menu(player) || !accepts(slot, stack)
                || SuitWorkstationSave.encode(slot == 0 ? stack : held[0], slot == 1 ? stack : held[1]) == null) { return false; }
        operating = true;
        try { held[slot] = stack.copy(); setChanged(); return true; }
        finally { operating = false; }
    }
    /** INSTALL=-1; otherwise REMOVE the fixed position, optionally into an empty inventory cell. */
    public boolean edit(ServerPlayer player, int position, boolean toInventory) {
        if (!menu(player) || position < -1 || position > 2
                || position == -1 && !CommonConfig.classicEquipmentEnabled()) { return false; }
        operating = true;
        try {
            if (position != -1 && !toInventory && !held[1].isEmpty()) { return false; }
            ArmorEquipment.Edit edit = position == -1 ? ArmorEquipment.install(held[0], held[1])
                    : ArmorEquipment.remove(held[0], position);
            if (edit == null) { return false; }
            if (SuitWorkstationSave.encode(edit.armor(), toInventory ? held[1] : edit.transfer()) == null) { return false; }
            int inventorySlot = -1;
            if (toInventory) {
                if (position == -1) { return false; }
                for (int i = 0; i < 36; i++) {
                    if (player.getInventory().getItem(i).isEmpty()) { inventorySlot = i; break; }
                }
                if (inventorySlot == -1) { return false; }
            }
            // All decisions and bounded copies precede the first write; no callback between these writes.
            held[0] = edit.armor();
            if (toInventory) { player.getInventory().setItem(inventorySlot, edit.transfer()); }
            else { held[1] = edit.transfer(); }
            setChanged(); player.getInventory().setChanged(); return true;
        } finally { operating = false; }
    }
    /** Shift-click publishes the exact detached source and destination once, never vanilla copy merging. */
    public boolean transfer(ServerPlayer player, int heldSlot, int inventorySlot, boolean intoWorkstation) {
        if (!menu(player) || heldSlot < 0 || heldSlot > 1 || inventorySlot < 0 || inventorySlot >= 36) { return false; }
        ItemStack inventory = player.getInventory().getItem(inventorySlot);
        ItemStack source = intoWorkstation ? inventory : held[heldSlot];
        ItemStack destination = intoWorkstation ? held[heldSlot] : inventory;
        if (source.isEmpty() || !destination.isEmpty() || !EquipmentNbt.safe(source)
                || intoWorkstation && (!accepts(heldSlot, source) || SuitWorkstationSave.encode(
                        heldSlot == 0 ? source : held[0], heldSlot == 1 ? source : held[1]) == null)) { return false; }
        ItemStack prepared = source.copy();
        operating = true;
        try {
            held[heldSlot] = intoWorkstation ? prepared : ItemStack.EMPTY;
            player.getInventory().setItem(inventorySlot, intoWorkstation ? ItemStack.EMPTY : prepared);
            setChanged(); player.getInventory().setChanged(); return true;
        } finally { operating = false; }
    }
    public void dropContents() {
        if (!available()) { return; }
        operating = true;
        try {
            for (int i = 0; i < 2; i++) {
                ItemStack stack = held[i]; held[i] = ItemStack.EMPTY;
                if (!stack.isEmpty()) { net.minecraft.world.Containers.dropItemStack(level,
                        worldPosition.getX() + .5, worldPosition.getY() + .5, worldPosition.getZ() + .5, stack); }
            }
            setChanged();
        } finally { operating = false; }
    }
    @Override protected void saveAdditional(CompoundTag outer) {
        super.saveAdditional(outer);
        if (refused != null) { outer.put(SuitWorkstationSave.KEY, SuitWorkstationSave.bounded(refused) ? refused.copy() : refused); }
        else {
            CompoundTag saved = SuitWorkstationSave.encode(held[0], held[1]);
            if (saved != null) { outer.put(SuitWorkstationSave.KEY, saved); }
        }
    }
    @Override public void load(CompoundTag outer) {
        super.load(outer); held[0] = ItemStack.EMPTY; held[1] = ItemStack.EMPTY; refused = null;
        Tag raw = outer.get(SuitWorkstationSave.KEY);
        if (raw == null) { return; }
        ItemStack[] decoded = SuitWorkstationSave.decode(raw);
        if (decoded == null) { refused = SuitWorkstationSave.bounded(raw) ? raw.copy() : raw; }
        else { held[0] = decoded[0]; held[1] = decoded[1]; }
    }
    @Override public Component getDisplayName() { return Component.translatable("block.advancedrocketrycommunity.suit_workstation"); }
    @Override public AbstractContainerMenu createMenu(int id, Inventory inventory, Player player) {
        return admitted(player) ? new SuitWorkstationMenu(id, inventory, this) : null;
    }
}
