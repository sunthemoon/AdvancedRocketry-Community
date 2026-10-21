package io.github.sunthemoon.advancedrocketrycommunity.gametest;

import java.util.concurrent.locks.LockSupport;
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
final class EntitySections {
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
     * chunk's entity storage load when the chunk becomes accessible, completes it on the IO and background threads, and
     * marks the chunk loaded only in the Level's next entity-management step ({@code PersistentEntitySectionManager.tick},
     * the last step of {@code ServerLevel.tick}). On a starved host that took longer than a test's whole tick budget. This
     * runs the chunk source's main-thread tasks and that same step until the chunk's entities are loaded or the
     * wall-clock deadline passes; a GameTest timeout counts ticks, not this time. Server thread only.
     */
    static boolean loadNow(ServerLevel level, ChunkPos chunk, long deadlineNanos) {
        // ServerLevel.entityManager in the 1.20.1 SRG mappings.
        PersistentEntitySectionManager<Entity> entities =
                ObfuscationReflectionHelper.getPrivateValue(ServerLevel.class, level, "f_143244_");
        while (!level.areEntitiesLoaded(chunk.toLong())) {
            if (System.nanoTime() - deadlineNanos > 0) {
                return false;
            }
            int ran = 0;
            while (ran < 256 && level.getChunkSource().pollTask()) {
                ran++;
            }
            entities.tick();
            if (ran == 0) {
                LockSupport.parkNanos(500_000L);
            }
        }
        return true;
    }
}
