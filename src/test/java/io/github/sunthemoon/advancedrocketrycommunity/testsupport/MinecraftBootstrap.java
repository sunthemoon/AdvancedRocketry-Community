package io.github.sunthemoon.advancedrocketrycommunity.testsupport;

import java.lang.reflect.Field;
import net.minecraft.server.Bootstrap;

public final class MinecraftBootstrap {
    private static boolean initialized;

    private MinecraftBootstrap() {
    }

    public static synchronized void initialize() {
        if (initialized) {
            return;
        }

        try {
            // Plain JUnit does not run through ModLauncher, so Forge's patched full bootstrap
            // cannot initialize its transformed event classes. ResourceKey constants only need
            // Minecraft's bootstrap guard enabled; GameTest covers the real Forge bootstrap.
            Field field = Bootstrap.class.getDeclaredField("isBootstrapped");
            field.setAccessible(true);
            field.setBoolean(null, true);
            // Initialize this class before Registries to preserve Minecraft's normal bootstrap
            // order and avoid a circular lookup of partially initialized registry keys.
            Class.forName("net.minecraft.core.registries.BuiltInRegistries");
            initialized = true;
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException("Could not prepare Minecraft ResourceKey constants", exception);
        }
    }
}
