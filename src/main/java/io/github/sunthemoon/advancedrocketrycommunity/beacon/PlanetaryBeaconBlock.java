package io.github.sunthemoon.advancedrocketrycommunity.beacon;

import io.github.sunthemoon.advancedrocketrycommunity.beacon.model.BeaconLimits;
import io.github.sunthemoon.advancedrocketrycommunity.registry.ModBlockEntities;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.RenderShape;
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

/**
 * The planetary beacon controller block (ADR-065 sections 2 and 7). It faces its placer; the pattern extends behind
 * it. Placement by a connected player makes that player the owner and takes the item's custom name. Only the owner or
 * an operator standing at the beacon can switch it (empty hand) or rename it (a named name tag, which is not used up);
 * sneaking with an empty hand only shows its status. Everyone else learns only that it belongs to someone else.
 */
public final class PlanetaryBeaconBlock extends BaseEntityBlock {
    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;

    public PlanetaryBeaconBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
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
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos position, BlockState state) {
        return new PlanetaryBeaconBlockEntity(position, state);
    }

    @Override
    @Nullable
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state,
                                                                  BlockEntityType<T> type) {
        return level.isClientSide ? null
                : createTickerHelper(type, ModBlockEntities.PLANETARY_BEACON.get(), PlanetaryBeaconBlockEntity::serverTick);
    }

    @Override
    public void setPlacedBy(Level level, BlockPos position, BlockState state, @Nullable LivingEntity placer,
                            ItemStack stack) {
        super.setPlacedBy(level, position, state, placer, stack);
        if (!level.isClientSide && placer instanceof ServerPlayer player && !(player instanceof FakePlayer)
                && level.getBlockEntity(position) instanceof PlanetaryBeaconBlockEntity beacon) {
            beacon.placedBy(player.getUUID(), stack.hasCustomHoverName() ? stack.getHoverName().getString() : "");
        }
    }

    @Override
    @SuppressWarnings("deprecation")
    public InteractionResult use(BlockState state, Level level, BlockPos position, Player player, InteractionHand hand,
                                 BlockHitResult hit) {
        ItemStack held = player.getItemInHand(hand);
        boolean nameTag = held.is(Items.NAME_TAG) && held.hasCustomHoverName();
        if (hand != InteractionHand.MAIN_HAND || !held.isEmpty() && !nameTag) {
            return InteractionResult.PASS;
        }
        if (level instanceof ServerLevel server && player instanceof ServerPlayer actor
                && level.getBlockEntity(position) instanceof PlanetaryBeaconBlockEntity beacon) {
            actor.displayClientMessage(interact(server, actor, beacon, held, nameTag), true);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    private static Component interact(ServerLevel level, ServerPlayer actor, PlanetaryBeaconBlockEntity beacon,
                                      ItemStack held, boolean nameTag) {
        if (beacon.status() == BeaconStatus.INVALID_DATA) {
            return Component.translatable(BeaconStatus.INVALID_DATA.translationKey());
        }
        boolean owner = beacon.owner().filter(actor.getUUID()::equals).isPresent();
        if (actor instanceof FakePlayer || actor.isSpectator() || !owner && !actor.hasPermissions(2)) {
            return Component.translatable(beacon.owner().isEmpty() ? BeaconStatus.NO_OWNER.translationKey()
                    : "message.advancedrocketrycommunity.beacon.not_owner");
        }
        if (nameTag) {
            String name = held.getHoverName().getString();
            if (!BeaconLimits.validName(name)) {
                return Component.translatable("message.advancedrocketrycommunity.beacon.name_invalid",
                        BeaconLimits.MAX_NAME_CHARS);
            }
            BeaconStatus status = beacon.rename(level, name);
            return Component.translatable("message.advancedrocketrycommunity.beacon.renamed", name,
                    Component.translatable(status.translationKey()));
        }
        BeaconStatus status = actor.isShiftKeyDown() ? beacon.status() : beacon.toggle(level);
        return Component.translatable(status.translationKey());
    }

    @Override
    @SuppressWarnings("deprecation")
    public void onRemove(BlockState state, Level level, BlockPos position, BlockState newState, boolean moved) {
        if (!state.is(newState.getBlock()) && level instanceof ServerLevel server
                && level.getBlockEntity(position) instanceof PlanetaryBeaconBlockEntity beacon) {
            beacon.removedFromWorld(server);
        }
        super.onRemove(state, level, position, newState, moved);
    }
}
