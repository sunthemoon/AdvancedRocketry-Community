package io.github.sunthemoon.advancedrocketrycommunity.gametest;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.ChunkPos;

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
}
