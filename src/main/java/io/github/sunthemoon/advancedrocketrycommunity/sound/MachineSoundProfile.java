package io.github.sunthemoon.advancedrocketrycommunity.sound;

import javax.annotation.Nullable;

/** Audible processing mappings from the pinned upstream tile bodies, not from machine size. */
public final class MachineSoundProfile {
    private MachineSoundProfile() { }

    @Nullable
    public static ClassicSound loop(String machine, boolean active) {
        if (!active) { return null; }
        return switch (machine) {
            case "lathe", "precision_laser_etcher" -> ClassicSound.LATHE;
            case "rolling_machine", "chemical_reactor" -> ClassicSound.ROLLING_MACHINE;
            default -> null;
        };
    }
}
