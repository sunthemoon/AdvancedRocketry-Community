package io.github.sunthemoon.advancedrocketrycommunity.machine.precision;

import java.util.Objects;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;

/** Narrow lifecycle bridge; the installed manager owns every mutable world index. */
public final class PrecisionAssemblerRuntime {
    private static volatile PrecisionAssemblerManager manager;

    private PrecisionAssemblerRuntime() {
    }

    public static void install(PrecisionAssemblerManager installedManager) {
        manager = Objects.requireNonNull(installedManager, "installedManager");
    }

    public static void observeController(ServerLevel level, PrecisionAssemblerBlockEntity controller) {
        PrecisionAssemblerManager current = manager;
        if (current != null) {
            current.observeController(level, controller);
        }
    }

    public static void removeController(ServerLevel level, PrecisionAssemblerBlockEntity controller) {
        PrecisionAssemblerManager current = manager;
        if (current != null) {
            current.removeController(level, controller);
        }
    }

    public static void markDirty(ServerLevel level, BlockPos position) {
        PrecisionAssemblerManager current = manager;
        if (current != null) {
            current.markDirty(level, position);
        }
    }

    public static void markProcessReady(ServerLevel level, BlockPos controllerPosition) {
        PrecisionAssemblerManager current = manager;
        if (current != null) {
            current.markProcessReady(level, controllerPosition);
        }
    }

    public static void clear() {
        manager = null;
    }
}
