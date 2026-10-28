package io.github.sunthemoon.advancedrocketrycommunity.rocket.server;

import static org.junit.jupiter.api.Assertions.*;

import io.github.sunthemoon.advancedrocketrycommunity.rocket.RocketLimits;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.sealed.SealedLazyWork;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.sealed.SealedRocketBlockEntity;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.sealed.SealedWorkBudget;
import io.github.sunthemoon.advancedrocketrycommunity.testsupport.MinecraftBootstrap;
import io.github.sunthemoon.advancedrocketrycommunity.testsupport.UnstartedServer;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ChunkHolder;
import net.minecraft.server.level.ServerChunkCache;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.chunk.LevelChunk;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class SealedLoadedDiscoveryTest {
    @BeforeAll static void bootstrap() { MinecraftBootstrap.initialize(); }

    @Test void overflowMembersPublishInOneSweepInBothAlternationPhases() {
        for (boolean splitFirstTick : List.of(false, true)) {
            var fixture = new Fixture(70);
            var published = new HashSet<BlockPos>();
            var lazy = fixture.lazy(published);
            for (int index = 0; index < 64; index++) { assertTrue(lazy.hint(fixture.hint(index))); }
            for (int index = 64; index < 70; index++) { assertFalse(lazy.hint(fixture.hint(index))); }
            var budget = new SealedWorkBudget();
            for (int tick = 0; tick < 20 && !fixture.chunks.nextChunkReached; tick++) {
                budget.beginTick(tick);
                if (splitFirstTick && tick == 0) {
                    for (int unit = 0; unit < 255; unit++) { assertTrue(budget.take()); }
                }
                lazy.tick(budget);
                assertEquals(0, budget.remaining());
                assertTrue(lazy.size() <= 64);
            }
            assertTrue(fixture.chunks.nextChunkReached, "The first visit did not finish");
            assertEquals(70, published.size(), "Overflow members waited for another world sweep");
            assertTrue(fixture.chunk.entities.isEmpty());
        }
    }

    @Test void aGrowingContinuouslyChangingMapCannotReplenishTheRevisitAllowance() {
        var fixture = new Fixture(5);
        assertNull(fixture.discovery.advance()); // Enter the first chunk with five entries.
        int advances = 0;
        while (!fixture.chunks.nextChunkReached && advances < 20) {
            fixture.add(100 + advances); // Invalidate every fresh iterator, while increasing map size.
            assertNull(fixture.discovery.advance());
            advances++;
        }
        assertTrue(fixture.chunks.nextChunkReached, "An edited map retained the cursor indefinitely");
        assertEquals(7, advances, "Five restarts, one exhausted advance, then the next chunk");
        assertEquals(12, fixture.chunk.entities.size(), "Discovery must not mutate or drop carriers");
    }

    @Test void aStableNonCarrierPrefixDoesNotHideOverflowMembersDuringRestarts() {
        var fixture = new Fixture(0);
        for (int index = 0; index < 4; index++) {
            var entity = UnstartedServer.allocate(ChestBlockEntity.class);
            fixture.chunk.entities.put(position(index), entity);
        }
        for (int index = 4; index < 12; index++) { fixture.add(index); }
        var published = new HashSet<BlockPos>();
        var lazy = fixture.lazy(published);
        var budget = new SealedWorkBudget(); budget.beginTick(1);
        lazy.tick(budget);
        assertTrue(fixture.chunks.nextChunkReached);
        assertEquals(8, published.size());
        assertEquals(4, fixture.chunk.entities.size());
    }

    @Test void unloadingOrReplacingTheChunkOrLevelAbandonsTheRevisit() {
        for (int change = 0; change < 3; change++) {
            var fixture = new Fixture(5);
            fixture.discovery.advance();
            var found = fixture.discovery.advance(); assertNotNull(found);
            fixture.chunk.entities.remove(found.position());
            fixture.discovery.advance(); // Restart after publication invalidates the iterator.
            assertNotNull(UnstartedServer.get(SealedLoadedDiscovery.class, fixture.discovery, "chunk"),
                    "The publication ended the visit instead of starting a revisit");
            if (change == 0) { fixture.chunks.loaded = null; }
            if (change == 1) { fixture.chunks.loaded = UnstartedServer.allocate(TestChunk.class); }
            if (change == 2) { UnstartedServer.set(MinecraftServer.class, fixture.server, "levels", Map.of()); }
            assertNull(fixture.discovery.advance());
            assertNull(UnstartedServer.get(SealedLoadedDiscovery.class, fixture.discovery, "chunk"));
            assertEquals(4, fixture.chunk.entities.size());
        }
    }

    @Test void theCursorResumesAfterTheSharedTickAllowanceIsExhausted() {
        var fixture = new Fixture(10);
        var published = new HashSet<BlockPos>(); var lazy = fixture.lazy(published);
        var budget = new SealedWorkBudget(); budget.beginTick(1);
        for (int unit = 0; unit < 250; unit++) { assertTrue(budget.take()); }
        lazy.tick(budget);
        assertEquals(0, budget.remaining()); assertFalse(fixture.chunks.nextChunkReached);
        int partial = published.size(); assertTrue(partial > 0 && partial < 10);
        budget.beginTick(1); lazy.tick(budget); assertEquals(partial, published.size());
        budget.beginTick(2); lazy.tick(budget);
        assertEquals(10, published.size()); assertTrue(fixture.chunks.nextChunkReached);
    }

    @Test void aBusyChunkWithoutCarriersKeepsItsCostFromBeforeTheRevisit() {
        // W186-R1 probe: {block entities at entry, ticks between foreign structural edits}, in vanilla's HashMap order.
        int[][] cases = {{100, 1}, {1000, 1}, {1000, 2}, {1000, 3}, {1000, 4}, {5000, 1}, {5000, 19}, {5000, 20}};
        for (int[] busy : cases) {
            var fixture = new Fixture(0); fixture.chunk.entities = new HashMap<>();
            for (int index = 0; index < busy[0]; index++) { fixture.chest(index); }
            // Before W186 the cursor left at the first edit after entry, or after one complete scan: the entry, every
            // entity, the exhausted step and the next chunk's entry, with the whole tick allowance for discovery.
            int perTick = RocketLimits.MAX_SCAN_INSPECTIONS_PER_TICK, scan = (busy[0] + 3 + perTick - 1) / perTick;
            int bound = Math.min(busy[1] + 1, scan);
            var run = fixture.drive(fixture.lazy(new HashSet<>()), busy[1], bound);
            assertTrue(fixture.chunks.nextChunkReached, "A busy chunk of " + busy[0] + " block entities edited every "
                    + busy[1] + " ticks held the cursor beyond " + bound + " ticks");
            assertTrue(run.busiest() <= RocketLimits.MAX_SCAN_INSPECTIONS_PER_TICK);
            assertEquals(0, fixture.attempts);
        }
    }

    @Test void heldCarriersInABusyChunkDoNotRetainTheCursor() {
        // The three carriers come first, so each is found and attempted before the first foreign edit.
        var fixture = new Fixture(3); fixture.holdAttempts = true;
        for (int index = 3; index < 1003; index++) { fixture.chest(index); }
        var published = new HashSet<BlockPos>();
        var run = fixture.drive(fixture.lazy(published), 1, 2);
        assertTrue(fixture.chunks.nextChunkReached, "Carriers that cannot publish let a busy chunk retain the cursor");
        assertEquals(3, fixture.attempts); assertTrue(published.isEmpty());
        assertTrue(run.busiest() <= RocketLimits.MAX_SCAN_INSPECTIONS_PER_TICK);
    }

    @Test void publicationsBehindManyOrdinaryBlockEntitiesFinishInOneVisit() {
        // {ordinary block entities, carriers}, with no foreign edit: the W184 shape in vanilla's HashMap order.
        int[][] cases = {{0, 70}, {500, 50}, {2000, 50}, {5000, 100}};
        for (int[] shape : cases) {
            var fixture = new Fixture(0); fixture.chunk.entities = new HashMap<>();
            for (int index = 0; index < shape[0]; index++) { fixture.chest(index); }
            for (int index = shape[0]; index < shape[0] + shape[1]; index++) { fixture.add(index); }
            var published = new HashSet<BlockPos>();
            var run = fixture.drive(fixture.lazy(published), 0, 100_000);
            assertTrue(fixture.chunks.nextChunkReached);
            assertEquals(shape[1], published.size(), "Members waited for another world sweep");
            assertEquals(shape[0], fixture.chunk.entities.size());
            assertTrue(run.busiest() <= RocketLimits.MAX_SCAN_INSPECTIONS_PER_TICK);
            // At most one restart per publication, each rescanning at most the entry map plus the restart step.
            assertTrue(fixture.advances <= (shape[1] + 1) * (shape[0] + shape[1] + 3), "advances=" + fixture.advances);
        }
    }

    private static BlockPos position(int index) { return new BlockPos(index % 16, index / 16, 0); }

    private record Run(int ticks, int busiest) { }

    private static final class Fixture {
        final MinecraftServer server = UnstartedServer.owned();
        final TestLevel level = UnstartedServer.allocate(TestLevel.class);
        final TestChunks chunks = UnstartedServer.allocate(TestChunks.class);
        final TestChunk chunk = UnstartedServer.allocate(TestChunk.class);
        final SealedLoadedDiscovery discovery = new SealedLoadedDiscovery(server);
        boolean holdAttempts; int advances; int attempts;

        Fixture(int members) {
            level.chunks = chunks; chunks.loaded = chunk; chunk.entities = new LinkedHashMap<>();
            UnstartedServer.set(MinecraftServer.class, server, "levels", Map.of(Level.OVERWORLD, level));
            UnstartedServer.set(SealedLoadedDiscovery.class, discovery, "level", level);
            var first = UnstartedServer.allocate(ChunkHolder.class);
            var next = UnstartedServer.allocate(ChunkHolder.class);
            UnstartedServer.set(ChunkHolder.class, first, "pos", new ChunkPos(0, 0));
            UnstartedServer.set(ChunkHolder.class, next, "pos", new ChunkPos(1, 0));
            UnstartedServer.set(SealedLoadedDiscovery.class, discovery, "chunks", List.of(first, next).iterator());
            for (int index = 0; index < members; index++) { add(index); }
        }

        void add(int index) {
            var carrier = UnstartedServer.allocate(SealedRocketBlockEntity.class);
            UnstartedServer.set(BlockEntity.class, carrier, "worldPosition", position(index));
            chunk.entities.put(position(index), carrier);
        }

        void chest(int index) { chunk.entities.put(position(index), UnstartedServer.allocate(ChestBlockEntity.class)); }

        SealedLoadedDiscovery.Hint hint(int index) { return new SealedLoadedDiscovery.Hint(Level.OVERWORLD, position(index)); }

        SealedLazyWork<SealedLoadedDiscovery.Hint> lazy(Set<BlockPos> published) {
            return new SealedLazyWork<>(new SealedLazyWork.Port<>() {
                public SealedLoadedDiscovery.Hint discover() {
                    // Stop after the next chunk: a later sweep must not conceal an incomplete first visit.
                    if (chunks.nextChunkReached) { return null; }
                    advances++; return discovery.advance();
                }
                public void attempt(SealedLoadedDiscovery.Hint hint, SealedWorkBudget budget) {
                    assertTrue(budget.take(), "Publication uses the caller's remaining budget");
                    attempts++;
                    if (!holdAttempts && chunk.entities.remove(hint.position()) != null) { published.add(hint.position()); }
                }
            });
        }

        /**
         * Runs whole ticks until the next chunk is reached, at most {@code limit}. Before every {@code period}-th tick a
         * foreign block entity (a piston's moving block, a placed chest) appears or disappears; 0 means never.
         */
        Run drive(SealedLazyWork<SealedLoadedDiscovery.Hint> lazy, int period, int limit) {
            var budget = new SealedWorkBudget(); var foreign = position(1_000_000);
            int tick = 0, busiest = 0;
            for (; tick < limit && !chunks.nextChunkReached; tick++) {
                if (period > 0 && tick > 0 && tick % period == 0) {
                    if (chunk.entities.remove(foreign) == null) { chest(1_000_000); }
                }
                int before = advances;
                budget.beginTick(tick); lazy.tick(budget);
                busiest = Math.max(busiest, advances - before);
            }
            return new Run(tick, busiest);
        }
    }

    private static final class TestLevel extends ServerLevel {
        TestChunks chunks;
        TestLevel() { super(null, null, null, null, null, null, null, false, 0, List.of(), false, null); }
        @Override public net.minecraft.resources.ResourceKey<Level> dimension() { return Level.OVERWORLD; }
        @Override public ServerChunkCache getChunkSource() { return chunks; }
    }
    private static final class TestChunks extends ServerChunkCache {
        TestChunk loaded; boolean nextChunkReached;
        TestChunks() { super(null, null, null, null, null, null, 0, 0, false, null, null, null); }
        @Override public LevelChunk getChunkNow(int x, int z) {
            if (x == 1 && z == 0) { nextChunkReached = true; }
            return x == 0 && z == 0 ? loaded : null;
        }
    }
    private static final class TestChunk extends LevelChunk {
        Map<BlockPos, BlockEntity> entities;
        TestChunk() { super(null, new ChunkPos(0, 0)); }
        @Override public ChunkPos getPos() { return new ChunkPos(0, 0); }
        @Override public Map<BlockPos, BlockEntity> getBlockEntities() { return entities; }
    }
}
