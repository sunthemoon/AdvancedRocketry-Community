package io.github.sunthemoon.advancedrocketrycommunity.equipment.suit;

import io.github.sunthemoon.advancedrocketrycommunity.model.api.equipment.ModuleKind;
import io.github.sunthemoon.advancedrocketrycommunity.model.api.equipment.SuitPart;
import net.minecraft.nbt.CompoundTag;

/** Detached whole-canister edit of the existing, sole module/gas root. */
public final class TankCanisterRefill {
    public static CompoundTag prepare(CompoundTag armor, TankData.Gas gas) {
        if (gas == null || gas == TankData.Gas.EMPTY || armor != null && !EquipmentNbt.bounded(armor)) { return null; }
        var modules = ArmorRootCodec.read(armor, SuitPart.CHEST);
        if (modules == null) { return null; }
        ArmorRootCodec.Module matching = null;
        ArmorRootCodec.Module empty = null;
        for (var module : modules) {
            if (module.kind() != ModuleKind.PRESSURE_TANK) { continue; }
            TankData tank = tank(module);
            if (tank.gas() == gas) { matching = module; }
            if (tank.gas() == TankData.Gas.EMPTY && (empty == null || module.position() < empty.position())) { empty = module; }
        }
        var selected = matching != null ? matching : empty;
        if (selected == null) { return null; }
        TankData tank = tank(selected);
        if (tank.maximum() - tank.stored() < 1000) { return null; }
        selected.stack().getCompound("tag").put(TankData.KEY, new TankData(gas, tank.stored() + 1000, tank.maximum()).encode());
        CompoundTag encoded = ArmorRootCodec.encode(modules);
        if (encoded == null) { return null; }
        CompoundTag result = armor == null ? new CompoundTag() : armor.copy(); result.put(ArmorRootCodec.KEY, encoded);
        return EquipmentNbt.bounded(result) && ArmorRootCodec.read(result, SuitPart.CHEST) != null ? result : null;
    }
    private static TankData tank(ArmorRootCodec.Module module) {
        return TankData.read(module.stack().getCompound("tag").get(TankData.KEY), TankData.baseCapacity(module.stack().getString("id")), 1);
    }
    private TankCanisterRefill() { }
}
