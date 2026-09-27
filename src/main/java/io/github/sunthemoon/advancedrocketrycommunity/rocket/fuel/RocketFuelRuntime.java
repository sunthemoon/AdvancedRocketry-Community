package io.github.sunthemoon.advancedrocketrycommunity.rocket.fuel;

import io.github.sunthemoon.advancedrocketrycommunity.compat.rocket.fuel.RocketFuelCatalog;
import java.util.Objects;
import net.minecraft.world.item.Item;

/** Lifecycle bridge for immutable common-setup metadata only; no world/session state. */
public final class RocketFuelRuntime {
    private static volatile RocketFuelCatalog catalog;

    private RocketFuelRuntime() { }

    public static void install(RocketFuelCatalog value) { catalog = Objects.requireNonNull(value, "catalog"); }

    public static RocketFuelCatalog.Entry find(Item item) {
        RocketFuelCatalog current = catalog;
        return current == null ? null : current.find(item);
    }
}
