package io.github.sunthemoon.advancedrocketrycommunity.celestial.service;

import java.util.Objects;
import java.util.Optional;

/**
 * W312F2 (R378): the installed gravity controller, so that the rocket logout save can ask which multiplier a player's
 * landing would be scaled by. Installed once by the mod constructor; the controller holds no world state.
 */
public final class CelestialGravityRuntime {
    private static volatile CelestialGravityController controller;

    private CelestialGravityRuntime() {
    }

    public static void install(CelestialGravityController installed) {
        controller = Objects.requireNonNull(installed, "installed");
    }

    /** The installed controller; empty only before the mod constructor has installed it. */
    public static Optional<CelestialGravityController> controller() {
        return Optional.ofNullable(controller);
    }
}
