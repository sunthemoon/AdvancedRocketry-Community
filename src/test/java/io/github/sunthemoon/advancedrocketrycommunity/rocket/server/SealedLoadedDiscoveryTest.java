package io.github.sunthemoon.advancedrocketrycommunity.rocket.server;

import static org.junit.jupiter.api.Assertions.*;

import io.github.sunthemoon.advancedrocketrycommunity.rocket.sealed.SealedLazyWork;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.sealed.SealedRocketBlockEntity;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.sealed.SealedWorkBudget;
import io.github.sunthemoon.advancedrocketrycommunity.testsupport.MinecraftBootstrap;
import io.github.sunthemoon.advancedrocketrycommunity.testsupport.UnstartedServer;
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

    private static BlockPos position(int index) { return new BlockPos(index % 16, index / 16, 0); }

    private static final class Fixture {
        final MinecraftServer server = UnstartedServer.owned();
        final TestLevel level = UnstartedServer.allocate(TestLevel.class);
        final TestChunks chunks = UnstartedServer.allocate(TestChunks.class);
        final TestChunk chunk = UnstartedServer.allocate(TestChunk.class);
        final SealedLoadedDiscovery discovery = new SealedLoadedDiscovery(server);

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

        SealedLoadedDiscovery.Hint hint(int index) { return new SealedLoadedDiscovery.Hint(Level.OVERWORLD, position(index)); }

        SealedLazyWork<SealedLoadedDiscovery.Hint> lazy(Set<BlockPos> published) {
            return new SealedLazyWork<>(new SealedLazyWork.Port<>() {
                public SealedLoadedDiscovery.Hint discover() {
                    // Stop after the next chunk: a later sweep must not conceal an incomplete first visit.
                    return chunks.nextChunkReached ? null : discovery.advance();
                }
                public void attempt(SealedLoadedDiscovery.Hint hint, SealedWorkBudget budget) {
                    assertTrue(budget.take(), "Publication uses the caller's remaining budget");
                    if (chunk.entities.remove(hint.position()) != null) { published.add(hint.position()); }
                }
            });
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
