package io.github.sunthemoon.advancedrocketrycommunity.compat.atmosphere;

import io.github.sunthemoon.advancedrocketrycommunity.atmosphere.AtmosphereLimits;
import io.github.sunthemoon.advancedrocketrycommunity.atmosphere.content.SpaceSuitArmorItem;
import io.github.sunthemoon.advancedrocketrycommunity.atmosphere.content.SpaceSuitOxygen;
import io.github.sunthemoon.advancedrocketrycommunity.atmosphere.life.OxygenTransfer;
import io.github.sunthemoon.advancedrocketrycommunity.atmosphere.life.OxygenTransferResult;
import io.github.sunthemoon.advancedrocketrycommunity.atmosphere.life.PlayerLifeSupportDecision;
import io.github.sunthemoon.advancedrocketrycommunity.atmosphere.life.PlayerLifeSupportEngine;
import io.github.sunthemoon.advancedrocketrycommunity.atmosphere.life.PlayerLifeSupportInput;
import io.github.sunthemoon.advancedrocketrycommunity.atmosphere.life.SuitReserveTransition;
import io.github.sunthemoon.advancedrocketrycommunity.config.CommonConfig;
import io.github.sunthemoon.advancedrocketrycommunity.equipment.suit.ArmorEquipment;
import io.github.sunthemoon.advancedrocketrycommunity.equipment.suit.EnchantedSuitOxygen;
import io.github.sunthemoon.advancedrocketrycommunity.equipment.suit.EquipmentNbt;
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
    private final SuitCanisterRefill canisters;
    private final SuitOxygenAccess oxygen = new SuitOxygenAccess();
    private boolean operating;

    public SuitEquipmentService(SuitEquipmentCatalog catalog) {
        this.catalog = Objects.requireNonNull(catalog, "catalog");
        canisters = new SuitCanisterRefill(catalog);
    }

    public int countPieces(LivingEntity entity) {
        int count = 0;
        for (EquipmentSlot slot : ARMOR_SLOTS) {
            ItemStack stack = entity.getItemBySlot(slot);
            if (stack.getCount() == 1 && (builtIn(stack, slot) || external(stack, slot) != null || enchanted(stack, slot))) {
                count++;
            }
        }
        return count;
    }

    /** Whether a registered external provider owns this item, enabled or not (ADR-066 §5.1). */
    public boolean claims(net.minecraft.world.item.Item item) {
        return catalog.find(item) != null;
    }

    /** Metadata-only prediction on either physical side. */
    public boolean isChest(ItemStack stack) {
        var piece = catalog.find(stack.getItem());
        return stack.getCount() == 1 && (builtIn(stack, EquipmentSlot.CHEST)
                || piece != null && piece.slot() == EquipmentSlot.CHEST || enchanted(stack, EquipmentSlot.CHEST));
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

    /** Scheduled D1 replenishment and payment publish the active key and reserve in one armor tag. */
    public PlayerLifeSupportDecision decideAndCommit(ServerPlayer player, Reading reading, PlayerLifeSupportInput input) {
        enter(player);
        try {
            boolean builtin = reading.valid && builtIn(reading.stack, EquipmentSlot.CHEST);
            var reserve = builtin ? ArmorEquipment.reserve(reading.stack) : new ArmorEquipment.Reserve(0, 0);
            var step = SuitReserveTransition.tick(input, reserve.stored(), reserve.maximum(),
                    CommonConfig.suitOxygenTarget(), builtin && CommonConfig.classicEquipmentEnabled());
            PlayerLifeSupportDecision decision = step.decision();
            if (decision.oxygenUnits() == input.oxygenUnits() && step.transferredUnits() == 0) { return decision; }
            if (step.transferredUnits() > 0) {
                ItemStack detached = reading.stack.copy();
                if (!SpaceSuitOxygen.set(detached, decision.oxygenUnits()) || !ArmorEquipment.setReserve(detached, step.reserveUnits())
                        || !sameNative(player, reading) || !EquipmentNbt.safe(detached)) {
                    return unpaid(player, input);
                }
                reading.stack.setTag(detached.getTag()); return decision;
            }
            return commit(player, reading, decision.oxygenUnits(), prepare(reading, decision.oxygenUnits()))
                    ? decision : unpaid(player, input);
        } finally { operating = false; }
    }

    private PlayerLifeSupportDecision unpaid(ServerPlayer player, PlayerLifeSupportInput input) {
        return PlayerLifeSupportEngine.unpaid(input, countPieces(player));
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
            int target = reading.external == null ? CommonConfig.suitOxygenTarget() : AtmosphereLimits.SUIT_OXYGEN_CAPACITY;
            if (reading.oxygenUnits() > target) { return rejected(reading.oxygenUnits()); }
            OxygenTransferResult transfer = OxygenTransfer.fillOneCanister(reading.oxygenUnits(), target);
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

    public SuitCanisterRefill.Prepared prepareTankCanister(ServerPlayer player, InteractionHand hand) {
        enter(player);
        try { return canisters.prepare(player, hand); }
        finally { operating = false; }
    }

    public boolean commitTankCanister(ServerPlayer player, SuitCanisterRefill.Prepared prepared) {
        enter(player);
        try { return canisters.commit(player, prepared); }
        finally { operating = false; }
    }

    public boolean fillTankCanister(ServerPlayer player, InteractionHand hand) {
        return commitTankCanister(player, prepareTankCanister(player, hand));
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
            return new Reading(stack, data.oxygenUnits(), data.status() == SpaceSuitOxygen.DataStatus.VALID
                    && ArmorEquipment.modules(stack) != null, null);
        }
        var piece = external(stack, EquipmentSlot.CHEST);
        if (piece == null && enchanted(stack, EquipmentSlot.CHEST)) {
            int amount = EnchantedSuitOxygen.read(stack.getTag());
            return new Reading(stack, Math.max(0, amount), amount >= 0, null);
        }
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
            if (!sameNative(player, reading)) { return false; }
            ItemStack detached = stack.copy();
            boolean preparedNative = builtIn(stack, EquipmentSlot.CHEST)
                    ? SpaceSuitOxygen.set(detached, units) : EnchantedSuitOxygen.set(detached, units);
            if (!preparedNative || !EquipmentNbt.safe(detached)) { return false; }
            stack.setTag(detached.getTag()); return true;
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

    private boolean enchanted(ItemStack stack, EquipmentSlot slot) {
        return CommonConfig.classicEquipmentEnabled() && catalog.find(stack.getItem()) == null
                && EnchantedSuitOxygen.eligible(stack, slot);
    }
    private boolean sameNative(ServerPlayer player, Reading reading) {
        ItemStack stack = reading.stack;
        return reading.valid && player.getItemBySlot(EquipmentSlot.CHEST) == stack && EquipmentNbt.safe(stack)
                && (stack.getTag() == null ? reading.original == null : stack.getTag().equals(reading.original))
                && (builtIn(stack, EquipmentSlot.CHEST) ? ArmorEquipment.modules(stack) != null : enchanted(stack, EquipmentSlot.CHEST));
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
        private final CompoundTag original;

        private Reading(ItemStack stack, int units, boolean valid, SuitOxygenAccess.Read external) {
            this.stack = stack;
            this.units = units;
            this.valid = valid;
            this.external = external;
            original = valid && external == null && stack.hasTag() && EquipmentNbt.safe(stack) ? stack.getTag().copy() : null;
        }

        public int oxygenUnits() {
            return valid ? units : 0;
        }
    }
}
