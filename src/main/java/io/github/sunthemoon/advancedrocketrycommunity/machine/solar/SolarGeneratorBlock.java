package io.github.sunthemoon.advancedrocketrycommunity.machine.solar;

import java.util.Objects;
import java.util.function.BiFunction;
import java.util.function.Supplier;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.network.NetworkHooks;

/** Fixed UP collector. Supported drops are the ordinary block item, never a stored-energy carrier. */
public final class SolarGeneratorBlock extends BaseEntityBlock {
    private final BiFunction<BlockPos, BlockState, SolarGeneratorBlockEntity> factory;
    private final Supplier<BlockEntityType<SolarGeneratorBlockEntity>> type;
    public SolarGeneratorBlock(Properties properties, BiFunction<BlockPos, BlockState, SolarGeneratorBlockEntity> factory,
                               Supplier<BlockEntityType<SolarGeneratorBlockEntity>> type) {
        super(properties);
        this.factory = Objects.requireNonNull(factory, "factory");
        this.type = Objects.requireNonNull(type, "type");
    }
    @Override public RenderShape getRenderShape(BlockState state) { return RenderShape.MODEL; }
    @Override public BlockEntity newBlockEntity(BlockPos position, BlockState state) { return factory.apply(position, state); }
    @Override @Nullable
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> requested) {
        return level.isClientSide ? null : createTickerHelper(requested, type.get(),
                (world, position, blockState, generator) -> SolarGeneratorBlockEntity.serverTick(
                        (ServerLevel) world, position, blockState, generator));
    }
    @Override public InteractionResult use(BlockState state, Level level, BlockPos position, Player player,
                                           InteractionHand hand, BlockHitResult hit) {
        if (player instanceof FakePlayer || !player.isAlive() || player.isSpectator()) { return InteractionResult.FAIL; }
        if (level.isClientSide) { return InteractionResult.SUCCESS; }
        if (!(level instanceof ServerLevel serverLevel) || !serverLevel.getServer().isSameThread()) { return InteractionResult.FAIL; }
        var chunk = serverLevel.getChunkSource().getChunkNow(position.getX() >> 4, position.getZ() >> 4);
        var owner = chunk == null ? null : chunk.getBlockEntities().get(position);
        if (owner instanceof SolarGeneratorBlockEntity generator && generator.repairRequired()
                && SolarGeneratorMenu.admitted(player, generator)) {
            player.sendSystemMessage(Component.translatable("screen.advancedrocketrycommunity.solar.reason.repair_required"));
            return InteractionResult.FAIL;
        }
        if (player instanceof ServerPlayer connected && owner instanceof SolarGeneratorBlockEntity generator
                && generator.available() && SolarGeneratorMenu.admitted(player, generator)) {
            NetworkHooks.openScreen(connected, generator, SolarGeneratorMenu::writeOpenData);
            return InteractionResult.CONSUME;
        }
        return InteractionResult.FAIL;
    }
    static boolean protectedData(BlockGetter level, BlockPos position) {
        return level.getBlockEntity(position) instanceof SolarGeneratorBlockEntity generator && generator.repairRequired();
    }
    @Override public boolean onDestroyedByPlayer(BlockState state, Level level, BlockPos position, Player player,
                                                  boolean harvest, FluidState fluid) {
        return !protectedData(level, position) && super.onDestroyedByPlayer(state, level, position, player, harvest, fluid);
    }
    @Override public boolean canEntityDestroy(BlockState state, BlockGetter level, BlockPos position, Entity entity) {
        return !protectedData(level, position) && super.canEntityDestroy(state, level, position, entity);
    }
    @Override public void onBlockExploded(BlockState state, Level level, BlockPos position, Explosion explosion) {
        if (!protectedData(level, position)) { super.onBlockExploded(state, level, position, explosion); }
    }
}
