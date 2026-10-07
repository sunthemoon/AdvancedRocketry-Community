package io.github.sunthemoon.advancedrocketrycommunity.machine.classic.adapter;

import io.github.sunthemoon.advancedrocketrycommunity.machine.multiblock.pattern.*;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.ChunkStatus;

/** Non-loading FULL chunk/map view. No tickets or deferred BE materialization. */
final class ClassicLoadedWorld implements PatternWorldView {
    private final ServerLevel level;

    ClassicLoadedWorld(ServerLevel level) { this.level = Objects.requireNonNull(level, "level"); }

    boolean usable(BlockPos position) {
        return level.getServer().isSameThread() && !level.isOutsideBuildHeight(position)
                && position.getX() >= -30_000_000 && position.getX() < 30_000_000
                && position.getZ() >= -30_000_000 && position.getZ() < 30_000_000;
    }

    Optional<LevelChunk> fullChunk(BlockPos position) {
        if (!usable(position)) { return Optional.empty(); }
        LevelChunk chunk = level.getChunkSource().getChunkNow(position.getX() >> 4, position.getZ() >> 4);
        return chunk != null && chunk.getStatus() == ChunkStatus.FULL ? Optional.of(chunk) : Optional.empty();
    }

    Optional<ClassicHatchBlockEntity> hatch(BlockPos position) {
        return installed(position).filter(ClassicHatchBlockEntity.class::isInstance).map(ClassicHatchBlockEntity.class::cast);
    }

    Optional<ClassicControllerBlockEntity> controller(BlockPos position) {
        return installed(position).filter(ClassicControllerBlockEntity.class::isInstance).map(ClassicControllerBlockEntity.class::cast);
    }

    /** Identity only, before observer admission; used to distinguish installed from initial load. */
    boolean containsOwner(BlockEntity owner) {
        if (owner.getLevel() != level || !usable(owner.getBlockPos())) { return false; }
        LevelChunk chunk = level.getChunkSource().getChunkNow(owner.getBlockPos().getX() >> 4, owner.getBlockPos().getZ() >> 4);
        return chunk != null && chunk.getBlockEntities().get(owner.getBlockPos()) == owner;
    }

    private Optional<BlockEntity> installed(BlockPos position) {
        Optional<LevelChunk> found = fullChunk(position);
        if (found.isEmpty()) { return Optional.empty(); }
        LevelChunk chunk = found.orElseThrow(); BlockEntity owner = chunk.getBlockEntities().get(position);
        if (owner == null || owner.getLevel() != level || owner.isRemoved() || !owner.getBlockPos().equals(position)
                || !ClassicSaveProtection.ownerObservationMatches(level, chunk, owner)) { return Optional.empty(); }
        // Capability lookup can invoke providers; do not trust the identity checked before it.
        if (!usable(position) || fullChunk(position).orElse(null) != chunk
                || chunk.getBlockEntities().get(position) != owner || owner.getLevel() != level || owner.isRemoved()) {
            return Optional.empty();
        }
        return Optional.of(owner);
    }

    @Override
    public PatternObservation observe(PatternPosition position) {
        BlockPos actual = new BlockPos(position.x(), position.y(), position.z());
        Optional<LevelChunk> found = fullChunk(actual);
        if (found.isEmpty()) { return PatternObservation.unloaded(); }
        LevelChunk chunk = found.orElseThrow(); var state = chunk.getBlockState(actual);
        var tags = state.getTags().map(tag -> tag.location().toString()).limit(PatternBlock.MAX_TAGS + 1L)
                .collect(Collectors.toUnmodifiableSet());
        ClassicValueChecks.require(tags.size() <= PatternBlock.MAX_TAGS, "Block tag bound");
        var id = BuiltInRegistries.BLOCK.getKey(state.getBlock());
        Optional<ClassicHatchKind> kind = ClassicHatchKind.fromBlockId(id);
        Optional<String> role = kind.map(ClassicHatchKind::patternRole);
        boolean controller = chunk.getBlockEntities().get(actual) instanceof ClassicControllerBlockEntity owner
                && owner.machineKind().equals(id);
        if (fullChunk(actual).orElse(null) != chunk || chunk.getBlockState(actual) != state) {
            return PatternObservation.unloaded();
        }
        return PatternObservation.loaded(new PatternBlock(id.toString(), tags, state.isAir(), controller, role));
    }
}
