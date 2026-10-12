package io.github.sunthemoon.advancedrocketrycommunity.rocket.persistence;

import io.github.sunthemoon.advancedrocketrycommunity.rocket.model.RocketNativeResources;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.model.RocketPropulsion;
import java.util.Set;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;

/** Independent descriptor schema, also included in the snapshot content hash. */
public final class RocketPropulsionCodec {
    private static final Set<String> KEYS = Set.of("schema", "mode", "primary", "oxidizer", "numerator",
            "denominator", "thrust_units", "capacity_units", "core_units", "gravity", "required", "hand", "fuels");
    private RocketPropulsionCodec() { }
    public static CompoundTag encode(RocketPropulsion value) {
        CompoundTag root = new CompoundTag();
        root.putInt("schema", RocketPropulsion.SCHEMA); root.putString("mode", value.mode().name());
        root.putLong("primary", value.primaryCapacity()); root.putLong("oxidizer", value.oxidizerCapacity());
        root.putLong("numerator", value.coefficientNumerator()); root.putLong("denominator", value.coefficientDenominator());
        root.putLong("thrust_units", value.settings().thrustUnits());
        root.putLong("capacity_units", value.settings().capacityUnits());
        root.putLong("core_units", value.settings().coreUnits());
        root.putBoolean("gravity", value.settings().gravityAffectsFuels());
        root.putBoolean("required", value.settings().rocketsRequireFuel());
        root.putBoolean("hand", value.settings().canBeFueledByHand());
        var fuels = new CompoundTag();
        value.settings().allowedFluids().forEach((role, ids) -> {
            var list = new net.minecraft.nbt.ListTag(); ids.forEach(id -> list.add(net.minecraft.nbt.StringTag.valueOf(id)));
            fuels.put(role.name(), list);
        });
        root.put("fuels", fuels);
        return root;
    }
    public static RocketPropulsion decode(CompoundTag root) {
        if (!RocketNativeResources.bounded(root, 12_288) || !root.getAllKeys().equals(KEYS)
                || !root.contains("schema", Tag.TAG_INT) || root.getInt("schema") != RocketPropulsion.SCHEMA
                || !root.contains("mode", Tag.TAG_STRING)) { throw new IllegalArgumentException("Unknown propulsion root"); }
        for (String key : Set.of("primary", "oxidizer", "numerator", "denominator", "thrust_units", "capacity_units", "core_units")) {
            if (!root.contains(key, Tag.TAG_LONG)) { throw new IllegalArgumentException("Invalid propulsion number"); }
        }
        for (String key : Set.of("gravity", "required", "hand")) {
            if (!root.contains(key, Tag.TAG_BYTE) || root.getByte(key) < 0 || root.getByte(key) > 1) {
                throw new IllegalArgumentException("Invalid propulsion switch");
            }
        }
        if (!root.contains("fuels", Tag.TAG_COMPOUND)) { throw new IllegalArgumentException("Missing captured Fluid allowlists"); }
        var fuels = root.getCompound("fuels");
        if (!fuels.getAllKeys().equals(java.util.Arrays.stream(RocketPropulsion.Role.values()).map(Enum::name)
                .collect(java.util.stream.Collectors.toSet()))) { throw new IllegalArgumentException("Unknown captured Fluid role"); }
        var allowed = new java.util.EnumMap<RocketPropulsion.Role, java.util.List<String>>(RocketPropulsion.Role.class);
        for (var role : RocketPropulsion.Role.values()) {
            if (!fuels.contains(role.name(), Tag.TAG_LIST)) { throw new IllegalArgumentException("Invalid captured Fluid list"); }
            var raw = fuels.get(role.name()); var list = (net.minecraft.nbt.ListTag) raw;
            if (list.size() > 64 || !list.isEmpty() && list.getElementType() != Tag.TAG_STRING) { throw new IllegalArgumentException("Invalid captured Fluid list"); }
            var ids = new java.util.ArrayList<String>(); list.forEach(id -> ids.add(id.getAsString())); allowed.put(role, ids);
        }
        return new RocketPropulsion(RocketPropulsion.Mode.valueOf(root.getString("mode")), root.getLong("primary"),
                root.getLong("oxidizer"), root.getLong("numerator"), root.getLong("denominator"),
                new RocketPropulsion.Settings(root.getLong("thrust_units"), root.getLong("capacity_units"),
                        root.getLong("core_units"), root.getBoolean("gravity"), root.getBoolean("required"), root.getBoolean("hand"), allowed));
    }
}
