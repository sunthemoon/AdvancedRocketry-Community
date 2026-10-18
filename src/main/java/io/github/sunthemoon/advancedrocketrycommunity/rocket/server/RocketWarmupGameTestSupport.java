package io.github.sunthemoon.advancedrocketrycommunity.rocket.server;

import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.persistence.RocketTransferSavedData;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.transaction.RocketRegion;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.transfer.RocketWarmupRegistry;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import net.minecraft.gametest.framework.GameTestServer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.DistanceManager;
import net.minecraft.server.level.Ticket;
import net.minecraft.world.level.ChunkPos;

/**
 * W90b GameTest support: read-only views of the installed session's destination warm-ups and their tickets, and the
 * warm-up part of an in-process runtime clear. It refuses every server except a GameTestServer and is never product
 * authority.
 */
public final class RocketWarmupGameTestSupport {
    private RocketWarmupGameTestSupport() { }

    /** One live warm-up as the registry holds it. */
    public record View(UUID logical, String state, UUID request, UUID transfer, ResourceLocation level, RocketRegion region,
                       long started, int lifetime, List<ChunkPos> columns, UUID player) { }

    public static Optional<View> warmup(MinecraftServer server, UUID logical) {
        return warmups(server).registry().warmup(logical).map(RocketWarmupGameTestSupport::view);
    }

    public static List<View> live(MinecraftServer server) {
        return warmups(server).registry().live().stream().map(RocketWarmupGameTestSupport::view).toList();
    }

    /** prepareLaunch's reservations for a request of logical rocket {@code requester}, as the product computes them. */
    public static List<RocketRegion> launchReservations(MinecraftServer server, RocketTransferSavedData journal, UUID requester) {
        var transfers = transfers(server);
        return transfers.warmups.reservations(transfers.launchReservations(server, journal), requester);
    }

    /** The warm-up part of a runtime clear while the Levels stay loaded: every warm-up is released first (design 7.6). */
    public static void clearWarmups(MinecraftServer server) {
        warmups(server).clear();
    }

    public static boolean warmupTicket(Ticket<?> ticket, UUID logical) {
        var text = ticket.toString();
        return text.contains("arce_rocket_warmup") && text.contains(logical.toString());
    }

    /** {@link RocketMarginGameTestSupport#column} names of every warm-up ticket keyed by the logical rocket. */
    public static Set<String> warmupTicketColumns(MinecraftServer server, UUID logical) {
        requireGameTest(server);
        var found = new TreeSet<String>();
        for (var level : server.getAllLevels()) {
            var distance = level.getChunkSource().chunkMap.getDistanceManager();
            try {
                for (var field : DistanceManager.class.getDeclaredFields()) {
                    if (!it.unimi.dsi.fastutil.longs.Long2ObjectMap.class.isAssignableFrom(field.getType())) { continue; }
                    field.setAccessible(true);
                    var map = (it.unimi.dsi.fastutil.longs.Long2ObjectMap<?>) field.get(distance);
                    for (var entry : map.long2ObjectEntrySet()) {
                        if (!(entry.getValue() instanceof Iterable<?> tickets)) { continue; }
                        for (var ticket : tickets) {
                            if (ticket instanceof Ticket<?> typed && warmupTicket(typed, logical)) {
                                found.add(RocketMarginGameTestSupport.column(level.dimension().location(), new ChunkPos(entry.getLongKey())));
                            }
                        }
                    }
                }
            } catch (ReflectiveOperationException exception) { throw new IllegalStateException("Cannot inspect chunk tickets", exception); }
        }
        return found;
    }

    /** {@link RocketMarginGameTestSupport#column} names of a view's started set. */
    public static Set<String> columns(View view) {
        var names = new TreeSet<String>();
        view.columns().forEach(column -> names.add(RocketMarginGameTestSupport.column(view.level(), column)));
        return names;
    }

    private static View view(RocketWarmupRegistry.Warmup warmup) {
        return new View(warmup.logical(), warmup.state().name(), warmup.request(), warmup.transfer(), warmup.level(),
                warmup.region(), warmup.started(), warmup.lifetime(), warmup.columns(), warmup.player());
    }

    private static RocketDestinationWarmups warmups(MinecraftServer server) {
        return transfers(server).warmups;
    }

    private static RocketTransferService transfers(MinecraftServer server) {
        requireGameTest(server);
        var transfers = RocketRuntime.installedTransfers();
        if (transfers == null) { throw new IllegalStateException("No installed rocket manager"); }
        return transfers;
    }

    private static void requireGameTest(MinecraftServer server) {
        if (!(server instanceof GameTestServer) || !server.isSameThread()) {
            throw new IllegalStateException("The warm-up support requires the GameTestServer thread");
        }
    }
}
