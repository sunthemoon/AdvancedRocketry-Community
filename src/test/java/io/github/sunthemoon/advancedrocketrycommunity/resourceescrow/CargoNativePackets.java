package io.github.sunthemoon.advancedrocketrycommunity.resourceescrow;

import io.github.sunthemoon.advancedrocketrycommunity.endgame.command.ReleaseTestPlayers;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.network.RocketVisualChunkPacket;
import io.netty.channel.*;
import java.util.*;
import java.util.concurrent.ConcurrentLinkedQueue;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.protocol.game.ClientboundCustomPayloadPacket;
import net.minecraft.server.MinecraftServer;

/** Passive observer before the existing release-player transport drain; forwards every message unchanged. */
final class CargoNativePackets implements AutoCloseable {
    record Visual(int tick, UUID player, UUID rocket, String hash, int index, int count, int bytes) { }
    final Queue<Visual> rows = new ConcurrentLinkedQueue<>();
    private final List<Channel> channels = new ArrayList<>();
    private volatile String failure;
    CargoNativePackets(MinecraftServer server) {
        var all = (Map<?, ?>) CargoNativeBenchmark.field(ReleaseTestPlayers.class, null, "CHANNELS");
        var owned = (Map<?, ?>) all.get(server);
        EscrowTags.require(owned != null && owned.size() == 2, "Two owned channels required");
        owned.forEach((player, value) -> {
            var channel = (Channel) CargoNativeBenchmark.field(value, "channel"); channels.add(channel);
            var id = ((net.minecraft.server.level.ServerPlayer) player).getUUID();
            channel.pipeline().addFirst("w144-observer", new ChannelOutboundHandlerAdapter() {
                @Override public void write(ChannelHandlerContext context, Object message, ChannelPromise promise) throws Exception {
                    try {
                        if (message instanceof ClientboundCustomPayloadPacket packet
                                && packet.getIdentifier().toString().equals("advancedrocketrycommunity:rocket_visual")) {
                            var data = new FriendlyByteBuf(packet.getData().duplicate());
                            EscrowTags.require(data.readVarInt() == 0, "Unexpected visual discriminator");
                            var visual = RocketVisualChunkPacket.decode(data);
                            EscrowTags.require(rows.size() < 4096, "Visual observer bound exceeded");
                            rows.add(new Visual(server.getTickCount(), id, visual.entityId(), visual.structureContentHash(),
                                    visual.chunkIndex(), visual.chunkCount(), visual.totalBytes()));
                        }
                    } catch (RuntimeException problem) { failure = problem.toString(); }
                    super.write(context, message, promise);
                }
            });
        });
    }
    void check() { EscrowTags.require(failure == null, "Visual observer failed: " + failure); }
    boolean complete(UUID rocket, String hash) {
        for (var first : rows) {
            if (!first.rocket().equals(rocket) || !first.hash().equals(hash)) { continue; }
            var indexes = new HashSet<Integer>();
            rows.stream().filter(row -> row.rocket().equals(rocket) && row.hash().equals(hash) && row.player().equals(first.player()))
                    .forEach(row -> indexes.add(row.index()));
            if (indexes.size() == first.count()) { return true; }
        }
        return false;
    }
    @Override public void close() {
        for (var channel : channels) {
            if (channel.pipeline().get("w144-observer") != null) { channel.pipeline().remove("w144-observer"); }
        }
    }
}
