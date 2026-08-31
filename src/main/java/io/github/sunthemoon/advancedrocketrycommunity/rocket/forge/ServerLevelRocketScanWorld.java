package io.github.sunthemoon.advancedrocketrycommunity.rocket.forge;

import io.github.sunthemoon.advancedrocketrycommunity.registry.ModBlockTags;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.model.RocketBlockEntityPayload;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.model.RocketPosition;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.scan.RocketScanObservation;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.scan.RocketScanWorld;
import java.util.Objects;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.registries.ForgeRegistries;

public final class ServerLevelRocketScanWorld implements RocketScanWorld {
    private static final Set<Block> ALWAYS_FORBIDDEN = Set.of(
            Blocks.COMMAND_BLOCK,
            Blocks.CHAIN_COMMAND_BLOCK,
            Blocks.REPEATING_COMMAND_BLOCK,
            Blocks.STRUCTURE_BLOCK,
            Blocks.JIGSAW,
            Blocks.SPAWNER,
            Blocks.END_PORTAL,
            Blocks.END_PORTAL_FRAME,
            Blocks.NETHER_PORTAL,
            Blocks.MOVING_PISTON,
            Blocks.PISTON_HEAD
    );

    private final ServerLevel level;
    private final RocketBlockEntityAdapters adapters;

    public ServerLevelRocketScanWorld(ServerLevel level, RocketBlockEntityAdapters adapters) {
        this.level = Objects.requireNonNull(level, "level");
        this.adapters = Objects.requireNonNull(adapters, "adapters");
    }

    @Override
    public RocketScanObservation observe(RocketPosition absolutePosition) {
        BlockPos position = toBlockPos(absolutePosition);
        if (!level.hasChunkAt(position)) {
            return RocketScanObservation.unloaded();
        }
        BlockState state = level.getBlockState(position);
        if (state.isAir()) {
            return RocketScanObservation.empty();
        }
        String id = blockId(state);
        if (ALWAYS_FORBIDDEN.contains(state.getBlock()) || state.is(ModBlockTags.ROCKET_FORBIDDEN)) {
            return RocketScanObservation.forbidden(id);
        }
        if (!state.is(ModBlockTags.ROCKET_MOVABLE)) {
            return RocketScanObservation.boundary(id);
        }

        RocketBlockEntityPayload payload = null;
        BlockEntity blockEntity = level.getBlockEntity(position);
        if (blockEntity != null) {
            RocketBlockEntityAdapters.CaptureResult captured = adapters.capture(blockEntity);
            if (!captured.supported()) {
                return RocketScanObservation.unsupportedBlockEntity(
                        captured.optionalRejectionDetail().orElse("unsupported BlockEntity")
                );
            }
            payload = captured.optionalPayload().orElseThrow();
        }
        return RocketScanObservation.movable(
                RocketBlockStateAdapter.capture(state),
                RocketForgeMetrics.resolve(state),
                payload
        );
    }

    static BlockPos toBlockPos(RocketPosition position) {
        return new BlockPos(position.x(), position.y(), position.z());
    }

    static RocketPosition toRocketPosition(BlockPos position) {
        return new RocketPosition(position.getX(), position.getY(), position.getZ());
    }

    private static String blockId(BlockState state) {
        var id = ForgeRegistries.BLOCKS.getKey(state.getBlock());
        return id == null ? "unregistered_block" : id.toString();
    }
}
