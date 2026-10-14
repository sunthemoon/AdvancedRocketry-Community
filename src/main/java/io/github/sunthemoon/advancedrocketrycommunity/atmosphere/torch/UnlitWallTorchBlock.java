package io.github.sunthemoon.advancedrocketrycommunity.atmosphere.torch;

import java.util.List;
import net.minecraft.Util;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.WallTorchBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraftforge.registries.ForgeRegistries;

/** Wall extinguished torch with the native {@code facing}; otherwise the same rules as {@link UnlitTorchBlock}. */
public final class UnlitWallTorchBlock extends WallTorchBlock implements EntityBlock, UnlitTorchFluidBarrier {
    private String descriptionId;

    public UnlitWallTorchBlock(Properties properties) {
        super(properties, ParticleTypes.SMOKE);
    }

    /** The vanilla wall torch borrows its item's name; this block has no item, so it keeps its own. */
    @Override
    public String getDescriptionId() {
        if (descriptionId == null) {
            descriptionId = Util.makeDescriptionId("block", ForgeRegistries.BLOCKS.getKey(this));
        }
        return descriptionId;
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new UnlitTorchBlockEntity(pos, state);
    }

    /** Buckets must use the position-aware container gate, not the non-solid replacement fallback. */
    @Override
    public boolean canBeReplaced(BlockState state, Fluid fluid) {
        return false;
    }

    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
    }

    @Override
    public BlockState updateShape(BlockState state, Direction direction, BlockState neighbour, LevelAccessor level,
                                  BlockPos pos, BlockPos neighbourPos) {
        return UnlitTorchBlockEntity.protectedAt(level, pos) ? state
                : super.updateShape(state, direction, neighbour, level, pos, neighbourPos);
    }

    @Override
    public List<ItemStack> getDrops(BlockState state, LootParams.Builder builder) {
        UnlitTorchBlockEntity.offerSourceDrop(builder);
        return super.getDrops(state, builder);
    }

    @Override
    public float getDestroyProgress(BlockState state, Player player, BlockGetter level, BlockPos pos) {
        return UnlitTorchBlockEntity.protectedAt(level, pos) ? 0 : super.getDestroyProgress(state, player, level, pos);
    }

    @Override
    public boolean onDestroyedByPlayer(BlockState state, Level level, BlockPos pos, Player player, boolean harvest,
                                       FluidState fluid) {
        return !UnlitTorchBlockEntity.protectedAt(level, pos)
                && super.onDestroyedByPlayer(state, level, pos, player, harvest, fluid);
    }

    @Override
    public boolean canEntityDestroy(BlockState state, BlockGetter level, BlockPos pos, Entity entity) {
        return !UnlitTorchBlockEntity.protectedAt(level, pos) && super.canEntityDestroy(state, level, pos, entity);
    }

    @Override
    public void onBlockExploded(BlockState state, Level level, BlockPos pos, Explosion explosion) {
        if (!UnlitTorchBlockEntity.protectedAt(level, pos)) {
            super.onBlockExploded(state, level, pos, explosion);
        }
    }
}
