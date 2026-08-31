package io.github.sunthemoon.advancedrocketrycommunity.atmosphere.server;

import io.github.sunthemoon.advancedrocketrycommunity.atmosphere.scan.CellObservation;
import io.github.sunthemoon.advancedrocketrycommunity.atmosphere.scan.VolumePosition;
import io.github.sunthemoon.advancedrocketrycommunity.atmosphere.scan.VolumeWorldView;
import io.github.sunthemoon.advancedrocketrycommunity.registry.ModBlockTags;
import java.util.Objects;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.FenceGateBlock;
import net.minecraft.world.level.block.TrapDoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.levelgen.Heightmap;

/** Read-only loaded-chunk adapter. It never requests a chunk or creates a ticket. */
public final class ServerLevelVolumeWorldView implements VolumeWorldView {
    private final ServerLevel level;
    private final boolean exposedSkyIsOpen;

    public ServerLevelVolumeWorldView(ServerLevel level, boolean exposedSkyIsOpen) {
        this.level = Objects.requireNonNull(level, "level");
        this.exposedSkyIsOpen = exposedSkyIsOpen;
    }

    @Override
    public CellObservation observe(VolumePosition position) {
        BlockPos blockPosition = new BlockPos(position.x(), position.y(), position.z());
        if (level.isOutsideBuildHeight(blockPosition)) {
            return CellObservation.OPEN;
        }
        if (!level.hasChunkAt(blockPosition)) {
            return CellObservation.UNLOADED;
        }

        BlockState state = level.getBlockState(blockPosition);
        if (state.is(ModBlockTags.ATMOSPHERE_SEALING)) {
            return CellObservation.SEALED;
        }
        if (isDoorLike(state)) {
            return state.getValue(BlockStateProperties.OPEN)
                    ? CellObservation.TRAVERSABLE
                    : CellObservation.SEALED;
        }
        if (state.is(ModBlockTags.ATMOSPHERE_PERMEABLE)) {
            return CellObservation.TRAVERSABLE;
        }

        FluidState fluid = state.getFluidState();
        if (!fluid.isEmpty()) {
            return CellObservation.SEALED;
        }
        if (state.isAir()) {
            if (isExposedToVacuum(blockPosition)) {
                return CellObservation.OPEN;
            }
            return CellObservation.TRAVERSABLE;
        }

        VoxelShape collision = state.getCollisionShape(level, blockPosition);
        return Block.isShapeFullBlock(collision)
                ? CellObservation.SEALED
                : CellObservation.TRAVERSABLE;
    }

    private boolean isExposedToVacuum(BlockPos position) {
        if (!exposedSkyIsOpen) {
            return false;
        }
        if (level.canSeeSky(position)) {
            return true;
        }
        // getHeight rechecks hasChunk before reading its already-loaded LevelChunk.
        int surface = level.getHeight(
                Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                position.getX(),
                position.getZ()
        );
        return position.getY() >= surface;
    }

    private static boolean isDoorLike(BlockState state) {
        return state.hasProperty(BlockStateProperties.OPEN)
                && (state.getBlock() instanceof DoorBlock
                || state.getBlock() instanceof TrapDoorBlock
                || state.getBlock() instanceof FenceGateBlock);
    }
}
