package io.github.sunthemoon.advancedrocketrycommunity.machine.multiblock.pattern.forge;

import io.github.sunthemoon.advancedrocketrycommunity.machine.multiblock.pattern.PatternBlock;
import io.github.sunthemoon.advancedrocketrycommunity.machine.multiblock.pattern.PatternObservation;
import io.github.sunthemoon.advancedrocketrycommunity.machine.multiblock.pattern.PatternPosition;
import io.github.sunthemoon.advancedrocketrycommunity.machine.multiblock.pattern.PatternWorldView;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;

/** Forge world adapter that checks chunk availability before every block-state read. */
public final class ServerLevelPatternWorldView implements PatternWorldView {
    private final ServerLevel level;
    private final PatternRoleResolver roles;
    @Nullable
    private final PatternBlockCache cache;

    public ServerLevelPatternWorldView(ServerLevel level, PatternRoleResolver roles) {
        this(level, roles, null);
    }

    /** As above, reusing the validated pattern blocks of {@code cache} (owned and cleared by the caller's service). */
    public ServerLevelPatternWorldView(ServerLevel level, PatternRoleResolver roles, @Nullable PatternBlockCache cache) {
        this.level = Objects.requireNonNull(level, "level");
        this.roles = Objects.requireNonNull(roles, "roles");
        this.cache = cache;
    }

    @Override
    public PatternObservation observe(PatternPosition worldPosition) {
        BlockPos position = new BlockPos(worldPosition.x(), worldPosition.y(), worldPosition.z());
        if (!level.hasChunkAt(position)) {
            return PatternObservation.unloaded();
        }
        BlockState state = level.getBlockState(position);
        boolean controller = roles.isController(position, state);
        Optional<String> port = roles.portChannel(position, state);
        return PatternObservation.loaded(cache == null ? block(state, controller, port)
                : cache.get(state, controller, port, () -> block(state, controller, port)));
    }

    private static PatternBlock block(BlockState state, boolean controller, Optional<String> port) {
        Set<String> tags = state.getTags()
                .map(tag -> tag.location().toString())
                .limit(PatternBlock.MAX_TAGS + 1L)
                .collect(Collectors.toUnmodifiableSet());
        if (tags.size() > PatternBlock.MAX_TAGS) {
            throw new IllegalStateException("loaded block exceeds the bounded tag observation limit");
        }
        return new PatternBlock(
                BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString(),
                tags,
                state.isAir(),
                controller,
                port
        );
    }
}
