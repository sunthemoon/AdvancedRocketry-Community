package io.github.sunthemoon.advancedrocketrycommunity.rocket.model;

import io.github.sunthemoon.advancedrocketrycommunity.rocket.RocketLimits;
import java.util.Objects;
import java.util.List;
import java.util.Map;
import net.minecraft.resources.ResourceLocation;

/** Captured server-only propulsion descriptor; no live config or provider callbacks. */
public record RocketPropulsion(Mode mode, long primaryCapacity, long oxidizerCapacity,
        long coefficientNumerator, long coefficientDenominator, Settings settings) {
    public static final int SCHEMA = 1;
    private static final long MAX_COEFFICIENT_TERM =
            (long) RocketLimits.MAX_BLOCKS * 5_000 * 4 * 5;
    public enum Mode { ABSTRACT, MONO, BIPROPELLANT, NUCLEAR }
    public enum Role { MONO, BIPROPELLANT_FUEL, OXIDIZER, WORKING_FLUID }

    public record Settings(long thrustUnits, long capacityUnits, long coreUnits,
            boolean gravityAffectsFuels, boolean rocketsRequireFuel, boolean canBeFueledByHand,
            Map<Role, List<String>> allowedFluids) {
        public Settings(long thrustUnits, long capacityUnits, long coreUnits, boolean gravity, boolean required, boolean hand) {
            this(thrustUnits, capacityUnits, coreUnits, gravity, required, hand, defaultFluids());
        }
        private static Map<Role, List<String>> defaultFluids() {
            String prefix = "advancedrocketrycommunity:";
            return Map.of(Role.MONO, List.of(prefix + "rocket_fuel"), Role.BIPROPELLANT_FUEL, List.of(prefix + "hydrogen"),
                    Role.OXIDIZER, List.of(prefix + "oxygen"), Role.WORKING_FLUID, List.of(prefix + "hydrogen"));
        }
        public static Settings capture(double thrust, double capacity, double core,
                boolean gravity, boolean required, boolean hand) {
            return new Settings(RocketPropulsionNumbers.captureMultiplierUnits(thrust),
                    RocketPropulsionNumbers.captureMultiplierUnits(capacity),
                    RocketPropulsionNumbers.captureMultiplierUnits(core), gravity, required, hand);
        }
        public static final Settings DEFAULTS = capture(1, 1, 1, true, true, true);
        public Settings {
            RocketPropulsionNumbers.scaleHostRounded(0, thrustUnits);
            RocketPropulsionNumbers.scaleHostRounded(0, capacityUnits);
            RocketPropulsionNumbers.scaleHostRounded(0, coreUnits);
            if (allowedFluids == null || !allowedFluids.keySet().equals(java.util.Set.of(Role.values()))
                    || allowedFluids.values().stream().mapToInt(List::size).sum() > 64) {
                throw new IllegalArgumentException("Invalid captured Fluid allowlists");
            }
            var copy = new java.util.EnumMap<Role, List<String>>(Role.class);
            allowedFluids.forEach((role, ids) -> {
                if (ids.size() != new java.util.HashSet<>(ids).size() || ids.stream().anyMatch(id -> !validFluidId(id))) {
                    throw new IllegalArgumentException("Invalid captured Fluid ID");
                }
                copy.put(role, ids.stream().sorted().toList());
            });
            allowedFluids = Map.copyOf(copy);
        }
        public Settings withAllowedFluids(Map<Role, List<String>> allowed) {
            return new Settings(thrustUnits, capacityUnits, coreUnits, gravityAffectsFuels, rocketsRequireFuel, canBeFueledByHand, allowed);
        }
        public boolean allows(Role role, ResourceLocation id) { return id != null && allowedFluids.get(role).contains(id.toString()); }
        public static boolean validFluidId(Object value) {
            if (!(value instanceof String text) || text.length() > 128 || !text.contains(":")) { return false; }
            var id = ResourceLocation.tryParse(text); return id != null && id.toString().equals(text);
        }
    }

    public RocketPropulsion {
        Objects.requireNonNull(mode, "mode");
        Objects.requireNonNull(settings, "settings");
        if (primaryCapacity < 0 || oxidizerCapacity < 0
                || Math.addExact(primaryCapacity, oxidizerCapacity) > (long) RocketLimits.MAX_BLOCKS * 1_000
                || mode != Mode.BIPROPELLANT && oxidizerCapacity != 0
                || coefficientNumerator <= 0 || coefficientDenominator <= 0
                || coefficientNumerator > MAX_COEFFICIENT_TERM || coefficientDenominator > MAX_COEFFICIENT_TERM
                || coefficientNumerator > coefficientDenominator) {
            throw new IllegalArgumentException("Invalid propulsion descriptor");
        }
    }

    public Role primaryRole() {
        return switch (mode) {
            case ABSTRACT, MONO -> Role.MONO;
            case BIPROPELLANT -> Role.BIPROPELLANT_FUEL;
            case NUCLEAR -> Role.WORKING_FLUID;
        };
    }

    public boolean acceptsAbstract() { return mode == Mode.ABSTRACT || mode == Mode.MONO; }
    public String signature() {
        return RocketNativeResources.hash(mode.name() + ":" + primaryCapacity + ":" + oxidizerCapacity + ":"
                + coefficientNumerator + ":" + coefficientDenominator + ":" + settings.thrustUnits() + ":"
                + settings.capacityUnits() + ":" + settings.coreUnits() + ":" + settings.gravityAffectsFuels()
                + ":" + settings.rocketsRequireFuel() + ":" + settings.canBeFueledByHand() + ":"
                + java.util.Arrays.stream(Role.values()).map(role -> role.name() + "=" + settings.allowedFluids().get(role))
                        .collect(java.util.stream.Collectors.joining(";")));
    }
}
