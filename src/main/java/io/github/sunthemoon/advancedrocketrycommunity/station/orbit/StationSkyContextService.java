package io.github.sunthemoon.advancedrocketrycommunity.station.orbit;

import io.github.sunthemoon.advancedrocketrycommunity.celestial.CelestialIds;
import io.github.sunthemoon.advancedrocketrycommunity.celestial.network.StationSkyContextPacket;
import io.github.sunthemoon.advancedrocketrycommunity.celestial.network.StationSkyContextPacket.Context;
import io.github.sunthemoon.advancedrocketrycommunity.celestial.network.StationSkyContextPacket.SkyKind;
import io.github.sunthemoon.advancedrocketrycommunity.celestial.service.CelestialCatalog;
import io.github.sunthemoon.advancedrocketrycommunity.station.model.StationState;
import io.github.sunthemoon.advancedrocketrycommunity.station.persistence.StationRegistrySavedData;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.BiConsumer;
import java.util.function.BiPredicate;
import java.util.function.Function;
import java.util.function.Supplier;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;

/**
 * ADR-047 server side, with the ADR-065 payload schema 2: every {@value #PASS_TICKS} ticks, each online
 * player's station sky context is derived from their own server position (one indexed lookup, no chunk access)
 * and sent when it changed. While its station rotates, the same context is re-sent with a fresh sample at most
 * once per {@value #HEARTBEAT_TICKS} ticks per player. The last-sent map holds one entry per online player and is
 * server-thread only.
 */
public final class StationSkyContextService {
    public static final int PASS_TICKS = 10;
    /** ADR-065 section 5.2: the heartbeat of a rotating station's context, at most once per this many ticks. */
    public static final int HEARTBEAT_TICKS = 20;

    private final BiConsumer<ServerPlayer, StationSkyContextPacket> sender;
    private final Function<ResourceLocation, SkyKind> kinds;
    private final Map<UUID, Sent> sent = new HashMap<>();

    /** A service whose contexts name the station's body as a {@link SkyKind#SPACE_FALLBACK} sky (tests). */
    public StationSkyContextService(BiConsumer<ServerPlayer, StationSkyContextPacket> sender) {
        this(sender, body -> SkyKind.SPACE_FALLBACK);
    }

    /** {@code kinds} chooses the sky kind of an orbit body on the server; see {@link #kinds}. */
    public StationSkyContextService(BiConsumer<ServerPlayer, StationSkyContextPacket> sender,
                                    Function<ResourceLocation, SkyKind> kinds) {
        this.sender = Objects.requireNonNull(sender, "sender");
        this.kinds = Objects.requireNonNull(kinds, "kinds");
    }

    /**
     * The server's sky kind rule: a body of the current catalog is PLANETARY, or SINGULARITY when the accepted
     * ADR-057 singularity profile applies to it; anything else is SPACE_FALLBACK (which keeps the body ID).
     */
    public static Function<ResourceLocation, SkyKind> kinds(Supplier<Optional<CelestialCatalog>> catalogs,
                                                           BiPredicate<ResourceLocation, CelestialCatalog> singularity) {
        Objects.requireNonNull(catalogs, "catalogs");
        Objects.requireNonNull(singularity, "singularity");
        return body -> catalogs.get()
                .filter(catalog -> catalog.get(body).isPresent())
                .map(catalog -> singularity.test(body, catalog) ? SkyKind.SINGULARITY : SkyKind.PLANETARY)
                .orElse(SkyKind.SPACE_FALLBACK);
    }

    /** The station a player stands in, if it gives them a sky context. */
    public static Optional<StationState> stationFor(MinecraftServer server, StationRegistrySavedData data,
                                                    ServerPlayer player) {
        ServerLevel level = player.serverLevel();
        boolean inSpace = level.dimension().equals(CelestialIds.SPACE_LEVEL)
                && server.getLevel(CelestialIds.SPACE_LEVEL) == level;
        return stationFor(data, inSpace, player.getBlockX(), player.getBlockZ());
    }

    /**
     * The same rule without a server: none outside Space, while the registry is blocked, and while it is
     * quarantined after an unknown write (ADR-065 section 5.2.1 sends NONE until restart).
     */
    public static Optional<StationState> stationFor(StationRegistrySavedData data, boolean inSpace, int x, int z) {
        if (!inSpace || !data.updatesAvailable()) {
            return Optional.empty();
        }
        return data.findAt(x, z);
    }

    /** The orbit body of {@link #stationFor}; kept for the ADR-047 callers. */
    public static Optional<ResourceLocation> contextFor(StationRegistrySavedData data, boolean inSpace, int x, int z) {
        return stationFor(data, inSpace, x, z).map(StationState::orbitBody);
    }

    /** The context for one player, sampled at the registry's current logical tick. */
    public Optional<Context> contextFor(MinecraftServer server, StationRegistrySavedData data, ServerPlayer player) {
        return stationFor(server, data, player).map(station -> new Context(kinds.apply(station.orbitBody()),
                Optional.of(station.orbitBody()), station.orbitalControls(), data.orbitalTick()));
    }

    public void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase == TickEvent.Phase.END && event.getServer().getTickCount() % PASS_TICKS == 0) {
            pass(event.getServer());
        }
    }

    /**
     * One pass over the online players; returns the number of messages sent. A context is sent when its basis
     * (kind, body, controls and epoch) changed, and re-sent as a heartbeat while it rotates and at least
     * {@value #HEARTBEAT_TICKS} ticks have passed since the last message to that player. Nothing in the loop can
     * throw for a valid registry, so there is no per-player isolation (final review B3, rev-1 F8).
     */
    public int pass(MinecraftServer server) {
        StationRegistrySavedData data = StationRegistrySavedData.get(server);
        long now = server.getTickCount();
        int messages = 0;
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            Optional<Context> context = contextFor(server, data, player);
            Optional<Context> basis = context.map(Context::basis);
            Sent previous = sent.get(player.getUUID());
            // A player with no entry has been sent nothing: the client starts with no context.
            boolean changed = previous == null ? context.isPresent() : !previous.basis().equals(basis);
            boolean heartbeat = !changed && previous != null && context.isPresent()
                    && context.orElseThrow().controls().rotating()
                    && (now < previous.tick() || now - previous.tick() >= HEARTBEAT_TICKS);
            if (changed || heartbeat) {
                sender.accept(player, new StationSkyContextPacket(context));
                sent.put(player.getUUID(), new Sent(basis, now));
                messages++;
            } else if (previous == null) {
                sent.put(player.getUUID(), new Sent(basis, now));
            }
        }
        sent.keySet().removeIf(playerId -> server.getPlayerList().getPlayer(playerId) == null);
        return messages;
    }

    /** The client clears its context on a Level change, so the next pass sends the current one. */
    public void onPlayerChangedDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        sent.remove(event.getEntity().getUUID());
    }

    public void onPlayerRespawn(PlayerEvent.PlayerRespawnEvent event) {
        sent.remove(event.getEntity().getUUID());
    }

    public void onPlayerLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        sent.remove(event.getEntity().getUUID());
    }

    public void onServerStopping(ServerStoppingEvent event) {
        sent.clear();
    }

    public int trackedPlayers() {
        return sent.size();
    }

    /** Whether the player has an entry, i.e. a pass has run since their login, respawn or Level change. */
    public boolean tracked(UUID playerId) {
        return sent.containsKey(playerId);
    }

    /** The body of the last context sent to (or recorded for) the player; empty when untracked or "none". */
    public Optional<ResourceLocation> lastSent(UUID playerId) {
        Sent last = sent.get(playerId);
        return last == null ? Optional.empty() : last.basis().flatMap(Context::body);
    }

    /** The server tick of the last message to (or record for) the player; empty when untracked. */
    public Optional<Long> lastSentTick(UUID playerId) {
        return Optional.ofNullable(sent.get(playerId)).map(Sent::tick);
    }

    private record Sent(Optional<Context> basis, long tick) {
    }
}
