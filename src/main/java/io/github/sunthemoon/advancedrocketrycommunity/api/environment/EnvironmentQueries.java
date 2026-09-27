package io.github.sunthemoon.advancedrocketrycommunity.api.environment;

import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

/** Read-only handle bound to one logical server, delivered by {@link ServerEnvironmentReadyEvent}. */
@FunctionalInterface
public interface EnvironmentQueries {
    /**
     * Reads configured values without accessing or loading chunks, including at unloaded positions.
     * Unknown, unavailable or ambiguous Levels and unresolved Space regions return empty.
     * Successful catalog reloads and station changes are visible on the next call; old snapshots
     * remain unchanged. This does not test room oxygen, equipment, entity gravity or permission.
     *
     * @throws NullPointerException for a null dimension or position
     * @throws IllegalStateException off the owning server thread or after that server starts stopping
     */
    Optional<EnvironmentSnapshot> at(ResourceKey<Level> dimension, BlockPos position);
}
