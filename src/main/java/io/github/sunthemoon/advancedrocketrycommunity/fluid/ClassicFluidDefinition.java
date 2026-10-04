package io.github.sunthemoon.advancedrocketrycommunity.fluid;

/** Stable fluid IDs and audited physical/color facts; no upstream implementation. */
public enum ClassicFluidDefinition {
    OXYGEN("oxygen", true, 0xFF6CE2FF, -1_000, 1_000, 300, 0),
    HYDROGEN("hydrogen", true, 0xFFDBC1C1, -1_000, 1_000, 300, 0),
    NITROGEN("nitrogen", true, 0xFFDFE5FE, -1_000, 1_000, 300, 0),
    ROCKET_FUEL("rocket_fuel", false, 0xFFE5D884, 800, 1_500, 300, 2),
    ENRICHED_LAVA("enriched_lava", false, 0xFFFFFFFF, 3_000, 6_000, 1_300, 15);

    private final String id;
    private final boolean gas;
    private final int tint;
    private final int density;
    private final int viscosity;
    private final int temperature;
    private final int light;

    ClassicFluidDefinition(String id, boolean gas, int tint, int density,
            int viscosity, int temperature, int light) {
        this.id = id;
        this.gas = gas;
        this.tint = tint;
        this.density = density;
        this.viscosity = viscosity;
        this.temperature = temperature;
        this.light = light;
    }

    public String id() { return id; }
    public boolean gas() { return gas; }
    public int tint() { return tint; }
    public int density() { return density; }
    public int viscosity() { return viscosity; }
    public int temperature() { return temperature; }
    public int light() { return light; }
}
