package io.github.sunthemoon.advancedrocketrycommunity.api.atmosphere;

/** A state's contribution to a bounded atmosphere scan, after host tag/door rules. */
public enum AtmosphereBoundary {
    /** Use the host's fluid, air and collision-shape fallback. */
    DEFAULT,
    /** Stop traversal at this cell. */
    SEALED,
    /** Traverse this cell, regardless of its collision shape. */
    PERMEABLE
}
