package io.github.sunthemoon.advancedrocketrycommunity.compat.atmosphere;

import io.github.sunthemoon.advancedrocketrycommunity.atmosphere.content.SpaceSuitArmorItem;
import io.github.sunthemoon.advancedrocketrycommunity.config.CommonConfig;
import io.github.sunthemoon.advancedrocketrycommunity.equipment.suit.ArmorEquipment;
import io.github.sunthemoon.advancedrocketrycommunity.equipment.suit.EnchantedSuitOxygen;
import io.github.sunthemoon.advancedrocketrycommunity.equipment.suit.EquipmentNbt;
import io.github.sunthemoon.advancedrocketrycommunity.equipment.suit.TankCanisterRefill;
import io.github.sunthemoon.advancedrocketrycommunity.equipment.suit.TankData;
import io.github.sunthemoon.advancedrocketrycommunity.fluid.CanisterInventoryExchange;
import io.github.sunthemoon.advancedrocketrycommunity.persistence.BoundedNbt;
import io.github.sunthemoon.advancedrocketrycommunity.registry.ModItems;
import java.util.AbstractList;
import java.util.List;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.common.util.FakePlayer;

/** W174 O1: one player's inventory is the sole persistence owner, with no drops or saves. */
public final class SuitCanisterRefill {
    private final SuitEquipmentCatalog catalog;
    SuitCanisterRefill(SuitEquipmentCatalog catalog) { this.catalog = catalog; }

    Prepared prepare(ServerPlayer player, InteractionHand hand) {
        if (!eligible(player) || !CommonConfig.classicEquipmentEnabled()) { return null; }
        ItemStack held = player.getItemInHand(hand);
        TankData.Gas gas = held.is(ModItems.OXYGEN_CANISTER.get()) ? TankData.Gas.OXYGEN
                : held.is(ModItems.HYDROGEN_CANISTER.get()) ? TankData.Gas.HYDROGEN : null;
        ItemStack chest = player.getItemBySlot(EquipmentSlot.CHEST);
        if (gas == null || !EquipmentNbt.safe(chest) || held.hasTag() && !BoundedNbt.fits(held.getTag(), 8192, 16, 1024)
                || catalog.find(chest.getItem()) != null) { return null; }
        CompoundTag original = chest.hasTag() ? chest.getTag().copy() : null;
        int target = CommonConfig.suitOxygenTarget();
        int slot = hand == InteractionHand.MAIN_HAND ? player.getInventory().selected : 40;
        boolean builtin = chest.getItem() instanceof SpaceSuitArmorItem armor && armor.getEquipmentSlot() == EquipmentSlot.CHEST;
        boolean enchanted = !builtin && EnchantedSuitOxygen.eligible(chest, EquipmentSlot.CHEST);
        if (!builtin && !enchanted) { return null; }
        ItemStack detached = chest.copy();
        if (builtin) {
            if (ArmorEquipment.modules(chest) == null) { return null; }
            CompoundTag edited = TankCanisterRefill.prepare(chest.getTag(), gas);
            if (edited == null) { return null; }
            detached.setTag(edited);
        } else {
            int before = EnchantedSuitOxygen.read(chest.getTag());
            if (gas != TankData.Gas.OXYGEN || before < 0 || CommonConfig.suitOxygenTarget() - before < 1000
                    || !EnchantedSuitOxygen.set(detached, before + 1000)) { return null; }
        }
        if (!EquipmentNbt.safe(detached)) { return null; }
        ItemStack empty = new ItemStack(ModItems.EMPTY_CANISTER.get());
        if (held.hasTag()) { empty.setTag(held.getTag().copy()); }
        var exchange = CanisterInventoryExchange.prepare(slots(player), slot, empty);
        Prepared prepared = exchange == null ? null : new Prepared(this, player, hand, slot, chest,
                original, detached, exchange, target);
        return matches(player, prepared) ? prepared : null;
    }
    boolean commit(ServerPlayer player, Prepared prepared) {
        if (!matches(player, prepared)) { return false; }
        // No callbacks, recursive copies, inventory add/drop or capability operations between these publications.
        prepared.exchange.publish(slots(player));
        // setTag/setItemSlot can invoke modded item callbacks; publish to vanilla storage directly instead.
        player.getInventory().armor.set(EquipmentSlot.CHEST.getIndex(), prepared.after);
        player.getInventory().setChanged(); return true;
    }
    private boolean matches(ServerPlayer player, Prepared prepared) {
        if (prepared == null || prepared.owner != this || prepared.player != player || !eligible(player)
                || !CommonConfig.classicEquipmentEnabled() || CommonConfig.suitOxygenTarget() != prepared.target
                || prepared.slot != (prepared.hand == InteractionHand.MAIN_HAND ? player.getInventory().selected : 40)
                || player.getItemBySlot(EquipmentSlot.CHEST) != prepared.chest || prepared.chest.getCount() != 1
                || prepared.chest.hasTag() && !EquipmentNbt.bounded(prepared.chest.getTag())
                || !(prepared.chest.getTag() == null ? prepared.original == null : prepared.chest.getTag().equals(prepared.original))
                || !prepared.exchange.matches(slots(player))) { return false; }
        return true;
    }
    private static boolean eligible(ServerPlayer player) {
        return player.server.isSameThread() && !(player instanceof FakePlayer) && player.isAlive() && !player.isSpectator()
                && player.server.getLevel(player.level().dimension()) == player.level()
                && player.connection != null && player.connection.connection.isConnected()
                && player.server.getPlayerList().getPlayer(player.getUUID()) == player;
    }
    private static List<ItemStack> slots(ServerPlayer player) {
        return new AbstractList<>() {
            @Override public int size() { return player.getInventory().getContainerSize(); }
            @Override public ItemStack get(int slot) { return player.getInventory().getItem(slot); }
            @Override public ItemStack set(int slot, ItemStack stack) {
                return slot == 40 ? player.getInventory().offhand.set(0, stack) : player.getInventory().items.set(slot, stack);
            }
        };
    }
    /** Opaque, operation-local one-shot preparation, never persisted or sent over the network. */
    public static final class Prepared {
        private final SuitCanisterRefill owner;
        private final ServerPlayer player;
        private final InteractionHand hand;
        private final int slot;
        private final ItemStack chest;
        private final CompoundTag original;
        private final ItemStack after;
        private final CanisterInventoryExchange exchange;
        private final int target;
        private Prepared(SuitCanisterRefill owner, ServerPlayer player, InteractionHand hand, int slot, ItemStack chest,
                         CompoundTag original, ItemStack after, CanisterInventoryExchange exchange, int target) {
            this.owner = owner; this.player = player; this.hand = hand; this.slot = slot; this.chest = chest;
            this.original = original; this.after = after; this.exchange = exchange; this.target = target;
        }
    }
}
