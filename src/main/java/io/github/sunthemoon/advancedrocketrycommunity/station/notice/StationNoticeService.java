package io.github.sunthemoon.advancedrocketrycommunity.station.notice;

import io.github.sunthemoon.advancedrocketrycommunity.AdvancedRocketryCommunity;
import io.github.sunthemoon.advancedrocketrycommunity.celestial.CelestialIds;
import io.github.sunthemoon.advancedrocketrycommunity.station.model.StationState;
import io.github.sunthemoon.advancedrocketrycommunity.station.persistence.StationRegistrySavedData;
import io.github.sunthemoon.advancedrocketrycommunity.station.service.StationAccessAction;
import io.github.sunthemoon.advancedrocketrycommunity.station.service.StationAccessService;
import io.github.sunthemoon.advancedrocketrycommunity.station.service.StationLifecycleListener;
import io.github.sunthemoon.advancedrocketrycommunity.station.service.StationManagementCode;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStartedEvent;

/**
 * Sends the ADR-065 section 5.3 warp notices and keeps the offline member summaries. Recipients come from the live
 * registry each time: the online owner and members, the acting player while they still hold VISIT access, and players
 * in Space inside the live region, each once. Players with VISIT access get the full notice and other visitors its
 * {@link StationNotice#forVisitors} form. A commit or abort replaces the station's summary; an owner or member who was
 * offline sees it once at the next login. This service holds no state: the summaries live in
 * {@link StationNoticeSavedData}.
 */
public final class StationNoticeService implements StationLifecycleListener {
    private static final StationAccessService ACCESS = new StationAccessService();

    /** A live notice to its recipients, in chat or on the action bar; returns the players told. */
    public Set<UUID> deliver(MinecraftServer server, UUID stationId, UUID actorId, StationNotice notice,
                             boolean actionBar) {
        Objects.requireNonNull(notice, "notice");
        Component full = component(notice);
        Component visitor = component(notice.forVisitors());
        Set<UUID> told = new LinkedHashSet<>();
        for (Recipient recipient : recipients(server, stationId, actorId)) {
            recipient.player().displayClientMessage(recipient.authorized() ? full : visitor, actionBar);
            told.add(recipient.player().getUUID());
        }
        return told;
    }

    /**
     * A commit or abort: sent live in chat, then kept as the station's summary. The owner and members told live are
     * acknowledged at once. A station that no longer exists, or an unavailable store, keeps nothing.
     */
    public void deliverOutcome(MinecraftServer server, UUID stationId, UUID actorId, StationNotice outcome) {
        Set<UUID> told = deliver(server, stationId, actorId, outcome, false);
        StationRegistrySavedData registry = StationRegistrySavedData.get(server);
        Optional<StationState> live = registry.find(stationId);
        if (live.isEmpty()) {
            return;
        }
        StationNoticeSavedData.get(server).record(live.get(), outcome, told, id -> registry.find(id).isPresent())
                .filter(admission -> admission != StationNoticeBook.Admission.ADMITTED)
                .ifPresent(admission -> AdvancedRocketryCommunity.LOGGER.warn(
                        "ARCE_STATION_NOTICE_NOT_KEPT station={} admission={}", stationId, admission));
    }

    public void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player && player.getServer() != null) {
            showPending(player);
        }
    }

    /**
     * At login: each live station whose owner or member the player is now shows its unacknowledged outcome once, at
     * most {@link StationNoticeLimits#MAX_LOGIN_NOTICES}, and records the acknowledgement. Returns how many were shown.
     */
    public int showPending(ServerPlayer player) {
        MinecraftServer server = Objects.requireNonNull(player.getServer(), "server");
        StationRegistrySavedData registry = StationRegistrySavedData.get(server);
        StationNoticeSavedData notices = StationNoticeSavedData.get(server);
        if (!registry.operational() || !notices.operational()) {
            return 0;
        }
        UUID playerId = player.getUUID();
        List<StationState> stations = registry.stations().stream()
                .filter(station -> station.ownerId().equals(playerId) || station.members().contains(playerId))
                .sorted(Comparator.comparing(station -> station.stationId().toString()))
                .toList();
        int shown = 0;
        for (StationState station : stations) {
            if (shown >= StationNoticeLimits.MAX_LOGIN_NOTICES) {
                break;
            }
            Optional<StationNotice> pending = notices.pending(station, playerId);
            if (pending.isPresent()) {
                player.displayClientMessage(Component.translatable(StationNoticeKind.WHILE_AWAY,
                        component(pending.get())), false);
                notices.acknowledge(station, playerId);
                shown++;
            }
        }
        return shown;
    }

    /**
     * At start, with both stores available: drops summaries of stations that no longer exist and acknowledgements of
     * former owners and members, and gives each station without a summary the empty one.
     */
    public void onServerStarted(ServerStartedEvent event) {
        reconcileAll(event.getServer());
    }

    public void reconcileAll(MinecraftServer server) {
        StationRegistrySavedData registry = StationRegistrySavedData.get(server);
        StationNoticeSavedData notices = StationNoticeSavedData.get(server);
        if (!registry.operational() || !notices.operational()) {
            return;
        }
        List<StationState> stations = registry.stations();
        Set<UUID> live = stations.stream().map(StationState::stationId).collect(Collectors.toSet());
        notices.retain(live::contains);
        for (StationState station : stations) {
            notices.reconcile(station);
            notices.add(station.stationId(), live::contains);
        }
    }

    @Override
    public void created(MinecraftServer server, StationState station) {
        StationRegistrySavedData registry = StationRegistrySavedData.get(server);
        if (registry.operational()) {
            StationNoticeSavedData.get(server).add(station.stationId(), id -> registry.find(id).isPresent());
        }
    }

    @Override
    public void teamChanged(MinecraftServer server, StationState station) {
        StationNoticeSavedData.get(server).reconcile(station);
    }

    @Override
    public void deleted(MinecraftServer server, UUID stationId) {
        StationNoticeSavedData.get(server).remove(stationId);
    }

    /** The translatable component: text, bodies and numbers literal, codes and cost classes through their keys. */
    public static MutableComponent component(StationNotice notice) {
        List<StationNoticeKind.Role> roles = notice.kind().roles();
        Object[] arguments = new Object[roles.size()];
        for (int index = 0; index < arguments.length; index++) {
            String value = notice.arguments().get(index);
            arguments[index] = switch (roles.get(index)) {
                case CODE -> Component.translatable(StationManagementCode.valueOf(value).translationKey());
                case COST_CLASS -> Component.translatable(StationNoticeKind.costClassKey(value));
                case TEXT, BODY, NUMBER -> Component.literal(value);
            };
        }
        return Component.translatable(notice.kind().key(), arguments);
    }

    /**
     * The live owner and members who are online, the actor when online and still holding VISIT access (W22 review F3:
     * the same check as every other recipient), and players in Space inside the live region, each with whether they
     * hold VISIT access now. Without a live station (deleted, or an unavailable registry) only an actor who is an
     * operator now is told.
     */
    private static Collection<Recipient> recipients(MinecraftServer server, UUID stationId, UUID actorId) {
        Map<UUID, Recipient> recipients = new LinkedHashMap<>();
        Optional<StationState> live = StationRegistrySavedData.get(server).find(stationId);
        live.ifPresent(station -> {
            addOnline(server, recipients, station.ownerId());
            station.sortedMembers().forEach(member -> addOnline(server, recipients, member));
        });
        ServerPlayer actor = server.getPlayerList().getPlayer(actorId);
        if (actor != null && authorized(live, actor)) {
            recipients.putIfAbsent(actorId, new Recipient(actor, true));
        }
        ServerLevel space = server.getLevel(CelestialIds.SPACE_LEVEL);
        if (live.isPresent() && space != null) {
            for (ServerPlayer player : space.players()) {
                if (live.get().region().contains(player.getBlockX(), player.getBlockZ())) {
                    recipients.putIfAbsent(player.getUUID(), new Recipient(player, authorized(live, player)));
                }
            }
        }
        return recipients.values();
    }

    private static void addOnline(MinecraftServer server, Map<UUID, Recipient> recipients, UUID playerId) {
        ServerPlayer online = server.getPlayerList().getPlayer(playerId);
        if (online != null) {
            recipients.putIfAbsent(playerId, new Recipient(online, true));
        }
    }

    /**
     * VISIT access now, as {@code /arce station warp status} checks it: the live owner, a live member or an operator.
     * Without a live station only an operator holds it.
     */
    private static boolean authorized(Optional<StationState> live, ServerPlayer player) {
        boolean operator = player.hasPermissions(2);
        return live.map(station -> ACCESS.allowed(station, player.getUUID(), operator, StationAccessAction.VISIT))
                .orElse(operator);
    }

    /** One player told once; {@code authorized} players get the full notice, other visitors its visitor form. */
    private record Recipient(ServerPlayer player, boolean authorized) {
    }
}
