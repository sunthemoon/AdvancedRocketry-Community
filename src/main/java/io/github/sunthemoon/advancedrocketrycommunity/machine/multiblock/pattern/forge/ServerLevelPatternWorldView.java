package io.github.sunthemoon.advancedrocketrycommunity.machine.multiblock.pattern.forge;

import io.github.sunthemoon.advancedrocketrycommunity.machine.multiblock.pattern.PatternBlock;
import io.github.sunthemoon.advancedrocketrycommunity.machine.multiblock.pattern.PatternObservation;
import io.github.sunthemoon.advancedrocketrycommunity.machine.multiblock.pattern.PatternPosition;
import io.github.sunthemoon.advancedrocketrycommunity.machine.multiblock.pattern.PatternWorldView;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;

/** Forge world adapter that checks chunk availability before every block-state read. */
public final class ServerLevelPatternWorldView implements PatternWorldView {
    private final ServerLevel level;
    private final PatternRoleResolver roles;

    public ServerLevelPatternWorldView(ServerLevel level, PatternRoleResolver roles) {
        this.level = Objects.requireNonNull(level, "level");
        this.roles = Objects.requireNonNull(roles, "roles");
    }

    @Override
    public PatternObservation observe(PatternPosition worldPosition) {
        BlockPos position = new BlockPos(worldPosition.x(), worldPosition.y(), worldPosition.z());
        if (!level.hasChunkAt(position)) {
            return PatternObservation.unloaded();
        }
        BlockState state = level.getBlockState(position);
        Set<String> tags = state.getTags()
                .map(tag -> tag.location().toString())
                .limit(PatternBlock.MAX_TAGS + 1L)
                .collect(Collectors.toUnmodifiableSet());
        if (tags.size() > PatternBlock.MAX_TAGS) {
            throw new IllegalStateException("loaded block exceeds the bounded tag observation limit");
        }
        Optional<String> port = roles.portChannel(position, state);
        return PatternObservation.loaded(new PatternBlock(
                BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString(),
                tags,
                state.isAir(),
                roles.isController(position, state),
                port
        ));
    }
}
