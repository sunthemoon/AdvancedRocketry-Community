package io.github.sunthemoon.advancedrocketrycommunity.config;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraftforge.common.ForgeConfigSpec;

/**
 * In-memory values for the COMMON server switches (the worldgen switches and the small plate press), for GameTests
 * only (C15b review R1-M4). Setting a {@link ForgeConfigSpec.BooleanValue} writes the config file, which Forge
 * reloads on its own watcher thread, so a test's write and a later reload can race and put back an older value. An
 * override is never saved and wins over the file until it is cleared.
 */
public final class SwitchOverrides {
    private static final Map<ForgeConfigSpec.BooleanValue, Boolean> VALUES = new ConcurrentHashMap<>();

    private SwitchOverrides() {
    }

    /** Holds a server switch at {@code enabled} until {@link #clear}. */
    public static void set(ForgeConfigSpec.BooleanValue value, boolean enabled) {
        if (!CommonConfig.serverSwitch(value)) {
            throw new IllegalArgumentException("Not a server switch: " + String.join(".", value.getPath()));
        }
        VALUES.put(value, enabled);
    }

    /** Lets the config file decide the switch again. */
    public static void clear(ForgeConfigSpec.BooleanValue value) {
        VALUES.remove(value);
    }

    static Boolean get(ForgeConfigSpec.BooleanValue value) {
        return VALUES.get(value);
    }
}
