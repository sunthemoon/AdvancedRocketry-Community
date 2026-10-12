package io.github.sunthemoon.advancedrocketrycommunity.model.api.equipment;

import java.util.Set;

/** Server-produced immutable worn-equipment facts, without nested component NBT. */
public record EquipmentSummary(Set<ModuleKind> installed) {
    public static final EquipmentSummary EMPTY = new EquipmentSummary(Set.of());
    public EquipmentSummary { installed = Set.copyOf(installed); }
    public boolean beaconFinderEnabled() { return installed.contains(ModuleKind.BEACON_FINDER); }
}
