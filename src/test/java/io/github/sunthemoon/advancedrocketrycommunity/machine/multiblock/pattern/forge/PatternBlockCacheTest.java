package io.github.sunthemoon.advancedrocketrycommunity.machine.multiblock.pattern.forge;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;

import io.github.sunthemoon.advancedrocketrycommunity.machine.multiblock.pattern.PatternBlock;
import io.github.sunthemoon.advancedrocketrycommunity.testsupport.MinecraftBootstrap;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import net.minecraft.core.RegistryAccess;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.event.TagsUpdatedEvent;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/** C13: the validated pattern-block cache returns what a new block would be, per state and role, and is bounded. */
final class PatternBlockCacheTest {
    @BeforeAll
    static void bootstrap() {
        MinecraftBootstrap.initialize();
    }

    @Test
    void oneBlockPerStateAndRoleUntilCleared() {
        PatternBlockCache cache = new PatternBlockCache();
        AtomicInteger built = new AtomicInteger();
        PatternBlock stone = cache.get(Blocks.STONE.defaultBlockState(), false, Optional.empty(), () -> {
            built.incrementAndGet();
            return new PatternBlock("minecraft:stone", Set.of(), false, false, Optional.empty());
        });
        assertSame(stone, cache.get(Blocks.STONE.defaultBlockState(), false, Optional.empty(), () -> {
            throw new AssertionError("a cached block was built again");
        }));
        PatternBlock controller = cache.get(Blocks.STONE.defaultBlockState(), true, Optional.empty(),
                () -> new PatternBlock("minecraft:stone", Set.of(), false, true, Optional.empty()));
        assertNotSame(stone, controller, "the role is part of the key");
        assertEquals(2, cache.size());
        cache.clear();
        cache.get(Blocks.STONE.defaultBlockState(), false, Optional.empty(), () -> {
            built.incrementAndGet();
            return new PatternBlock("minecraft:stone", Set.of(), false, false, Optional.empty());
        });
        assertEquals(2, built.get(), "a cleared cache builds again");
    }

    /** Review C13-F6: only the server's tag reload clears it; the client-side post may run on another thread. */
    @Test
    void onlyTheServerTagReloadClearsIt() {
        PatternBlockCache cache = new PatternBlockCache();
        cache.get(Blocks.STONE.defaultBlockState(), false, Optional.empty(),
                () -> new PatternBlock("minecraft:stone", Set.of(), false, false, Optional.empty()));
        cache.onTagsUpdated(new TagsUpdatedEvent(RegistryAccess.EMPTY, true, true));
        assertEquals(1, cache.size(), "an integrated client's tag packet cleared the server-thread cache");
        cache.onTagsUpdated(new TagsUpdatedEvent(RegistryAccess.EMPTY, true, false));
        assertEquals(1, cache.size(), "a remote client's tag packet cleared it");
        cache.onTagsUpdated(new TagsUpdatedEvent(RegistryAccess.EMPTY, false, false));
        assertEquals(0, cache.size(), "the server's tag reload left stale tag sets");
    }
}
