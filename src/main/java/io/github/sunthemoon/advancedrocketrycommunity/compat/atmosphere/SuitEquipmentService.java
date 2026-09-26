package io.github.sunthemoon.advancedrocketrycommunity.compat.atmosphere;

import io.github.sunthemoon.advancedrocketrycommunity.atmosphere.AtmosphereLimits;
import io.github.sunthemoon.advancedrocketrycommunity.atmosphere.content.SpaceSuitArmorItem;
import io.github.sunthemoon.advancedrocketrycommunity.atmosphere.content.SpaceSuitOxygen;
import io.github.sunthemoon.advancedrocketrycommunity.atmosphere.life.OxygenTransfer;
import io.github.sunthemoon.advancedrocketrycommunity.atmosphere.life.OxygenTransferResult;
import io.github.sunthemoon.advancedrocketrycommunity.registry.ModItems;
import java.util.Objects;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;

/** Server-owned resource commit; callbacks never receive a live player or ItemStack. */
public final class SuitEquipmentService {
    private static final EquipmentSlot[] ARMOR_SLOTS = {
            EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};
    private final SuitEquipmentCatalog catalog;
    private final SuitOxygenAccess oxygen = new SuitOxygenAccess();
    private boolean operating;

    public SuitEquipmentService(SuitEquipmentCatalog catalog) {
        this.catalog = Objects.requireNonNull(catalog, "catalog");
    }

    public int countPieces(LivingEntity entity) {
        int count = 0;
        for (EquipmentSlot slot : ARMOR_SLOTS) {
            ItemStack stack = entity.getItemBySlot(slot);
            if (stack.getCount() == 1 && (builtIn(stack, slot) || external(stack, slot) != null)) {
                count++;
            }
        }
        return count;
    }

    /** Metadata-only prediction on either physical side. */
    public boolean isChest(ItemStack stack) {
        var piece = catalog.find(stack.getItem());
        return stack.getCount() == 1 && (builtIn(stack, EquipmentSlot.CHEST)
                || piece != null && piece.slot() == EquipmentSlot.CHEST);
    }

    public Reading readOxygen(ServerPlayer player) {
        enter(player);
        try {
            return read(player.getItemBySlot(EquipmentSlot.CHEST));
        } finally {
            operating = false;
        }
    }

    public boolean setOxygen(ServerPlayer player, Reading reading, int units) {
        enter(player);
        try {
            CompoundTag prepared = prepare(reading, units);
            return commit(player, reading, units, prepared);
        } finally {
            operating = false;
        }
    }

    public OxygenTransferResult fillOneCanister(ServerPlayer player, InteractionHand hand) {
        enter(player);
        try {
            ItemStack held = player.getItemInHand(hand);
            int count = held.getCount();
            if (!held.is(ModItems.OXYGEN_CANISTER.get()) || count <= 0) {
                return rejected(0);
            }
            Reading reading = read(player.getItemBySlot(EquipmentSlot.CHEST));
            if (!reading.valid) {
                return rejected(0);
            }
            OxygenTransferResult transfer = OxygenTransfer.fillOneCanister(
                    reading.oxygenUnits(), AtmosphereLimits.SUIT_OXYGEN_CAPACITY);
            if (!transfer.accepted()) {
                return transfer;
            }
            CompoundTag prepared = prepare(reading, transfer.oxygenUnits());
            if (player.getItemInHand(hand) != held || held.getCount() != count
                    || !held.is(ModItems.OXYGEN_CANISTER.get())
                    || !commit(player, reading, transfer.oxygenUnits(), prepared)) {
                return rejected(reading.oxygenUnits());
            }
            if (!player.getAbilities().instabuild) {
                held.shrink(1);
                ItemStack empty = new ItemStack(ModItems.EMPTY_CANISTER.get());
                if (!player.getInventory().add(empty)) {
                    player.drop(empty, false);
                }
            }
            return transfer;
        } finally {
            operating = false;
        }
    }

    public void clear() {
        if (operating) {
            throw new IllegalStateException("Cannot clear active suit service");
        }
        oxygen.clear();
    }

    private Reading read(ItemStack stack) {
        if (stack.getCount() != 1) {
            return new Reading(stack, 0, false, null);
        }
        if (builtIn(stack, EquipmentSlot.CHEST)) {
            var data = SpaceSuitOxygen.read(stack);
            return new Reading(stack, data.oxygenUnits(), data.status() == SpaceSuitOxygen.DataStatus.VALID, null);
        }
        var piece = external(stack, EquipmentSlot.CHEST);
        var data = piece == null ? null : oxygen.read(piece.provider(), owned(stack));
        return new Reading(stack, data == null ? 0 : data.oxygen(), data != null, data);
    }

    private CompoundTag prepare(Reading reading, int units) {
        return reading.valid && reading.external != null ? oxygen.prepare(reading.external, units) : null;
    }

    private boolean commit(ServerPlayer player, Reading reading, int units, CompoundTag prepared) {
        ItemStack stack = reading.stack;
        if (!reading.valid || player.getItemBySlot(EquipmentSlot.CHEST) != stack || stack.getCount() != 1
                || units < 0 || units > AtmosphereLimits.SUIT_OXYGEN_CAPACITY) {
            return false;
        }
        if (reading.external == null) {
            return SpaceSuitOxygen.read(stack).equals(new SpaceSuitOxygen.ReadResult(
                    SpaceSuitOxygen.DataStatus.VALID, reading.oxygenUnits())) && SpaceSuitOxygen.set(stack, units);
        }
        Tag original = reading.external.original();
        Tag actual = owned(stack);
        if (prepared == null || oxygen.disabled(reading.external.provider())
                || !(actual == null && original == null || actual instanceof CompoundTag compound
                && SuitOxygenPayloads.bounded(compound) && actual.equals(original))) {
            return false;
        }
        stack.getOrCreateTag().put(SuitOxygenPayloads.KEY, prepared);
        return true;
    }

    private SuitEquipmentCatalog.Piece external(ItemStack stack, EquipmentSlot slot) {
        var piece = catalog.find(stack.getItem());
        return piece != null && piece.slot() == slot && !oxygen.disabled(piece.provider()) ? piece : null;
    }

    private static boolean builtIn(ItemStack stack, EquipmentSlot slot) {
        return stack.getItem() instanceof SpaceSuitArmorItem armor && armor.getEquipmentSlot() == slot;
    }

    private static Tag owned(ItemStack stack) {
        return stack.hasTag() ? stack.getTag().get(SuitOxygenPayloads.KEY) : null;
    }

    private static OxygenTransferResult rejected(int units) {
        return new OxygenTransferResult(false, units, 0);
    }

    private void enter(ServerPlayer player) {
        if (!player.serverLevel().getServer().isSameThread() || operating) {
            throw new IllegalStateException("Suit operations require the non-reentrant logical server thread");
        }
        operating = true;
    }

    /** Opaque operation-local readback. It grants no public API or independent mutation authority. */
    public static final class Reading {
        private final ItemStack stack;
        private final int units;
        private final boolean valid;
        private final SuitOxygenAccess.Read external;

        private Reading(ItemStack stack, int units, boolean valid, SuitOxygenAccess.Read external) {
            this.stack = stack;
            this.units = units;
            this.valid = valid;
            this.external = external;
        }

        public int oxygenUnits() {
            return valid ? units : 0;
        }
    }
}
