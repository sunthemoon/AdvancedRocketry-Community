package io.github.sunthemoon.advancedrocketrycommunity.station.orbit;

import io.github.sunthemoon.advancedrocketrycommunity.celestial.CelestialIds;
import io.github.sunthemoon.advancedrocketrycommunity.celestial.network.StationSkyContextPacket;
import io.github.sunthemoon.advancedrocketrycommunity.station.model.StationState;
import io.github.sunthemoon.advancedrocketrycommunity.station.persistence.StationRegistrySavedData;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.BiConsumer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;

/**
 * ADR-047 server side: every {@value #PASS_TICKS} ticks, each online player's station sky context is
 * derived from their own server position (one indexed lookup, no chunk access) and sent only when it
 * changed. The last-sent map holds one entry per online player and is server-thread only.
 */
public final class StationSkyContextService {
    public static final int PASS_TICKS = 10;

    private final BiConsumer<ServerPlayer, StationSkyContextPacket> sender;
    private final Map<UUID, Optional<ResourceLocation>> sent = new HashMap<>();

    public StationSkyContextService(BiConsumer<ServerPlayer, StationSkyContextPacket> sender) {
        this.sender = Objects.requireNonNull(sender, "sender");
    }

    /** The context for one player: the orbit body of the committed station region they stand in. */
    public static Optional<ResourceLocation> contextFor(MinecraftServer server, StationRegistrySavedData data,
                                                        ServerPlayer player) {
        ServerLevel level = player.serverLevel();
        if (!data.operational() || !level.dimension().equals(CelestialIds.SPACE_LEVEL)
                || server.getLevel(CelestialIds.SPACE_LEVEL) != level) {
            return Optional.empty();
        }
        return data.findAt(player.getBlockX(), player.getBlockZ()).map(StationState::orbitBody);
    }

    public void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase == TickEvent.Phase.END && event.getServer().getTickCount() % PASS_TICKS == 0) {
            pass(event.getServer());
        }
    }

    /** One pass over the online players; returns the number of messages sent. */
    public int pass(MinecraftServer server) {
        StationRegistrySavedData data = StationRegistrySavedData.get(server);
        int messages = 0;
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            Optional<ResourceLocation> context = contextFor(server, data, player);
            Optional<ResourceLocation> previous = sent.get(player.getUUID());
            // A player with no entry has been sent nothing: the client starts with no context.
            if (previous == null ? context.isPresent() : !previous.equals(context)) {
                sender.accept(player, new StationSkyContextPacket(context));
                messages++;
            }
            sent.put(player.getUUID(), context);
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
}
