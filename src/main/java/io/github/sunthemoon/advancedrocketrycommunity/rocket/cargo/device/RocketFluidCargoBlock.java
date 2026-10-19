package io.github.sunthemoon.advancedrocketrycommunity.rocket.cargo.device;

import io.github.sunthemoon.advancedrocketrycommunity.rocket.fuel.loader.FluidFuelLoaderItem;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.fuel.loader.LinkerItem;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.fuel.loader.LoaderCarrier;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.fuel.loader.LoaderTank;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
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
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.network.NetworkHooks;

/**
 * Design section 3's removal and placement rules. Breaking an unprotected device drops one loader item carrying its
 * owner and tank; a protected device (escrow-locked, or holding an unsupported root) refuses player removal, leaves
 * explosions and drops nothing. Placement applies the carrier owner rule and creates a fresh device identity.
 */
public final class RocketFluidCargoBlock extends BaseEntityBlock {
    private final RocketCargoRole role;

    public RocketFluidCargoBlock(RocketCargoRole role, Properties properties) {
        super(properties);
        this.role = java.util.Objects.requireNonNull(role);
    }

    public RocketCargoRole role() { return role; }

    @Override
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos position, BlockState state) {
        return new RocketFluidCargoBlockEntity(position, state);
    }


    static boolean protectedAt(BlockGetter level, BlockPos position) {
        return level.getBlockEntity(position) instanceof RocketFluidCargoBlockEntity loader && loader.protectedFromRemoval();
    }

    /**
     * The owner rule: no carried owner or the placer's own gives the placer; an operator keeps a carried owner; a
     * non-operator placing another player's carrier places an unclaimed device. Placement by anything other than a
     * connected player leaves the device unclaimed.
     */
    static UUID placementOwner(@Nullable LivingEntity placer, @Nullable UUID carried) {
        if (!(placer instanceof ServerPlayer player) || placer instanceof FakePlayer) {
            return null;
        }
        if (carried == null || carried.equals(player.getUUID())) {
            return player.getUUID();
        }
        return player.hasPermissions(2) ? carried : null;
    }

    @Override
    public void setPlacedBy(Level level, BlockPos position, BlockState state, @Nullable LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(level, position, state, placer, stack);
        if (!level.isClientSide && level.getBlockEntity(position) instanceof RocketFluidCargoBlockEntity loader) {
            // FluidFuelLoaderItem.place preflights the carrier before any world change.
            LoaderCarrier carrier = FluidFuelLoaderItem.carrier(stack).orElse(new LoaderCarrier(null, LoaderTank.EMPTY));
            loader.placed(placementOwner(placer, carrier.owner()), carrier.tank());
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
        ItemStack held = player.getItemInHand(hand);
        if (held.getItem() instanceof LinkerItem) {
            return InteractionResult.PASS;
        }
        if (!held.isEmpty() && !held.getCapability(ForgeCapabilities.FLUID_HANDLER_ITEM).isPresent()) {
            return InteractionResult.PASS;
        }
        if (level.isClientSide) {
            return InteractionResult.SUCCESS;
        }
        if (!(player instanceof ServerPlayer server) || player instanceof FakePlayer || player.isSpectator()
                || !level.isLoaded(position) || !(level.getBlockEntity(position) instanceof RocketFluidCargoBlockEntity loader)) {
            return InteractionResult.FAIL;
        }
        if (!loader.admitted(player)) {
            // Data refusals and foreign players get a reason; a distant or obstructed owner gets none.
            RocketCargoStatus refusal = loader.deviceRefusal();
            boolean data = refusal == RocketCargoStatus.INVALID_DATA || refusal == RocketCargoStatus.UNSUPPORTED_DATA;
            if (data || !loader.owner().filter(player.getUUID()::equals).isPresent()) {
                player.displayClientMessage(Component.translatable(
                        (data ? refusal : RocketCargoStatus.NOT_OWNER).translationKey()), true);
            }
            return InteractionResult.FAIL;
        }
        if (held.isEmpty()) {
            NetworkHooks.openScreen(server, loader, RocketFluidCargoMenu::writeOpenData);
            return InteractionResult.CONSUME;
        }
        if (loader.exchange(player, hand)) {
            return InteractionResult.CONSUME;
        }
        player.displayClientMessage(Component.translatable(loader.escrowLocked()
                ? RocketCargoStatus.ESCROW_PENDING.translationKey() : FluidFuelLoaderItem.EXCHANGE_REFUSED), true);
        return InteractionResult.FAIL;
    }

    /** One carrier for an unprotected device, on the first loader item of the loot; nothing for a protected one. */
    @Override
    public List<ItemStack> getDrops(BlockState state, LootParams.Builder builder) {
        if (!(builder.getOptionalParameter(LootContextParams.BLOCK_ENTITY) instanceof RocketFluidCargoBlockEntity loader)) {
            return super.getDrops(state, builder);
        }
        if (loader.protectedFromRemoval()) {
            return List.of();
        }
        List<ItemStack> drops = new ArrayList<>();
        boolean carried = false;
        for (ItemStack drop : super.getDrops(state, builder)) {
            if (!drop.is(asItem())) {
                drops.add(drop);
            } else if (!carried) {
                drops.add(FluidFuelLoaderItem.withCarrier(drop.copyWithCount(1), loader.carrier()));
                carried = true;
            }
        }
        return drops;
    }

    @Override
    @SuppressWarnings("deprecation")
    public void onRemove(BlockState state, Level level, BlockPos position, BlockState replacement, boolean moved) {
        if (!state.is(replacement.getBlock()) && !level.isClientSide
                && level.getBlockEntity(position) instanceof RocketFluidCargoBlockEntity loader) {
            loader.noteRemoval();
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
