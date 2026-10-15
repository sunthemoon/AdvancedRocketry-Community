package io.github.sunthemoon.advancedrocketrycommunity.rocket.bay;

import io.github.sunthemoon.advancedrocketrycommunity.ModIdentity;
import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Containers;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.network.NetworkHooks;

/**
 * Section 6's removal policy. A valid, available, unclaimed bay drops its package once as the ordinary item plus a plain
 * bay item; a consumed bay drops only the bay. Guarded roots and claimed positions refuse player break, leave
 * explosions and drop nothing. No {@code BlockEntityTag} carries the root, and ordinary placement restores nothing.
 */
public final class SatelliteBayBlock extends BaseEntityBlock {
    /** The loot table's dynamic entry that yields the extracted package. */
    public static final ResourceLocation PACKAGE_DROP = ModIdentity.id("rocket_satellite_bay_package");

    public SatelliteBayBlock(Properties properties) {
        super(properties);
    }

    @Override
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos position, BlockState state) {
        return new SatelliteBayBlockEntity(position, state);
    }

    static boolean protectedAt(BlockGetter level, BlockPos position) {
        return level.getBlockEntity(position) instanceof SatelliteBayBlockEntity bay && bay.protectedFromRemoval();
    }

    @Override
    public void setPlacedBy(Level level, BlockPos position, BlockState state, @Nullable LivingEntity placer,
                            ItemStack stack) {
        super.setPlacedBy(level, position, state, placer, stack);
        if (!level.isClientSide && placer instanceof ServerPlayer player && !(player instanceof FakePlayer)
                && level.getBlockEntity(position) instanceof SatelliteBayBlockEntity bay) {
            bay.assignPlacer(player);
        }
    }

    /** Only {@link #use} opens the menu, so a spectator's container shortcut never reaches it. */
    @Nullable
    @Override
    public MenuProvider getMenuProvider(BlockState state, Level level, BlockPos position) {
        return null;
    }

    @Override
    public InteractionResult use(BlockState state, Level level, BlockPos position, Player player, InteractionHand hand,
                                 BlockHitResult hit) {
        if (!(level.getBlockEntity(position) instanceof SatelliteBayBlockEntity bay)) {
            return InteractionResult.PASS;
        }
        if (level.isClientSide) {
            return InteractionResult.SUCCESS;
        }
        SatelliteBayStatus.Guard guard = bay.dataGuard();
        if (guard != SatelliteBayStatus.Guard.NONE) {
            player.displayClientMessage(Component.translatable(guard.translationKey()), true);
            return InteractionResult.FAIL;
        }
        if (!(player instanceof ServerPlayer server) || !bay.admitted(player)) {
            player.displayClientMessage(Component.translatable(
                    "message.advancedrocketrycommunity.rocket_satellite_bay.unauthorized"), true);
            return InteractionResult.FAIL;
        }
        NetworkHooks.openScreen(server, bay, SatelliteBayMenu::writeOpenData);
        return InteractionResult.CONSUME;
    }

    @Override
    public List<ItemStack> getDrops(BlockState state, LootParams.Builder builder) {
        if (builder.getOptionalParameter(LootContextParams.BLOCK_ENTITY) instanceof SatelliteBayBlockEntity bay) {
            if (bay.protectedFromRemoval()) {
                return List.of();
            }
            builder.withDynamicDrop(PACKAGE_DROP, output -> {
                ItemStack extracted = bay.takePackageForRemoval();
                if (!extracted.isEmpty()) {
                    output.accept(extracted);
                }
            });
        }
        return super.getDrops(state, builder);
    }

    @Override
    @SuppressWarnings("deprecation")
    public void onRemove(BlockState state, Level level, BlockPos position, BlockState replacement, boolean moved) {
        if (!state.is(replacement.getBlock()) && !level.isClientSide
                && level.getBlockEntity(position) instanceof SatelliteBayBlockEntity bay) {
            bay.noteRemoval();
            ItemStack extracted = bay.takePackageForRemoval();
            if (!extracted.isEmpty()) {
                Containers.dropItemStack(level, position.getX() + 0.5D, position.getY() + 0.5D,
                        position.getZ() + 0.5D, extracted);
            }
        }
        super.onRemove(state, level, position, replacement, moved);
    }

    @Override
    public boolean onDestroyedByPlayer(BlockState state, Level level, BlockPos position, Player player,
                                       boolean willHarvest, FluidState fluid) {
        return !protectedAt(level, position)
                && super.onDestroyedByPlayer(state, level, position, player, willHarvest, fluid);
    }

    @Override
    public boolean canEntityDestroy(BlockState state, BlockGetter level, BlockPos position, Entity entity) {
        return !protectedAt(level, position) && super.canEntityDestroy(state, level, position, entity);
    }

    @Override
    public void onBlockExploded(BlockState state, Level level, BlockPos position, Explosion explosion) {
        if (!protectedAt(level, position)) {
            super.onBlockExploded(state, level, position, explosion);
        }
    }

    @Override
    @SuppressWarnings("deprecation")
    public float getDestroyProgress(BlockState state, Player player, BlockGetter level, BlockPos position) {
        return protectedAt(level, position) ? 0.0F : super.getDestroyProgress(state, player, level, position);
    }
}
