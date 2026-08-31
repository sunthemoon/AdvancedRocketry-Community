package io.github.sunthemoon.advancedrocketrycommunity.celestial.legacy;

import java.lang.reflect.Field;
import net.minecraft.server.Bootstrap;

/** Initializes only the Minecraft constants required by the standalone import CLI. */
final class LegacyImportBootstrap {
    private static boolean initialized;

    private LegacyImportBootstrap() {
    }

    static synchronized void initialize() {
        if (initialized) {
            return;
        }
        try {
            // A plain JavaExec does not pass through ModLauncher. The importer only needs
            // ResourceKey constants, so avoid Forge's transformed full server bootstrap.
            Field field = Bootstrap.class.getDeclaredField("isBootstrapped");
            field.setAccessible(true);
            field.setBoolean(null, true);
            Class.forName("net.minecraft.core.registries.BuiltInRegistries");
            initialized = true;
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException("Could not initialize Minecraft constants for legacy import", exception);
        }
    }
}
