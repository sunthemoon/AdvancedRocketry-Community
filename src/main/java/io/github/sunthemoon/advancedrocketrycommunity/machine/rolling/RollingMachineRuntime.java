package io.github.sunthemoon.advancedrocketrycommunity.machine.rolling;

import java.util.Objects;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;

/** Narrow lifecycle bridge; mutable indexes remain owned by the installed manager. */
public final class RollingMachineRuntime {
    private static volatile RollingMachineManager manager;

    private RollingMachineRuntime() {
    }

    public static void install(RollingMachineManager installedManager) {
        manager = Objects.requireNonNull(installedManager, "installedManager");
    }

    public static void observeController(ServerLevel level, RollingMachineBlockEntity controller) {
        RollingMachineManager current = manager;
        if (current != null) {
            current.observeController(level, controller);
        }
    }

    public static void removeController(ServerLevel level, RollingMachineBlockEntity controller) {
        RollingMachineManager current = manager;
        if (current != null) {
            current.removeController(level, controller);
        }
    }

    public static void markDirty(ServerLevel level, BlockPos position) {
        RollingMachineManager current = manager;
        if (current != null) {
            current.markDirty(level, position);
        }
    }

    public static void clear() {
        manager = null;
    }
}
