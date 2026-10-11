package io.github.sunthemoon.advancedrocketrycommunity.equipment.suit;

import io.github.sunthemoon.advancedrocketrycommunity.atmosphere.content.SpaceSuitArmorItem;
import io.github.sunthemoon.advancedrocketrycommunity.atmosphere.content.SpaceSuitOxygen;
import io.github.sunthemoon.advancedrocketrycommunity.config.CommonConfig;
import io.github.sunthemoon.advancedrocketrycommunity.model.api.equipment.EquipmentSummary;
import io.github.sunthemoon.advancedrocketrycommunity.model.api.equipment.ModuleKind;
import io.github.sunthemoon.advancedrocketrycommunity.model.api.equipment.SuitPart;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.ForgeRegistries;

/** Detached domain edits; only the workstation and oxygen service publish them. */
public final class ArmorEquipment {
    public record Edit(ItemStack armor, ItemStack transfer) { }
    public record Reserve(int stored, int maximum) { }

    public static SuitPart part(ItemStack stack) {
        if (!(stack.getItem() instanceof SpaceSuitArmorItem armor) || stack.getCount() != 1) { return null; }
        return switch (armor.getEquipmentSlot()) {
            case HEAD -> SuitPart.HEAD; case CHEST -> SuitPart.CHEST;
            case LEGS -> SuitPart.LEGS; case FEET -> SuitPart.FEET; default -> null;
        };
    }
    public static ModuleKind kind(ItemStack stack) {
        var id = ForgeRegistries.ITEMS.getKey(stack.getItem());
        return id == null ? null : ModuleKind.forItem(id.toString());
    }
    public static List<ArmorRootCodec.Module> modules(ItemStack armor) {
        SuitPart part = part(armor);
        if (part == null || !EquipmentNbt.safe(armor)) { return null; }
        if (part == SuitPart.CHEST && SpaceSuitOxygen.read(armor).status() != SpaceSuitOxygen.DataStatus.VALID) { return null; }
        return ArmorRootCodec.read(armor.getTag(), part);
    }
    public static Edit install(ItemStack armor, ItemStack component) {
        List<ArmorRootCodec.Module> saved = modules(armor);
        ModuleKind kind = kind(component);
        if (saved == null || kind == null || !EquipmentNbt.safe(component) || component.isEmpty()) { return null; }
        ItemStack prepared = component.copy();
        if (kind == ModuleKind.PRESSURE_TANK) {
            int base = TankData.baseCapacity(ForgeRegistries.ITEMS.getKey(prepared.getItem()).toString());
            TankData tank = TankData.read(prepared.hasTag() ? prepared.getTag().get(TankData.KEY) : null,
                    base, CommonConfig.suitTankMultiplier());
            if (tank == null) { return null; }
            prepared.getOrCreateTag().put(TankData.KEY, tank.encode());
        }
        CompoundTag serialized = prepared.serializeNBT();
        if (!ArmorRootCodec.validStack(serialized)) { return null; }
        SuitPart part = part(armor);
        for (int position = 0; position < part.positions(); position++) {
            if (!kind.fits(part, position)) { continue; }
            var edited = new ArrayList<>(saved);
            edited.add(new ArmorRootCodec.Module(position, kind, serialized));
            ItemStack result = write(armor, edited);
            if (result != null) { return new Edit(result, ItemStack.EMPTY); }
        }
        return null;
    }
    public static Edit remove(ItemStack armor, int position) {
        List<ArmorRootCodec.Module> saved = modules(armor);
        if (saved == null) { return null; }
        var edited = new ArrayList<>(saved);
        for (ArmorRootCodec.Module module : saved) {
            if (module.position() != position) { continue; }
            ItemStack removed = ItemStack.of(module.stack());
            if (removed.isEmpty() || kind(removed) != module.kind() || !EquipmentNbt.safe(removed)) { return null; }
            edited.remove(module);
            ItemStack result = write(armor, edited);
            return result == null ? null : new Edit(result, removed);
        }
        return null;
    }
    private static ItemStack write(ItemStack original, List<ArmorRootCodec.Module> modules) {
        CompoundTag root = ArmorRootCodec.encode(modules);
        if (root == null) { return null; }
        CompoundTag probe = new CompoundTag(); probe.put(ArmorRootCodec.KEY, root);
        if (ArmorRootCodec.read(probe, part(original)) == null) { return null; }
        ItemStack result = original.copy(); result.getOrCreateTag().put(ArmorRootCodec.KEY, root);
        return EquipmentNbt.safe(result) ? result : null;
    }
    public static Reserve reserve(ItemStack armor) {
        var modules = modules(armor);
        if (modules == null || part(armor) != SuitPart.CHEST) { return new Reserve(0, 0); }
        for (var module : modules) {
            if (module.kind() != ModuleKind.PRESSURE_TANK) { continue; }
            TankData tank = tank(module);
            if (tank.gas() == TankData.Gas.OXYGEN) { return new Reserve(tank.stored(), tank.maximum()); }
        }
        return new Reserve(0, 0);
    }
    /** Called on a detached chest within the same publication as the active-buffer edit. */
    public static boolean setReserve(ItemStack detached, int amount) {
        var saved = modules(detached);
        if (saved == null || part(detached) != SuitPart.CHEST) { return false; }
        for (var module : saved) {
            if (module.kind() != ModuleKind.PRESSURE_TANK) { continue; }
            TankData tank = tank(module);
            if (tank.gas() != TankData.Gas.OXYGEN) { continue; }
            if (amount < 0 || amount > tank.maximum()) { return false; }
            module.stack().getCompound("tag").put(TankData.KEY,
                    new TankData(tank.gas(), amount, tank.maximum()).encode());
            ItemStack prepared = write(detached, saved);
            if (prepared == null) { return false; }
            detached.setTag(prepared.getTag()); return true;
        }
        return amount == 0;
    }
    private static TankData tank(ArmorRootCodec.Module module) {
        return TankData.read(module.stack().getCompound("tag").get(TankData.KEY),
                TankData.baseCapacity(module.stack().getString("id")), 1);
    }
    public static EquipmentSummary summarize(ServerPlayer player) {
        if (!player.server.isSameThread()) { throw new IllegalStateException("Equipment summary requires server thread"); }
        if (!CommonConfig.classicEquipmentEnabled()) { return EquipmentSummary.EMPTY; }
        var kinds = new HashSet<ModuleKind>();
        for (EquipmentSlot slot : List.of(EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET)) {
            ItemStack stack = player.getItemBySlot(slot);
            if (!(stack.getItem() instanceof SpaceSuitArmorItem armor) || armor.getEquipmentSlot() != slot) { continue; }
            var saved = modules(stack);
            if (saved != null) { saved.forEach(module -> kinds.add(module.kind())); }
        }
        return new EquipmentSummary(kinds);
    }
    private ArmorEquipment() { }
}
