package io.github.sunthemoon.advancedrocketrycommunity.rocket.forge;

import io.github.sunthemoon.advancedrocketrycommunity.registry.ModBlockTags;
import io.github.sunthemoon.advancedrocketrycommunity.registry.ModEntities;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.entity.RocketEntity;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.model.RocketPosition;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.model.RocketStructureSnapshot;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.model.RocketBlockEntityPayload;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.transaction.RocketRegion;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.transaction.RocketTransactionWorld;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.transaction.RocketWorldBlock;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

/** Loaded-only ServerLevel adapter. No method creates a ticket or calls getChunk. */
public final class ServerLevelRocketTransactionWorld implements RocketTransactionWorld {
    private static final int UPDATE_FLAGS = Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE;

    private final ServerLevel level;
    private final RocketBlockEntityAdapters adapters;
    private final UUID ownerId;

    public ServerLevelRocketTransactionWorld(
            ServerLevel level,
            RocketBlockEntityAdapters adapters,
            UUID ownerId
    ) {
        this.level = Objects.requireNonNull(level, "level");
        this.adapters = Objects.requireNonNull(adapters, "adapters");
        this.ownerId = Objects.requireNonNull(ownerId, "ownerId");
    }

    @Override
    public ResourceLocation dimension() {
        return level.dimension().location();
    }

    @Override
    public boolean isRegionLoaded(RocketRegion region) {
        if (!region.dimension().equals(dimension())
                || region.minimum().y() < level.getMinBuildHeight()
                || region.maximum().y() >= level.getMaxBuildHeight()) {
            return false;
        }
        int minChunkX = region.minimum().x() >> 4;
        int maxChunkX = region.maximum().x() >> 4;
        int minChunkZ = region.minimum().z() >> 4;
        int maxChunkZ = region.maximum().z() >> 4;
        int sampleY = Math.max(level.getMinBuildHeight(), Math.min(region.minimum().y(), level.getMaxBuildHeight() - 1));
        for (int chunkX = minChunkX; chunkX <= maxChunkX; chunkX++) {
            for (int chunkZ = minChunkZ; chunkZ <= maxChunkZ; chunkZ++) {
                if (!level.hasChunkAt(new BlockPos(chunkX << 4, sampleY, chunkZ << 4))) {
                    return false;
                }
            }
        }
        return true;
    }

    @Override
    public Optional<RocketWorldBlock> readBlock(RocketPosition absolutePosition) {
        BlockPos position = ServerLevelRocketScanWorld.toBlockPos(absolutePosition);
        if (!level.hasChunkAt(position)) {
            throw new IllegalStateException("Rocket position is not loaded");
        }
        BlockState state = level.getBlockState(position);
        if (state.isAir()) {
            return Optional.empty();
        }
        RocketBlockEntityPayload payload = null;
        BlockEntity blockEntity = level.getBlockEntity(position);
        if (blockEntity != null) {
            RocketBlockEntityAdapters.CaptureResult captured = adapters.capture(blockEntity);
            if (captured.supported()) {
                payload = captured.optionalPayload().orElseThrow();
            }
        }
        // Non-movable/unsupported states remain observable as occupied, but can
        // never equal an approved snapshot block with different adapter data.
        return Optional.of(new RocketWorldBlock(RocketBlockStateAdapter.capture(state), payload));
    }

    @Override
    public boolean removeBlockNoDrops(
            RocketPosition absolutePosition,
            RocketWorldBlock expected
    ) {
        BlockPos position = ServerLevelRocketScanWorld.toBlockPos(absolutePosition);
        if (!level.hasChunkAt(position)) {
            return false;
        }
        Optional<RocketWorldBlock> current;
        try {
            current = readBlock(absolutePosition);
        } catch (RuntimeException exception) {
            return false;
        }
        if (current.isEmpty() || !expected.equals(current.orElseThrow())) {
            return false;
        }
        level.removeBlockEntity(position);
        if (!level.setBlock(position, Blocks.AIR.defaultBlockState(), UPDATE_FLAGS)) {
            return false;
        }
        return level.getBlockState(position).isAir() && level.getBlockEntity(position) == null;
    }

    @Override
    public boolean placeBlockIfEmpty(
            RocketPosition absolutePosition,
            RocketWorldBlock block
    ) {
        BlockPos position = ServerLevelRocketScanWorld.toBlockPos(absolutePosition);
        if (!level.hasChunkAt(position)
                || !level.getBlockState(position).isAir()
                || level.getBlockEntity(position) != null) {
            return false;
        }
        Optional<BlockState> restored = RocketBlockStateAdapter.restore(block.state());
        if (restored.isEmpty()) {
            return false;
        }
        BlockState state = restored.orElseThrow();
        if (!state.is(ModBlockTags.ROCKET_MOVABLE)
                || state.is(ModBlockTags.ROCKET_FORBIDDEN)) {
            return false;
        }
        if (!level.setBlock(position, state, UPDATE_FLAGS)) {
            return false;
        }
        BlockEntity blockEntity = level.getBlockEntity(position);
        boolean restoredPayload;
        if (block.payload().isPresent()) {
            restoredPayload = blockEntity != null
                    && adapters.restore(blockEntity, block.payload().orElseThrow());
        } else {
            restoredPayload = blockEntity == null;
        }
        if (!restoredPayload) {
            level.removeBlockEntity(position);
            level.setBlock(position, Blocks.AIR.defaultBlockState(), UPDATE_FLAGS);
            return false;
        }
        return true;
    }

    @Override
    public Optional<UUID> spawnRocket(
            RocketStructureSnapshot snapshot,
            UUID transactionId
    ) {
        RocketEntity rocket = ModEntities.ROCKET.get().create(level);
        if (rocket == null) {
            return Optional.empty();
        }
        rocket.initialize(snapshot, transactionId, ownerId);
        return level.addFreshEntity(rocket) ? Optional.of(rocket.getUUID()) : Optional.empty();
    }

    @Override
    public boolean rocketMatches(UUID rocketId, UUID snapshotId, String contentHash) {
        Entity entity = level.getEntity(rocketId);
        if (!(entity instanceof RocketEntity rocket) || !rocket.operational()) {
            return false;
        }
        return rocket.snapshot()
                .filter(snapshot -> snapshot.snapshotId().equals(snapshotId))
                .filter(snapshot -> snapshot.contentHash().equals(contentHash))
                .isPresent();
    }

    /** Bounded spatial lookup for an entity spawned before its journal recorded the entity UUID. */
    public List<UUID> matchingAssemblyRockets(
            UUID assemblyTransactionId,
            RocketStructureSnapshot snapshot
    ) {
        Objects.requireNonNull(assemblyTransactionId, "assemblyTransactionId");
        Objects.requireNonNull(snapshot, "snapshot");
        double x = snapshot.sourceOrigin().x() + 0.5D;
        double y = snapshot.sourceOrigin().y();
        double z = snapshot.sourceOrigin().z() + 0.5D;
        AABB recoveryBox = new AABB(x - 1.0D, y - 1.0D, z - 1.0D, x + 1.0D, y + 1.0D, z + 1.0D);
        return level.getEntitiesOfClass(RocketEntity.class, recoveryBox).stream()
                .filter(RocketEntity::operational)
                .filter(rocket -> rocket.assemblyTransactionId()
                        .filter(assemblyTransactionId::equals)
                        .isPresent())
                .filter(rocket -> rocket.snapshot()
                        .filter(entitySnapshot -> entitySnapshot.snapshotId().equals(snapshot.snapshotId()))
                        .filter(entitySnapshot -> entitySnapshot.contentHash().equals(snapshot.contentHash()))
                        .isPresent())
                .map(Entity::getUUID)
                .sorted(Comparator.naturalOrder())
                .toList();
    }

    @Override
    public boolean removeRocket(UUID rocketId, UUID snapshotId) {
        Entity entity = level.getEntity(rocketId);
        if (!(entity instanceof RocketEntity rocket)
                || rocket.snapshot().filter(snapshot -> snapshot.snapshotId().equals(snapshotId)).isEmpty()) {
            return false;
        }
        rocket.discard();
        return rocket.isRemoved();
    }
}
