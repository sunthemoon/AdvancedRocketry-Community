package io.github.sunthemoon.advancedrocketrycommunity.rocket.model;

import io.github.sunthemoon.advancedrocketrycommunity.ModIdentity;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.stats.RocketBlockMetrics;
import net.minecraft.resources.ResourceLocation;

/** Classification follows explicit-definition precedence, not the public component API. */
public enum RocketPropulsionComponent {
    EXTERNAL(null, 0, 0, 0),
    BASIC_MONO("rocket_motor", 100, 1_000, 0),
    ADVANCED_MONO("advanced_rocket_motor", 100, 5_000, 0),
    BIPROPELLANT("bipropellant_rocket_motor", 100, 1_000, 0),
    ADVANCED_BIPROPELLANT("advanced_bipropellant_rocket_motor", 100, 5_000, 0),
    NUCLEAR("nuclear_rocket_motor", 100, 3_500, 0),
    MONO_TANK("rocket_fuel_tank", 50, 0, 1_000),
    BIPROPELLANT_TANK("bipropellant_fuel_tank", 50, 0, 1_000),
    OXIDIZER_TANK("oxidizer_fuel_tank", 50, 0, 1_000),
    NUCLEAR_TANK("nuclear_fuel_tank", 50, 0, 1_000),
    CORE("nuclear_core", 100, 0, 0);

    private final String path;
    private final long mass;
    private final long thrust;
    private final long capacity;
    RocketPropulsionComponent(String path, long mass, long thrust, long capacity) {
        this.path = path; this.mass = mass; this.thrust = thrust; this.capacity = capacity;
    }
    public String path() { return path; }
    public long thrust() { return thrust; }
    public long capacity() { return capacity; }
    public RocketBlockMetrics metrics() {
        return new RocketBlockMetrics(mass, thrust, capacity, thrust > 0, false, false);
    }
    public static RocketPropulsionComponent host(ResourceLocation id) {
        for (var component : values()) {
            if (component.path != null && id.equals(ModIdentity.id(component.path))) { return component; }
        }
        return EXTERNAL;
    }
}
