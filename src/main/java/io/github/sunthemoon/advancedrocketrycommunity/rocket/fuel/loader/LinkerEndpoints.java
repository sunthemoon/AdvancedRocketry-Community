package io.github.sunthemoon.advancedrocketrycommunity.rocket.fuel.loader;

import io.github.sunthemoon.advancedrocketrycommunity.rocket.entity.RocketEntity;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketTransferPhase;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.persistence.RocketTransferSavedData;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.monitor.RocketMonitorBinding;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.monitor.RocketMonitorBlockEntity;
import io.github.sunthemoon.advancedrocketrycommunity.station.pad.LandingPadBlockEntity;
import io.github.sunthemoon.advancedrocketrycommunity.station.pad.LandingPadService;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.UseOnContext;

/** Missing linker roles; every actual binding still names a logical rocket, never a nearest entity or a future occupant. */
final class LinkerEndpoints {
    private LinkerEndpoints() { }
    static InteractionResult select(UseOnContext context, RocketMonitorBlockEntity monitor) {
        Player player = context.getPlayer();
        if (player == null || !LinkerItem.actor(player, context.getHand()) || !monitor.admitted(player)
                || player.getCooldowns().isOnCooldown(context.getItemInHand().getItem())) { return InteractionResult.FAIL; }
        if (player.isSecondaryUseActive()) {
            if (monitor.root().binding() == null || !monitor.bind(null)) { return InteractionResult.FAIL; }
            return changed(player, context.getHand(), LinkerItem.UNBOUND);
        }
        var selected = new LinkerSelection(monitor.root().device(), context.getLevel().dimension().location(), monitor.getBlockPos());
        if (LinkerItem.selection(context.getItemInHand()).filter(selected::equals).isPresent()) { return InteractionResult.FAIL; }
        context.getItemInHand().getOrCreateTag().put(LinkerSelection.KEY, selected.encode());
        return changed(player, context.getHand(), LinkerItem.SELECTED);
    }
    static InteractionResult bind(ServerPlayer player, InteractionHand hand, RocketEntity rocket,
                                  LinkerSelection selected, RocketMonitorBlockEntity monitor) {
        if (!monitor.admitted(player) || !monitor.root().device().equals(selected.device())
                || !rocket.operational() || !rocket.ownerId().filter(monitor.root().owner()::equals).isPresent()
                || !RocketMonitorBinding.loaded(player.serverLevel(), rocket.blockPosition())
                || !RocketMonitorBinding.docked(rocket, monitor.getBlockPos())
                || rocket.getBoundingBox().distanceToSqr(player.position()) > 64) { return refuse(player); }
        var binding = RocketMonitorBinding.capture(rocket);
        if (binding.equals(monitor.root().binding()) || !monitor.bind(binding)) { return InteractionResult.FAIL; }
        return changed(player, hand, LinkerItem.BOUND);
    }

    /** A bounded journal lookup of the clicked pad, not a world/entity search. Empty and ambiguous pads refuse. */
    static InteractionResult pad(UseOnContext context, LandingPadBlockEntity pad) {
        if (!(context.getPlayer() instanceof ServerPlayer player) || !LinkerItem.actor(player, context.getHand())
                || player.getCooldowns().isOnCooldown(context.getItemInHand().getItem())
                || player.distanceToSqr(context.getClickedPos().getX() + .5, context.getClickedPos().getY() + .5,
                        context.getClickedPos().getZ() + .5) > 64) { return InteractionResult.FAIL; }
        ServerLevel level = player.serverLevel();
        if (!RocketMonitorBinding.loaded(level, pad.getBlockPos()) || !pad.operational() || pad.reference() == null) { return refuse(player); }
        try { LandingPadService.localAuthority(level, pad.getBlockPos(), player.getUUID(), player.hasPermissions(2)); }
        catch (IllegalArgumentException denied) { return refuse(player); }
        var entry = LandingPadService.resolve(level.getServer(), pad.stationId(), pad.reference());
        var journal = RocketTransferSavedData.get(level.getServer());
        if (entry == null || !journal.operational()) { return refuse(player); }
        RocketEntity found = null;
        for (var record : journal.entries()) {
            var plan = record.sourceFlightData().plan().orElse(null);
            if (plan == null || !entry.reference().equals(plan.landingPad()) || !entry.stationId().equals(plan.destinationStationId())
                    || record.phase() != RocketTransferPhase.COMMITTED || record.destinationEntityId().isEmpty()) { continue; }
            var entity = level.getEntity(record.destinationEntityId().orElseThrow());
            if (!(entity instanceof RocketEntity rocket) || !rocket.operational()
                    || !rocket.flightData().orElseThrow().logicalRocketId().equals(record.logicalRocketId())
                    || !RocketMonitorBinding.docked(rocket, pad.getBlockPos())) { continue; }
            if (found != null && found != rocket) { return refuse(player); }
            found = rocket;
        }
        return found == null ? refuse(player) : LinkerItem.bindRocket(player, context.getHand(), found);
    }
    private static InteractionResult refuse(Player player) {
        player.displayClientMessage(Component.translatable("monitor.advancedrocketrycommunity.link_refused"), true);
        return InteractionResult.FAIL;
    }
    private static InteractionResult changed(Player player, InteractionHand hand, String key) {
        player.getCooldowns().addCooldown(player.getItemInHand(hand).getItem(), LinkerItem.CHANGE_TICKS);
        player.displayClientMessage(Component.translatable(key), true); return InteractionResult.CONSUME;
    }
}
