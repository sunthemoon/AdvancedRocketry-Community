package io.github.sunthemoon.advancedrocketrycommunity.machine.classic.runtime;

/** Stable display-only reason IDs. Unknown values are always repair-required. */
public enum ClassicWait {
    UNFORMED, LOOKUP, INPUT, OUTPUT, ENERGY, REDSTONE, UNLOADED, CONFLICT,
    RECIPE_MISSING, RECIPE_CHANGED, RECIPE_TAGS_INVALID, REPAIR, LIMIT, RUNNING;
    public String key() { return "screen.advancedrocketrycommunity.classic." + name().toLowerCase(java.util.Locale.ROOT); }
    public static ClassicWait fromCode(int code) { return code >= 0 && code < values().length ? values()[code] : REPAIR; }
}
