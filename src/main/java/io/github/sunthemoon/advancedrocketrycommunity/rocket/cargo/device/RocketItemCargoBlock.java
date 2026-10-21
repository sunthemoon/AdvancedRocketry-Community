package io.github.sunthemoon.advancedrocketrycommunity.rocket.cargo.device;

import io.github.sunthemoon.advancedrocketrycommunity.rocket.fuel.loader.LinkerItem;
import java.util.List;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
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
 * W11CD section 5.1's item column. Breaking an unprotected device drops the plain device item through its loot table
 * and spills the buffer as ordinary item drops, as vanilla containers do; a protected device (escrow-locked, or
 * holding an unsupported root) refuses player removal, leaves explosions, and drops and spills nothing. The device
 * item carries nothing, so the placer owns a placed device and its identity is always fresh.
 */
public final class RocketItemCargoBlock extends BaseEntityBlock {
    private final RocketCargoRole role;

    public RocketItemCargoBlock(RocketCargoRole role, Properties properties) {
        super(properties);
        this.role = java.util.Objects.requireNonNull(role);
    }

    public RocketCargoRole role() { return role; }

    /** The registry path of the device with this role. */
    public static String id(RocketCargoRole role) {
        return role == RocketCargoRole.LOAD ? "rocket_item_loader" : "rocket_item_unloader";
    }

    @Override
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos position, BlockState state) {
        return new RocketItemCargoBlockEntity(position, state);
    }

    static boolean protectedAt(BlockGetter level, BlockPos position) {
        return level.getBlockEntity(position) instanceof RocketItemCargoBlockEntity device && device.protectedFromRemoval();
    }

    /** The placer owns the device; placement by anything other than a connected player leaves it unclaimed. */
    @Nullable
    static UUID placementOwner(@Nullable LivingEntity placer) {
        return placer instanceof ServerPlayer player && !(placer instanceof FakePlayer) ? player.getUUID() : null;
    }

    @Override
    public void setPlacedBy(Level level, BlockPos position, BlockState state, @Nullable LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(level, position, state, placer, stack);
        if (!level.isClientSide && level.getBlockEntity(position) instanceof RocketItemCargoBlockEntity device) {
            // RocketItemCargoItem.place refuses a BlockEntityTag before any world change.
            device.placed(placementOwner(placer));
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
        if (player.getItemInHand(hand).getItem() instanceof LinkerItem) {
            return InteractionResult.PASS;
        }
        if (level.isClientSide) {
            return InteractionResult.SUCCESS;
        }
        if (!(player instanceof ServerPlayer server) || player instanceof FakePlayer || player.isSpectator()
                || !level.isLoaded(position) || !(level.getBlockEntity(position) instanceof RocketItemCargoBlockEntity device)) {
            return InteractionResult.FAIL;
        }
        if (!device.admitted(player)) {
            // Data refusals and foreign players get a reason; a distant or obstructed owner gets none.
            RocketCargoStatus refusal = device.deviceRefusal();
            boolean data = refusal == RocketCargoStatus.INVALID_DATA || refusal == RocketCargoStatus.UNSUPPORTED_DATA;
            if (data || !device.owner().filter(player.getUUID()::equals).isPresent()) {
                player.displayClientMessage(Component.translatable(
                        (data ? refusal : RocketCargoStatus.NOT_OWNER).translationKey()), true);
            }
            return InteractionResult.FAIL;
        }
        NetworkHooks.openScreen(server, device, RocketItemCargoMenu::writeOpenData);
        return InteractionResult.CONSUME;
    }

    /** The loot table's plain item for an unprotected device; nothing for a protected one. */
    @Override
    public List<ItemStack> getDrops(BlockState state, LootParams.Builder builder) {
        if (builder.getOptionalParameter(LootContextParams.BLOCK_ENTITY) instanceof RocketItemCargoBlockEntity device
                && device.protectedFromRemoval()) {
            return List.of();
        }
        return super.getDrops(state, builder);
    }

    /** The buffer spills here, once, as a vanilla container's contents do; a protected device spills nothing. */
    @Override
    @SuppressWarnings("deprecation")
    public void onRemove(BlockState state, Level level, BlockPos position, BlockState replacement, boolean moved) {
        if (!state.is(replacement.getBlock()) && !level.isClientSide
                && level.getBlockEntity(position) instanceof RocketItemCargoBlockEntity device) {
            device.noteRemoval();
            for (ItemStack stack : device.spill()) {
                Containers.dropItemStack(level, position.getX(), position.getY(), position.getZ(), stack);
            }
        }
        super.onRemove(state, level, position, replacement, moved);
    }

    @Override
    public boolean onDestroyedByPlayer(BlockState state, Level level, BlockPos position, Player player,
                                       boolean willHarvest, FluidState fluid) {
        return !protectedAt(level, position) && super.onDestroyedByPlayer(state, level, position, player, willHarvest, fluid);
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
