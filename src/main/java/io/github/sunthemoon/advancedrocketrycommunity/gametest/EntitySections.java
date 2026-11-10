package io.github.sunthemoon.advancedrocketrycommunity.gametest;

import java.util.function.BooleanSupplier;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.entity.PersistentEntitySectionManager;
import net.minecraftforge.fml.util.ObfuscationReflectionHelper;

/**
 * GameTest support (review C12R2-L1): a test chunk's entity section loads asynchronously, later than its blocks, and
 * later still while about 100 tests of one batch start in the same tick. Tests that need entities to be loaded there
 * (rocket authority recovery, the fuel loader's nearby-rocket rule) wait for it instead of asserting it once.
 */
public final class EntitySections {
    private EntitySections() {
    }

    /** Runs {@code body} once the entity sections at the test-relative positions have loaded, then succeeds. */
    static void whenLoaded(GameTestHelper helper, Runnable body, BlockPos... relativePositions) {
        whenLoaded(helper, body, null, relativePositions);
    }

    /** Opt-in wall-clock pacing for short waits; the caller's original test timeout still applies. */
    static void whenLoaded(GameTestHelper helper, Runnable body, GameTestTickPacer pacer,
            BlockPos... relativePositions) {
        helper.startSequence()
                .thenWaitUntil(() -> {
                    for (BlockPos relative : relativePositions) {
                        BlockPos at = helper.absolutePos(relative);
                        boolean loaded = helper.getLevel().areEntitiesLoaded(ChunkPos.asLong(at.getX() >> 4,
                                at.getZ() >> 4));
                        if (!loaded && pacer != null) {
                            pacer.pace();
                        }
                        helper.assertTrue(loaded, "The test chunk's entities have not loaded yet");
                    }
                })
                .thenExecute(body)
                .thenSucceed();
    }

    /**
     * W122: loads a held chunk's entities now, in a test's setup, so that no timed tick waits for them. Vanilla queues a
     * chunk's entity storage load when the chunk becomes accessible (a chunk-source task), reads it on the IO worker,
     * deserializes it on the server's own task queue (the {@code EntityStorage} executor), and marks the chunk loaded only
     * in the Level's next entity-management step ({@code PersistentEntitySectionManager.tick}, the last step of
     * {@code ServerLevel.tick}). A server that is behind runs its own tasks only once they are three ticks old, so on a
     * starved host this took longer than a test's whole tick budget. This runs all three steps in a managed block of the
     * server, which runs its tasks at once, until the chunk's entities are loaded or the wall-clock deadline passes; a
     * GameTest timeout counts ticks, not this time. Server thread only.
     */
    static boolean loadNow(ServerLevel level, ChunkPos chunk, long deadlineNanos) {
        return awaitReady(level, () -> level.areEntitiesLoaded(chunk.toLong()), deadlineNanos);
    }

    /**
     * Setup-only wait for already held chunks, including callers that also need entity ticking. Uses the same server,
     * chunk-source and entity-management steps as {@link #loadNow}; does not add tickets or advance measured test ticks.
     * The caller must check the result and keep its existing timed assertions outside this wait. Server thread only.
     */
    public static boolean awaitReady(ServerLevel level, BooleanSupplier ready, long deadlineNanos) {
        // ServerLevel.entityManager in the 1.20.1 SRG mappings.
        PersistentEntitySectionManager<Entity> entities =
                ObfuscationReflectionHelper.getPrivateValue(ServerLevel.class, level, "f_143244_");
        level.getServer().managedBlock(() -> {
            if (ready.getAsBoolean() || System.nanoTime() - deadlineNanos > 0) {
                return true;
            }
            int ran = 0;
            while (ran < 256 && level.getChunkSource().pollTask()) {
                ran++;
            }
            entities.tick();
            return ready.getAsBoolean();
        });
        return ready.getAsBoolean();
    }
}
