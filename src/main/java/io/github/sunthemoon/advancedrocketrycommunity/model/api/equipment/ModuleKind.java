package io.github.sunthemoon.advancedrocketrycommunity.model.api.equipment;

import java.util.List;

/** Bounded shared C17/C18 identity table. Beacon item registration belongs to C17. */
public enum ModuleKind {
    PRESSURE_TANK(SuitPart.CHEST, "low_pressure_tank", "pressure_tank", "high_pressure_tank", "super_high_pressure_tank"),
    JETPACK(SuitPart.CHEST, "jetpack"),
    HOVER(SuitPart.HEAD, "hover_upgrade"),
    FLIGHT_SPEED(SuitPart.LEGS, "flight_speed_upgrade"),
    BIONIC(SuitPart.LEGS, "bionic_leg_upgrade"),
    PADDED(SuitPart.FEET, "padded_landing_boots"),
    ANTI_FOG(SuitPart.HEAD, "anti_fog_visor"),
    EARTHBRIGHT(SuitPart.HEAD, "earthbright_visor"),
    BEACON_FINDER(SuitPart.HEAD, "beacon_finder");

    private static final String NS = "advancedrocketrycommunity:";
    private final SuitPart part;
    private final List<String> items;

    ModuleKind(SuitPart part, String... items) { this.part = part; this.items = List.of(items); }
    public SuitPart part() { return part; }
    public List<String> itemPaths() { return items; }
    public String stableId() { return items.get(0); }
    public boolean fits(SuitPart armor, int position) {
        return armor == part && position >= 0 && position < armor.positions()
                && (armor != SuitPart.CHEST || (this == JETPACK ? position == 0 : position > 0));
    }
    public static ModuleKind forItem(String id) {
        for (ModuleKind kind : values()) {
            for (String item : kind.items) { if ((NS + item).equals(id)) { return kind; } }
        }
        return null;
    }
    public static ModuleKind forStableId(String id) {
        for (ModuleKind kind : values()) { if (kind.stableId().equals(id)) { return kind; } }
        return null;
    }
}
