package io.github.sunthemoon.advancedrocketrycommunity.config;

import io.github.sunthemoon.advancedrocketrycommunity.rocket.model.RocketPropulsion;
import java.util.List;
import net.minecraftforge.common.ForgeConfigSpec;

/** ROCKET COMMON settings, read on the server and captured by new assembly. */
public final class RocketPropulsionConfig {
    public final ForgeConfigSpec.DoubleValue thrustMultiplier, fuelCapacityMultiplier, nuclearCoreThrustRatio;
    public final ForgeConfigSpec.BooleanValue gravityAffectsFuels, rocketsRequireFuel, canBeFueledByHand;
    public final ForgeConfigSpec.ConfigValue<List<? extends String>> rocketFuels, bipropellantFuels, oxidizers, nuclearWorkingFluids;
    RocketPropulsionConfig(ForgeConfigSpec.Builder builder) {
        builder.push("ROCKET");
        thrustMultiplier = builder.defineInRange("thrustMultiplier", 1.0, 0.1, 4.0);
        fuelCapacityMultiplier = builder.defineInRange("fuelCapacityMultiplier", 1.0, 0.1, 4.0);
        nuclearCoreThrustRatio = builder.defineInRange("nuclearCoreThrustRatio", 1.0, 0.1, 4.0);
        gravityAffectsFuels = builder.define("gravityAffectsFuels", true);
        rocketsRequireFuel = builder.define("rocketsRequireFuel", true);
        canBeFueledByHand = builder.define("canBeFueledByHand", true);
        rocketFuels = list(builder, "rocketFuels", "advancedrocketrycommunity:rocket_fuel");
        bipropellantFuels = list(builder, "rocketBipropellants", "advancedrocketrycommunity:hydrogen");
        oxidizers = list(builder, "rocketOxidizers", "advancedrocketrycommunity:oxygen");
        nuclearWorkingFluids = list(builder, "rocketNuclearWorkingFluids", "advancedrocketrycommunity:hydrogen");
        builder.pop();
    }
    private static ForgeConfigSpec.ConfigValue<List<? extends String>> list(ForgeConfigSpec.Builder builder,
            String key, String defaultId) {
        return builder.comment("Canonical source Fluid IDs allowed for this role; efficiencies come from rocket_fluid_fuels.",
                "At most 64 entries across all four lists. Reload publishes only a complete valid catalog.")
                .defineListAllowEmpty(key, List.of(defaultId), RocketPropulsionConfig::validId);
    }
    public static boolean validId(Object value) {
        return RocketPropulsion.Settings.validFluidId(value);
    }
    public RocketPropulsion.Settings capture() {
        boolean loaded = CommonConfig.SPEC.isLoaded();
        var settings = RocketPropulsion.Settings.capture(loaded ? thrustMultiplier.get() : thrustMultiplier.getDefault(),
                loaded ? fuelCapacityMultiplier.get() : fuelCapacityMultiplier.getDefault(),
                loaded ? nuclearCoreThrustRatio.get() : nuclearCoreThrustRatio.getDefault(),
                loaded ? gravityAffectsFuels.get() : gravityAffectsFuels.getDefault(),
                loaded ? rocketsRequireFuel.get() : rocketsRequireFuel.getDefault(),
                loaded ? canBeFueledByHand.get() : canBeFueledByHand.getDefault());
        var allowed = new java.util.EnumMap<RocketPropulsion.Role, List<String>>(RocketPropulsion.Role.class);
        for (var role : RocketPropulsion.Role.values()) { allowed.put(role, List.copyOf(allowed(role))); }
        return settings.withAllowedFluids(allowed);
    }
    public List<? extends String> allowed(RocketPropulsion.Role role) {
        var list = switch (role) {
            case MONO -> rocketFuels; case BIPROPELLANT_FUEL -> bipropellantFuels;
            case OXIDIZER -> oxidizers; case WORKING_FLUID -> nuclearWorkingFluids;
        };
        return CommonConfig.SPEC.isLoaded() ? list.get() : list.getDefault();
    }
}
