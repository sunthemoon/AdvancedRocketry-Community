package io.github.sunthemoon.advancedrocketrycommunity.rocket.fuel.loader;

import io.github.sunthemoon.advancedrocketrycommunity.rocket.entity.RocketEntity;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketFlightData;
import java.util.List;
import java.util.Optional;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraftforge.common.util.FakePlayer;

/**
 * The rocket-binding half of the linker (design section 3). Using it on a loaded loader the player owns selects that
 * device; using it on a rocket then binds the selected device on the server. Sneak-use on a loader clears its idle
 * binding, and sneak-use in the air clears the selection. Every change starts a 10-tick cooldown, so a player makes at
 * most one change per 10 ticks. The other linker roles belong to W11e.
 */
public final class LinkerItem extends Item {
    public static final int CHANGE_TICKS = 10;
    public static final double MAX_ACTOR_DISTANCE = FluidFuelLoaderBlockEntity.MAX_ACTOR_DISTANCE;
    private static final String MESSAGE = "message.advancedrocketrycommunity.linker.";
    public static final String SELECTED = MESSAGE + "selected";
    public static final String CLEARED = MESSAGE + "cleared";
    public static final String BOUND = MESSAGE + "bound";
    public static final String UNBOUND = MESSAGE + "unbound";
    public static final String NO_SELECTION = MESSAGE + "no_selection";
    public static final String ALREADY_BOUND = MESSAGE + "already_bound";
    public static final String NOT_BOUND = MESSAGE + "not_bound";
    public static final String TOO_FAR = MESSAGE + "too_far";
    private static final String TOOLTIP = "tooltip.advancedrocketrycommunity.linker.";
    public static final String TOOLTIP_SELECTION = TOOLTIP + "selection";
    public static final String TOOLTIP_NONE = TOOLTIP + "none";
    public static final String TOOLTIP_INVALID = TOOLTIP + "invalid";

    public LinkerItem(Properties properties) {
        super(properties);
    }

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
        return player instanceof ServerPlayer server && !(player instanceof FakePlayer) && !server.hasDisconnected()
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
    @Override
    public InteractionResult useOn(UseOnContext context) {
        Level level = context.getLevel();
        BlockPos position = context.getClickedPos();
        if (level.isClientSide) {
            return level.getBlockState(position).getBlock() instanceof FluidFuelLoaderBlock
                    ? InteractionResult.SUCCESS : InteractionResult.PASS;
        }
        Player player = context.getPlayer();
        InteractionHand hand = context.getHand();
        if (player == null || !actor(player, hand) || !level.isLoaded(position)
                || !(level.getBlockEntity(position) instanceof FluidFuelLoaderBlockEntity loader)) {
            return InteractionResult.PASS;
        }
        ItemStack stack = player.getItemInHand(hand);
        if (player.getCooldowns().isOnCooldown(this)) {
            return InteractionResult.FAIL;
        }
        FluidFuelLoaderStatus refusal = ownerRefusal(player, loader);
        if (refusal != null) {
            return refusal == FluidFuelLoaderStatus.OUT_OF_RANGE ? refuse(player, TOO_FAR) : refuse(player, refusal.translationKey());
        }
        if (player.isSecondaryUseActive()) {
            if (loader.root().orElseThrow().binding() == null) {
                return refuse(player, NOT_BOUND);
            }
            return loader.clearBinding() ? changed(player, stack, UNBOUND)
                    : refuse(player, FluidFuelLoaderStatus.ESCROW_PENDING.translationKey());
        }
        LinkerSelection selected = new LinkerSelection(loader.root().orElseThrow().device(),
                level.dimension().location(), position);
        if (selection(stack).filter(selected::equals).isPresent()) {
            return InteractionResult.FAIL;
        }
        stack.getOrCreateTag().put(LinkerSelection.KEY, selected.encode());
        return changed(player, stack, SELECTED);
    }

    /** Sneak-use in the air clears any selection, valid or inert. */
    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!player.isSecondaryUseActive() || !stack.hasTag() || !stack.getTag().contains(LinkerSelection.KEY)) {
            return InteractionResultHolder.pass(stack);
        }
        if (level.isClientSide) {
            return InteractionResultHolder.success(stack);
        }
        if (!actor(player, hand) || player.getCooldowns().isOnCooldown(this)) {
            return InteractionResultHolder.fail(stack);
        }
        stack.removeTagKey(LinkerSelection.KEY);
        changed(player, stack, CLEARED);
        return InteractionResultHolder.consume(stack);
    }

    /**
     * Why this player may not change this device: unsupported or duplicate data, an unclaimed or foreign device
     * (operators included), escrow, or distance (OUT_OF_RANGE here means the actor is too far). Null when allowed.
     */
    @Nullable
    static FluidFuelLoaderStatus ownerRefusal(Player player, FluidFuelLoaderBlockEntity loader) {
        FluidFuelLoaderStatus refusal = loader.deviceRefusal();
        if (refusal == FluidFuelLoaderStatus.INVALID_DATA || refusal == FluidFuelLoaderStatus.UNSUPPORTED_DATA
                || refusal == FluidFuelLoaderStatus.NOT_OWNER) {
            return refusal;
        }
        if (!loader.owner().filter(player.getUUID()::equals).isPresent()) {
            return FluidFuelLoaderStatus.NOT_OWNER;
        }
        if (!loader.admitted(player)) {
            return FluidFuelLoaderStatus.OUT_OF_RANGE;
        }
        return refusal == FluidFuelLoaderStatus.ESCROW_PENDING ? refusal : null;
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
            return refuse(player, FluidFuelLoaderStatus.STALE_BINDING.translationKey());
        }
        // The selected chunk must already be loaded before anything is looked up there.
        if (level.getChunkSource().getChunkNow(selection.pos().getX() >> 4, selection.pos().getZ() >> 4) == null) {
            return refuse(player, FluidFuelLoaderStatus.WAITING_UNLOADED.translationKey());
        }
        if (!(level.getBlockEntity(selection.pos()) instanceof FluidFuelLoaderBlockEntity loader)
                || !loader.root().map(LoaderRoot::device).filter(selection.device()::equals).isPresent()) {
            return refuse(player, FluidFuelLoaderStatus.STALE_BINDING.translationKey());
        }
        FluidFuelLoaderStatus refusal = ownerRefusal(player, loader);
        if (refusal != null) {
            return refusal == FluidFuelLoaderStatus.OUT_OF_RANGE ? refuse(player, TOO_FAR) : refuse(player, refusal.translationKey());
        }
        if (!rocket.operational() || !LoaderEligibility.loaded(level, rocket.blockPosition())) {
            return refuse(player, FluidFuelLoaderStatus.STALE_BINDING.translationKey());
        }
        if (!rocket.ownerId().filter(player.getUUID()::equals).isPresent()) {
            return refuse(player, FluidFuelLoaderStatus.NOT_OWNER.translationKey());
        }
        if (!LoaderEligibility.stationary(rocket)) {
            return refuse(player, FluidFuelLoaderStatus.ROCKET_BUSY.translationKey());
        }
        if (rocket.getBoundingBox().distanceToSqr(player.position()) > MAX_ACTOR_DISTANCE * MAX_ACTOR_DISTANCE) {
            return refuse(player, TOO_FAR);
        }
        if (!LoaderEligibility.withinRange(rocket, selection.pos())) {
            return refuse(player, FluidFuelLoaderStatus.OUT_OF_RANGE.translationKey());
        }
        if (LoaderEligibility.duplicated(level, selection.pos(), rocket)) {
            return refuse(player, FluidFuelLoaderStatus.STALE_BINDING.translationKey());
        }
        RocketFlightData flight = rocket.flightData().orElseThrow();
        LoaderBinding binding = new LoaderBinding(flight.logicalRocketId(), level.dimension().location(), rocket.getUUID(),
                LoaderEligibility.origin(flight), LoaderEligibility.station(flight));
        if (binding.equals(loader.root().orElseThrow().binding())) {
            return refuse(player, ALREADY_BOUND);
        }
        return loader.bind(binding) ? changed(player, stack, BOUND)
                : refuse(player, FluidFuelLoaderStatus.ESCROW_PENDING.translationKey());
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> lines, TooltipFlag flag) {
        super.appendHoverText(stack, level, lines, flag);
        if (!stack.hasTag() || !stack.getTag().contains(LinkerSelection.KEY)) {
            lines.add(Component.translatable(TOOLTIP_NONE));
            return;
        }
        selection(stack).ifPresentOrElse(selected -> lines.add(Component.translatable(TOOLTIP_SELECTION,
                        selected.pos().getX(), selected.pos().getY(), selected.pos().getZ())),
                () -> lines.add(Component.translatable(TOOLTIP_INVALID)));
    }
}
