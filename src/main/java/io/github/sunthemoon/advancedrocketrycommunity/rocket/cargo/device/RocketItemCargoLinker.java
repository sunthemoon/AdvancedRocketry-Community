package io.github.sunthemoon.advancedrocketrycommunity.rocket.cargo.device;

import static io.github.sunthemoon.advancedrocketrycommunity.rocket.fuel.loader.LinkerItem.*;

import io.github.sunthemoon.advancedrocketrycommunity.resourceescrow.RocketEscrowGate;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.entity.RocketEntity;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketFlightData;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.fuel.loader.LinkerSelection;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.fuel.loader.LoaderBinding;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.server.RocketRuntime;
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

/**
 * The linker roles for the item devices, the same as C-A's for the fluid devices (W11B3's binding and selection codecs):
 * using it on a loaded device the player owns selects it; using it on a rocket then binds the selected device on the
 * server; sneak-use on a device clears its idle binding. Every change starts the linker's 10-tick cooldown.
 */
public final class RocketItemCargoLinker {
    private static final double MAX_ACTOR_DISTANCE = RocketItemCargoBlockEntity.MAX_ACTOR_DISTANCE;
    private RocketItemCargoLinker() { }

    private static InteractionResult refuse(Player player, String key) {
        player.displayClientMessage(Component.translatable(key), true);
        return InteractionResult.FAIL;
    }

    private static InteractionResult changed(Player player, ItemStack stack, String key) {
        player.getCooldowns().addCooldown(stack.getItem(), CHANGE_TICKS);
        player.displayClientMessage(Component.translatable(key), true);
        return InteractionResult.CONSUME;
    }

    /** Select a device, or with sneak clear its binding. */
    public static InteractionResult select(UseOnContext context) {
        Level level = context.getLevel();
        BlockPos position = context.getClickedPos();
        if (level.isClientSide) {
            return level.getBlockState(position).getBlock() instanceof RocketItemCargoBlock
                    ? InteractionResult.SUCCESS : InteractionResult.PASS;
        }
        Player player = context.getPlayer();
        InteractionHand hand = context.getHand();
        if (player == null || !RocketFluidCargoLinker.actor(player, hand) || !level.isLoaded(position)
                || !(level.getBlockEntity(position) instanceof RocketItemCargoBlockEntity device)) {
            return InteractionResult.PASS;
        }
        ItemStack stack = player.getItemInHand(hand);
        if (player.getCooldowns().isOnCooldown(stack.getItem())) {
            return InteractionResult.FAIL;
        }
        RocketCargoStatus refusal = ownerRefusal(player, device);
        if (refusal != null) {
            return refusal == RocketCargoStatus.OUT_OF_RANGE ? refuse(player, TOO_FAR) : refuse(player, refusal.translationKey());
        }
        if (player.isSecondaryUseActive()) {
            if (device.root().orElseThrow().binding() == null) {
                return refuse(player, NOT_BOUND);
            }
            return device.clearBinding() ? changed(player, stack, UNBOUND)
                    : refuse(player, RocketCargoStatus.ESCROW_PENDING.translationKey());
        }
        LinkerSelection selected = new LinkerSelection(device.root().orElseThrow().device(),
                level.dimension().location(), position);
        if (RocketFluidCargoLinker.selection(stack).filter(selected::equals).isPresent()) {
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
    static RocketCargoStatus ownerRefusal(Player player, RocketItemCargoBlockEntity device) {
        RocketCargoStatus refusal = device.deviceRefusal();
        if (refusal == RocketCargoStatus.INVALID_DATA || refusal == RocketCargoStatus.UNSUPPORTED_DATA
                || refusal == RocketCargoStatus.NOT_OWNER) {
            return refusal;
        }
        if (!device.owner().filter(player.getUUID()::equals).isPresent()) {
            return RocketCargoStatus.NOT_OWNER;
        }
        if (!device.admitted(player)) {
            return RocketCargoStatus.OUT_OF_RANGE;
        }
        // A held device shows its progress elsewhere; a rebinding or clearing is refused as locked by escrow.
        return device.escrowLocked() ? RocketCargoStatus.ESCROW_PENDING : null;
    }

    /**
     * The server half of the binding: the held selection names a loaded device that this player owns, and the rocket
     * the player used is stationary, theirs and within 6 blocks of it. The entity's integer ID is never authority;
     * this is the entity the server resolved for the interaction.
     */
    public static InteractionResult bindRocket(ServerPlayer player, InteractionHand hand, RocketEntity rocket) {
        if (!RocketFluidCargoLinker.actor(player, hand) || rocket.level() != player.level() || rocket.isRemoved()) {
            return InteractionResult.FAIL;
        }
        ItemStack stack = player.getItemInHand(hand);
        if (player.getCooldowns().isOnCooldown(stack.getItem())) {
            return InteractionResult.FAIL;
        }
        Optional<LinkerSelection> held = RocketFluidCargoLinker.selection(stack);
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
        if (!(level.getBlockEntity(selection.pos()) instanceof RocketItemCargoBlockEntity device)
                || !device.root().map(RocketItemCargoRoot::device).filter(selection.device()::equals).isPresent()) {
            return refuse(player, RocketCargoStatus.STALE_BINDING.translationKey());
        }
        RocketCargoStatus refusal = ownerRefusal(player, device);
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
                || !RocketEscrowGate.idle(rocket) || !RocketRuntime.resourceMutationAllowed(rocket)) {
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
        if (binding.equals(device.root().orElseThrow().binding())) {
            return refuse(player, ALREADY_BOUND);
        }
        return device.bind(binding) ? changed(player, stack, BOUND)
                : refuse(player, RocketCargoStatus.ESCROW_PENDING.translationKey());
    }
}
