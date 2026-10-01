package io.github.sunthemoon.advancedrocketrycommunity.endgame.blackhole;

import io.github.sunthemoon.advancedrocketrycommunity.endgame.device.EndgameDeviceBlock;
import io.github.sunthemoon.advancedrocketrycommunity.endgame.device.EndgameDeviceBlockEntity;
import io.github.sunthemoon.advancedrocketrycommunity.endgame.device.EndgameDeviceMenu;
import io.github.sunthemoon.advancedrocketrycommunity.endgame.intent.EndgameIntentGuard;
import io.github.sunthemoon.advancedrocketrycommunity.endgame.model.EndgameCode;
import io.github.sunthemoon.advancedrocketrycommunity.endgame.service.EndgameRuntime;
import io.github.sunthemoon.advancedrocketrycommunity.registry.ModBlockEntities;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.network.NetworkHooks;

/** ADR-057 section 3: the black-hole generator controller; the structure extends behind its front face. */
public final class BlackHoleGeneratorBlock extends EndgameDeviceBlock {
    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;

    public BlackHoleGeneratorBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH));
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
    }

    @Override
    @SuppressWarnings("deprecation")
    public InteractionResult use(BlockState state, Level level, BlockPos position, Player player, InteractionHand hand,
                                 BlockHitResult hit) {
        if (level.isClientSide) {
            return InteractionResult.SUCCESS;
        }
        if (!(player instanceof ServerPlayer serverPlayer) || player instanceof FakePlayer
                || !(level.getBlockEntity(position) instanceof BlackHoleGeneratorBlockEntity generator)) {
            return InteractionResult.PASS;
        }
        EndgameCode refusal = EndgameRuntime.devices().isEmpty() ? EndgameCode.ROOT_UNAVAILABLE
                : generator.quarantined() ? EndgameCode.DEVICE_QUARANTINED
                : EndgameDeviceMenu.viewAuthority((ServerLevel) level, player, generator).refusal();
        if (refusal != EndgameCode.OK) {
            EndgameIntentGuard.statusLine(player, refusal);
            return InteractionResult.CONSUME;
        }
        NetworkHooks.openScreen(serverPlayer, generator, buffer -> BlackHoleGeneratorMenu.writeOpenData(buffer,
                generator));
        return InteractionResult.CONSUME;
    }

    /** Fuel drops; the stored energy is lost (ADR-054 section 2). */
    @Override
    protected void dropLocalBuffers(Level level, BlockPos position, EndgameDeviceBlockEntity device) {
        if (device instanceof BlackHoleGeneratorBlockEntity generator) {
            generator.dropFuel(level, position);
        }
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos position, BlockState state) {
        return new BlackHoleGeneratorBlockEntity(position, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state,
                                                                  BlockEntityType<T> type) {
        return createTickerHelper(type, ModBlockEntities.BLACK_HOLE_GENERATOR.get(), level.isClientSide
                ? BlackHoleGeneratorBlockEntity::clientTick : BlackHoleGeneratorBlockEntity::serverTick);
    }

    @Override
    @SuppressWarnings("deprecation")
    public BlockState rotate(BlockState state, Rotation rotation) {
        return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
    }

    @Override
    @SuppressWarnings("deprecation")
    public BlockState mirror(BlockState state, Mirror mirror) {
        return state.rotate(mirror.getRotation(state.getValue(FACING)));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }
}
