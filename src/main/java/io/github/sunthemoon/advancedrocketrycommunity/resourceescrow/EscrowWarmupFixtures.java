package io.github.sunthemoon.advancedrocketrycommunity.resourceescrow;

import io.github.sunthemoon.advancedrocketrycommunity.rocket.entity.RocketEntity;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketFlightRequestCode;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.persistence.RocketTransferSavedData;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.model.RocketStructureSnapshot;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.server.RocketMarginGameTestSupport;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.server.RocketWarmupGameTestSupport;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.transaction.RocketRegion;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.transfer.RocketLandingPadSelector;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.TicketType;
import net.minecraft.world.level.ChunkPos;

/** W90b GameTest fixtures shared by the destination warm-up tests: governed rockets, a connected player, pad predictions. */
final class EscrowWarmupFixtures {
    /** Test-only source support: there is no source warm-up (R22 answer 4). */
    static final TicketType<UUID> FIXTURE = TicketType.create("arce_w90b_warmup_fixture", UUID::compareTo);
    static final String LOADING = RocketFlightRequestCode.DESTINATION_LOADING.translationKey();
    static final String LIMIT = RocketFlightRequestCode.DESTINATION_LOADING_LIMIT.translationKey();
    static final String PENDING = RocketFlightRequestCode.JOURNAL_PENDING.translationKey();
    static final String REPLAYED = RocketFlightRequestCode.REQUEST_REPLAYED.translationKey();
    static final String READY = "message.advancedrocketrycommunity.flight.destination_ready";
    private static final int[][] SINGLE = {{0, 0}};

    private EscrowWarmupFixtures() { }

    /** A fueled, ledger-bound rocket of one two-block column, not yet added; its margin is one chunk column at x, z = 8. */
    static RocketEntity governed(ServerLevel level, BlockPos origin, UUID ledger) {
        return EscrowGovernedMarginGameTests.rocket(level, origin, ledger, SINGLE);
    }

    static boolean columnReady(ServerLevel level, BlockPos position) {
        return EscrowGovernedMarginGameTests.ready(level, List.of(new ChunkPos(position)), position.getY());
    }

    static UUID logical(RocketEntity rocket) {
        return rocket.assemblyTransactionId().orElseThrow();
    }

    static RocketRegion region(RocketStructureSnapshot snapshot) {
        return RocketRegion.fromSnapshot(snapshot);
    }

    static RocketWarmupGameTestSupport.View warmup(MinecraftServer server, RocketEntity rocket) {
        return RocketWarmupGameTestSupport.warmup(server, logical(rocket)).orElse(null);
    }

    static Set<String> tickets(MinecraftServer server, RocketEntity rocket) {
        return RocketWarmupGameTestSupport.warmupTicketColumns(server, logical(rocket));
    }

    /** Not ready: the census's readiness predicate on the destination is false. */
    static boolean cold(MinecraftServer server, RocketStructureSnapshot destination) {
        return !RocketMarginGameTestSupport.endReady(server, destination);
    }

    /** The product's next {@code count} surface picks for this rocket's shape, as consecutive requests would reserve them. */
    static List<RocketStructureSnapshot> predict(MinecraftServer server, ServerLevel level, RocketEntity rocket, int count) {
        var reservations = new ArrayList<>(RocketWarmupGameTestSupport.launchReservations(server,
                RocketTransferSavedData.get(server), logical(rocket)));
        var picks = new ArrayList<RocketStructureSnapshot>();
        for (int i = 0; i < count; i++) {
            var selected = new RocketLandingPadSelector().select(level, rocket.snapshot().orElseThrow(), UUID.randomUUID(),
                    reservations, level.getGameTime(), true);
            if (!selected.success()) { return null; }
            picks.add(selected.snapshot().orElseThrow());
            reservations.add(region(selected.snapshot().orElseThrow()));
        }
        return picks;
    }

    /** The surface pad that ordinary selection, without a kept pad, would give this rocket now. */
    static RocketRegion ordinary(MinecraftServer server, ServerLevel level, RocketEntity rocket) {
        var selected = new RocketLandingPadSelector().select(level, rocket.snapshot().orElseThrow(), UUID.randomUUID(),
                RocketWarmupGameTestSupport.launchReservations(server, RocketTransferSavedData.get(server), logical(rocket)),
                level.getGameTime(), true);
        return selected.success() ? region(selected.snapshot().orElseThrow()) : null;
    }

    /** A connected creative mock player that records the translation keys it is shown. */
    static final class Player extends ServerPlayer {
        private final List<String> keys = new ArrayList<>();
        private io.netty.channel.embedded.EmbeddedChannel channel;

        private Player(ServerLevel level, String name) {
            super(level.getServer(), level, new com.mojang.authlib.GameProfile(UUID.randomUUID(), name));
        }

        static Player connect(ServerLevel level, BlockPos position, String name) {
            var player = new Player(level, name);
            var connection = new net.minecraft.network.Connection(net.minecraft.network.protocol.PacketFlow.SERVERBOUND);
            player.channel = new io.netty.channel.embedded.EmbeddedChannel(connection);
            level.getServer().getPlayerList().placeNewPlayer(connection, player);
            player.setGameMode(net.minecraft.world.level.GameType.CREATIVE);
            player.teleportTo(level, position.getX() + 0.5, position.getY() + 3, position.getZ() + 0.5, 0, 0);
            return player;
        }

        @Override public void displayClientMessage(Component message, boolean overlay) {
            if (message.getContents() instanceof TranslatableContents text) { keys.add(text.getKey()); }
            super.displayClientMessage(message, overlay);
        }

        /** Answers keep-alives, so a long test does not time the connection out. */
        void pump() {
            connection.tick();
            Object message;
            while ((message = channel.readOutbound()) != null) {
                if (message instanceof net.minecraft.network.protocol.game.ClientboundKeepAlivePacket keepAlive) {
                    connection.handleKeepAlive(new net.minecraft.network.protocol.game.ServerboundKeepAlivePacket(keepAlive.getId()));
                }
                io.netty.util.ReferenceCountUtil.release(message);
            }
        }

        void leave() {
            getServer().getPlayerList().remove(this);
            channel.finishAndReleaseAll();
        }

        String last() { return keys.isEmpty() ? "" : keys.get(keys.size() - 1); }

        long count(String key) { return keys.stream().filter(key::equals).count(); }
    }
}
