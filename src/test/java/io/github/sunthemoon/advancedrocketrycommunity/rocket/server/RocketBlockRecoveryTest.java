package io.github.sunthemoon.advancedrocketrycommunity.rocket.server;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.sunthemoon.advancedrocketrycommunity.rocket.model.RocketBlock;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.model.RocketBlockState;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.model.RocketPosition;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.model.RocketStructureSnapshot;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.stats.RocketStats;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.transaction.RocketRegion;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.transaction.RocketTransactionWorld;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.transaction.RocketWorldBlock;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

final class RocketBlockRecoveryTest {
    private static final UUID ROCKET = new UUID(13, 1);
    private static final RocketStructureSnapshot SNAPSHOT = RocketStructureSnapshot.create(
            new UUID(13, 2), ResourceLocation.tryParse("minecraft:overworld"), new RocketPosition(0, 70, 0),
            List.of(new RocketBlock(new RocketPosition(0, 0, 0),
                    new RocketBlockState(ResourceLocation.tryParse("minecraft:stone"), Map.of()))),
            List.of(), new RocketStats(1, 1, 0, 0, 0, 0, 0, 0), 0);

    @Test
    void refusedEntityRemovalCannotExposeASecondInventory() {
        World world = new World();
        world.refuseRemoval = true;
        assertFalse(RocketTransactionRecoveryService.recoverBlocks(world, SNAPSHOT, List.of(ROCKET)));
        assertTrue(world.entity);
        assertEquals(0, world.placements);
    }

    @Test
    void exceptionBeforeEntityRemovalDoesNotStartRestoration() {
        World world = new World();
        world.throwBeforeRemoval = true;
        assertThrows(IllegalStateException.class,
                () -> RocketTransactionRecoveryService.recoverBlocks(world, SNAPSHOT, List.of(ROCKET)));
        assertTrue(world.entity);
        assertEquals(0, world.placements);
    }

    @Test
    void exceptionAfterEntityRemovalLeavesRestorationToJournalRetry() {
        World world = new World();
        world.throwAfterRemoval = true;
        assertThrows(IllegalStateException.class,
                () -> RocketTransactionRecoveryService.recoverBlocks(world, SNAPSHOT, List.of(ROCKET)));
        assertFalse(world.entity);
        assertEquals(0, world.placements);
        assertTrue(RocketTransactionRecoveryService.recoverBlocks(world, SNAPSHOT, List.of()));
        assertEquals(1, world.placements);
    }

    @Test
    void conflictingTargetPreservesEntityWithoutCallingRemoval() {
        World world = new World();
        world.current = new RocketWorldBlock(
                new RocketBlockState(ResourceLocation.tryParse("minecraft:dirt"), Map.of()), null);
        assertFalse(RocketTransactionRecoveryService.recoverBlocks(world, SNAPSHOT, List.of(ROCKET)));
        assertTrue(world.entity);
        assertEquals(0, world.removals);
        assertEquals(0, world.placements);
    }

    @Test
    void healthyRecoveryRetiresEntityBeforeRestoringInventory() {
        World world = new World();
        assertTrue(RocketTransactionRecoveryService.recoverBlocks(world, SNAPSHOT, List.of(ROCKET)));
        assertFalse(world.entity);
        assertEquals(1, world.placements);
    }

    private static final class World implements RocketTransactionWorld {
        private boolean entity = true;
        private boolean refuseRemoval;
        private boolean throwBeforeRemoval;
        private boolean throwAfterRemoval;
        private RocketWorldBlock current;
        private int removals;
        private int placements;

        @Override
        public ResourceLocation dimension() {
            return SNAPSHOT.sourceDimension();
        }

        @Override
        public boolean isRegionLoaded(RocketRegion region) {
            return true;
        }

        @Override
        public Optional<RocketWorldBlock> readBlock(RocketPosition position) {
            return Optional.ofNullable(current);
        }

        @Override
        public boolean removeBlockNoDrops(RocketPosition position, RocketWorldBlock expected) {
            throw new AssertionError("Block-authoritative recovery must not remove existing blocks");
        }

        @Override
        public boolean placeBlockIfEmpty(RocketPosition position, RocketWorldBlock block) {
            assertFalse(entity, "Restoration exposed block resources while entity authority remained");
            placements++;
            current = block;
            return true;
        }

        @Override
        public Optional<UUID> spawnRocket(RocketStructureSnapshot snapshot, UUID transactionId) {
            throw new AssertionError("Recovery must not spawn a second entity");
        }

        @Override
        public boolean rocketMatches(UUID rocketId, UUID snapshotId, String hash) {
            return entity;
        }

        @Override
        public boolean removeRocket(UUID rocketId, UUID snapshotId) {
            removals++;
            if (throwBeforeRemoval) {
                throw new IllegalStateException("before discard");
            }
            if (refuseRemoval) {
                return false;
            }
            entity = false;
            if (throwAfterRemoval) {
                throw new IllegalStateException("after discard");
            }
            return true;
        }
    }
}
