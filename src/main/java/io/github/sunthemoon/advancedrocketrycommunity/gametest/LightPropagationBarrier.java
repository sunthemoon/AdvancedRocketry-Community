package io.github.sunthemoon.advancedrocketrycommunity.gametest;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Collection;
import java.util.concurrent.CompletableFuture;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ThreadedLevelLightEngine;
import net.minecraft.world.level.ChunkPos;

/** Test-only per-chunk fences, without relighting chunks or changing their light tickets. */
final class LightPropagationBarrier {
    // 1.20.1 exposes neither a queue fence nor TaskType. Select the unique four-argument overload by
    // signature so this development fixture also works with SRG method/class names on a dedicated server.
    private static final Method ADD_TASK = Arrays.stream(ThreadedLevelLightEngine.class.getDeclaredMethods())
            .filter(method -> {
                Class<?>[] types = method.getParameterTypes();
                return types.length == 4 && types[0] == int.class && types[1] == int.class
                        && types[2].isEnum() && types[3] == Runnable.class;
            }).findFirst().orElseThrow();
    private static final Object POST_UPDATE = Arrays.stream(ADD_TASK.getParameterTypes()[2].getEnumConstants())
            .filter(value -> ((Enum<?>) value).name().equals("POST_UPDATE")).findFirst().orElseThrow();

    static {
        ADD_TASK.setAccessible(true);
    }

    private LightPropagationBarrier() { }

    static CompletableFuture<Void> afterChanges(ServerLevel level, Collection<BlockPos> changed) {
        ThreadedLevelLightEngine engine = level.getChunkSource().getLightEngine();
        var fences = changed.stream().map(ChunkPos::new).distinct().map(chunk -> {
            var fence = new CompletableFuture<Void>();
            try {
                // checkBlock and this marker use the same chunk sorter/mailbox. POST_UPDATE runs only
                // after all preceding PRE_UPDATE checks and LevelLightEngine.runLightUpdates publish.
                ADD_TASK.invoke(engine, chunk.x, chunk.z, POST_UPDATE, (Runnable) () -> fence.complete(null));
            } catch (ReflectiveOperationException failure) {
                throw new IllegalStateException("Cannot fence the GameTest light engine", failure);
            }
            return fence;
        }).toArray(CompletableFuture<?>[]::new);
        engine.tryScheduleUpdate();
        return CompletableFuture.allOf(fences);
    }
}
