package io.github.sunthemoon.advancedrocketrycommunity.rocket.cargo.device;

import io.github.sunthemoon.advancedrocketrycommunity.rocket.entity.RocketEntity;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.fuel.loader.LinkerItem;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.fuel.loader.LinkerSelection;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.fuel.loader.LoaderBinding;
import static io.github.sunthemoon.advancedrocketrycommunity.rocket.fuel.loader.LinkerItem.*;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketFlightData;
import java.util.Optional;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraftforge.common.util.FakePlayer;

/**
 * The rocket-binding half of the linker (design section 3). Using it on a loaded loader the player owns selects that
 * device; using it on a rocket then binds the selected device on the server. Sneak-use on a loader clears its idle
 * binding, and sneak-use in the air clears the selection. Every change starts a 10-tick cooldown, so a player makes at
 * most one change per 10 ticks. The other linker roles belong to W11e.
 */
public final class RocketFluidCargoLinker {
    private static final double MAX_ACTOR_DISTANCE = RocketFluidCargoBlockEntity.MAX_ACTOR_DISTANCE;
    private RocketFluidCargoLinker() { }

    /** The held selection; empty when there is none or it does not decode (an inert selection). */
    public static Optional<LinkerSelection> selection(ItemStack stack) {
        if (!stack.hasTag() || !stack.getTag().contains(LinkerSelection.KEY)) {
            return Optional.empty();
        }
        try {
            return Optional.of(LinkerSelection.decode(stack.getTag().get(LinkerSelection.KEY)));
        } catch (IllegalArgumentException inert) {
            return Optional.empty();
        }
    }

    /** A connected, living, non-fake, non-spectator player whose hand still holds a linker. */
    static boolean actor(Player player, InteractionHand hand) {
        return player instanceof ServerPlayer server && !(player instanceof FakePlayer) && server.getServer().isSameThread() && !server.hasDisconnected()
                && player.isAlive() && !player.isSpectator() && player.getItemInHand(hand).getItem() instanceof LinkerItem;
    }

    private static InteractionResult refuse(Player player, String key) {
        player.displayClientMessage(Component.translatable(key), true);
        return InteractionResult.FAIL;
    }

    private static InteractionResult changed(Player player, ItemStack stack, String key) {
        player.getCooldowns().addCooldown(stack.getItem(), CHANGE_TICKS);
        player.displayClientMessage(Component.translatable(key), true);
        return InteractionResult.CONSUME;
    }

    /** Select a loader, or with sneak clear its binding. */
    public static InteractionResult select(UseOnContext context) {
        Level level = context.getLevel();
        BlockPos position = context.getClickedPos();
        if (level.isClientSide) {
            return level.getBlockState(position).getBlock() instanceof RocketFluidCargoBlock
                    ? InteractionResult.SUCCESS : InteractionResult.PASS;
        }
        Player player = context.getPlayer();
        InteractionHand hand = context.getHand();
        if (player == null || !actor(player, hand) || !level.isLoaded(position)
                || !(level.getBlockEntity(position) instanceof RocketFluidCargoBlockEntity loader)) {
            return InteractionResult.PASS;
        }
        ItemStack stack = player.getItemInHand(hand);
        if (player.getCooldowns().isOnCooldown(stack.getItem())) {
            return InteractionResult.FAIL;
        }
        RocketCargoStatus refusal = ownerRefusal(player, loader);
        if (refusal != null) {
            return refusal == RocketCargoStatus.OUT_OF_RANGE ? refuse(player, TOO_FAR) : refuse(player, refusal.translationKey());
        }
        if (player.isSecondaryUseActive()) {
            if (loader.root().orElseThrow().binding() == null) {
                return refuse(player, NOT_BOUND);
            }
            return loader.clearBinding() ? changed(player, stack, UNBOUND)
                    : refuse(player, RocketCargoStatus.ESCROW_PENDING.translationKey());
        }
        LinkerSelection selected = new LinkerSelection(loader.root().orElseThrow().device(),
                level.dimension().location(), position);
        if (selection(stack).filter(selected::equals).isPresent()) {
            return InteractionResult.FAIL;
        }
        stack.getOrCreateTag().put(LinkerSelection.KEY, selected.encode());
        return changed(player, stack, SELECTED);
    }

    /**
     * Why this player may not change this device: unsupported or duplicate data, an unclaimed or foreign device
     * (operators included), escrow, or distance (OUT_OF_RANGE here means the actor is too far). Null when allowed.
     */
    @Nullable
    static RocketCargoStatus ownerRefusal(Player player, RocketFluidCargoBlockEntity loader) {
        RocketCargoStatus refusal = loader.deviceRefusal();
        if (refusal == RocketCargoStatus.INVALID_DATA || refusal == RocketCargoStatus.UNSUPPORTED_DATA
                || refusal == RocketCargoStatus.NOT_OWNER) {
            return refusal;
        }
        if (!loader.owner().filter(player.getUUID()::equals).isPresent()) {
            return RocketCargoStatus.NOT_OWNER;
        }
        if (!loader.admitted(player)) {
            return RocketCargoStatus.OUT_OF_RANGE;
        }
        // A held device shows its progress elsewhere; a rebinding or clearing is refused as locked by escrow.
        return loader.escrowLocked() ? RocketCargoStatus.ESCROW_PENDING : null;
    }

    /**
     * The server half of the binding: the held selection names a loaded loader that this player owns, and the rocket
     * the player used is stationary, theirs and within 6 blocks of it. The entity's integer ID is never authority;
     * this is the entity the server resolved for the interaction.
     */
    public static InteractionResult bindRocket(ServerPlayer player, InteractionHand hand, RocketEntity rocket) {
        if (!actor(player, hand) || rocket.level() != player.level() || rocket.isRemoved()) {
            return InteractionResult.FAIL;
        }
        ItemStack stack = player.getItemInHand(hand);
        if (player.getCooldowns().isOnCooldown(stack.getItem())) {
            return InteractionResult.FAIL;
        }
        Optional<LinkerSelection> held = selection(stack);
        if (held.isEmpty()) {
            return refuse(player, NO_SELECTION);
        }
        LinkerSelection selection = held.get();
        ServerLevel level = (ServerLevel) player.level();
        if (!selection.level().equals(level.dimension().location()) || !level.isInWorldBounds(selection.pos())
                || !level.getWorldBorder().isWithinBounds(selection.pos())) {
            return refuse(player, RocketCargoStatus.STALE_BINDING.translationKey());
        }
        // The selected chunk must already be loaded before anything is looked up there.
        if (level.getChunkSource().getChunkNow(selection.pos().getX() >> 4, selection.pos().getZ() >> 4) == null) {
            return refuse(player, RocketCargoStatus.WAITING_UNLOADED.translationKey());
        }
        if (!(level.getBlockEntity(selection.pos()) instanceof RocketFluidCargoBlockEntity loader)
                || !loader.root().map(RocketFluidCargoRoot::device).filter(selection.device()::equals).isPresent()) {
            return refuse(player, RocketCargoStatus.STALE_BINDING.translationKey());
        }
        RocketCargoStatus refusal = ownerRefusal(player, loader);
        if (refusal != null) {
            return refusal == RocketCargoStatus.OUT_OF_RANGE ? refuse(player, TOO_FAR) : refuse(player, refusal.translationKey());
        }
        if (!rocket.operational() || !RocketCargoBindingChecks.loaded(level, rocket.blockPosition())
                || !RocketCargoBindingChecks.atOrigin(rocket)
                || !rocket.flightData().orElseThrow().currentDimension().equals(level.dimension().location())) {
            return refuse(player, RocketCargoStatus.STALE_BINDING.translationKey());
        }
        if (!rocket.ownerId().filter(player.getUUID()::equals).isPresent()) {
            return refuse(player, RocketCargoStatus.NOT_OWNER.translationKey());
        }
        if (!RocketCargoBindingChecks.stationary(rocket) || RocketCargoBindingChecks.occupied(rocket)
                || !io.github.sunthemoon.advancedrocketrycommunity.resourceescrow.RocketEscrowGate.idle(rocket)
                || !io.github.sunthemoon.advancedrocketrycommunity.rocket.server.RocketRuntime.resourceMutationAllowed(rocket)) {
            return refuse(player, RocketCargoStatus.ROCKET_BUSY.translationKey());
        }
        if (rocket.getBoundingBox().distanceToSqr(player.position()) > MAX_ACTOR_DISTANCE * MAX_ACTOR_DISTANCE) {
            return refuse(player, TOO_FAR);
        }
        if (!RocketCargoBindingChecks.withinRange(rocket, selection.pos())) {
            return refuse(player, RocketCargoStatus.OUT_OF_RANGE.translationKey());
        }
        if (RocketCargoBindingChecks.duplicated(level, selection.pos(), rocket)) {
            return refuse(player, RocketCargoStatus.STALE_BINDING.translationKey());
        }
        RocketFlightData flight = rocket.flightData().orElseThrow();
        LoaderBinding binding = new LoaderBinding(flight.logicalRocketId(), level.dimension().location(), rocket.getUUID(),
                RocketCargoBindingChecks.origin(flight), RocketCargoBindingChecks.station(flight));
        if (binding.equals(loader.root().orElseThrow().binding())) {
            return refuse(player, ALREADY_BOUND);
        }
        return loader.bind(binding) ? changed(player, stack, BOUND)
                : refuse(player, RocketCargoStatus.ESCROW_PENDING.translationKey());
    }

}
