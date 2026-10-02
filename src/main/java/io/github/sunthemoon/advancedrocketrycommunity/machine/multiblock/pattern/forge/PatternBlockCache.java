package io.github.sunthemoon.advancedrocketrycommunity.machine.multiblock.pattern.forge;

import io.github.sunthemoon.advancedrocketrycommunity.machine.multiblock.pattern.PatternBlock;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.event.TagsUpdatedEvent;

/**
 * Validated {@link PatternBlock}s by block state and role, for a validator that re-reads the same blocks often (C13:
 * every endgame structure revalidates every 200 ticks, and building a pattern block validates its ID and every tag).
 * A pattern block is immutable and depends only on the state's ID and tags and on the role, so a cached one is
 * exactly what a new one would be until the tags change. Its owner is a lifecycle-bound service that clears it when
 * tags reload and when the server stops; it holds at most 4,096 entries and starts over beyond that.
 */
public final class PatternBlockCache {
    private static final int MAX_ENTRIES = 4096;

    private final Map<Key, PatternBlock> blocks = new HashMap<>();

    private record Key(BlockState state, boolean controller, Optional<String> port) {
    }

    PatternBlock get(BlockState state, boolean controller, Optional<String> port, Supplier<PatternBlock> build) {
        Key key = new Key(state, controller, port);
        PatternBlock cached = blocks.get(key);
        if (cached != null) {
            return cached;
        }
        PatternBlock built = build.get();
        if (blocks.size() >= MAX_ENTRIES) {
            blocks.clear();
        }
        blocks.put(key, built);
        return built;
    }

    public void clear() {
        blocks.clear();
    }

    /**
     * Clears on the server's own tag reload only. A single-player or LAN host also posts this event on the client
     * thread when its client receives the tags, and the cache belongs to the server thread (review C13-F6).
     */
    public void onTagsUpdated(TagsUpdatedEvent event) {
        if (event.getUpdateCause() == TagsUpdatedEvent.UpdateCause.SERVER_DATA_LOAD) {
            blocks.clear();
        }
    }

    public int size() {
        return blocks.size();
    }
}
