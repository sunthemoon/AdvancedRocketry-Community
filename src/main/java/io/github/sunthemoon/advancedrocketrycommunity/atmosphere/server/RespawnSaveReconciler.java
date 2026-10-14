package io.github.sunthemoon.advancedrocketrycommunity.atmosphere.server;

import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Objects;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.level.Level;

/** Server-thread, session-owned copies across an unadopted respawn; not a persistent recovery record. */
final class RespawnSaveReconciler {
    private final Map<ServerPlayer, Copy> copies = new IdentityHashMap<>();

    private record Tuple(ResourceKey<Level> dimension, BlockPos position, int angleBits, boolean forced) {
        static Tuple read(ServerPlayer player) {
            BlockPos position = player.getRespawnPosition();
            return new Tuple(player.getRespawnDimension(), position == null ? null : position.immutable(),
                    Float.floatToRawIntBits(player.getRespawnAngle()), player.isRespawnForced());
        }

        void write(ServerPlayer player) {
            player.respawnDimension = dimension;
            player.respawnPosition = position;
            player.respawnAngle = Float.intBitsToFloat(angleBits);
            player.respawnForced = forced;
        }
    }

    private record Copy(ServerPlayer replacement, ServerGamePacketListenerImpl connection, Tuple tuple) { }

    /** First copy needs a pending backup and the exact clear; later copies need this service's exact previous copy. */
    boolean reconcile(ServerPlayer replacement, boolean pending) {
        ServerPlayer old = replacement.connection == null ? null : replacement.connection.player;
        if (!unadopted(old, replacement)) { return false; }
        Copy previous = copies.get(old);
        boolean owned = previous != null && previous.replacement() == replacement
                && previous.connection() == replacement.connection
                && previous.tuple().equals(Tuple.read(old));
        if (!owned && !(pending && cleared(old))) {
            copies.remove(old); // A foreign old tuple is never overwritten or adopted as our own copy.
            return false;
        }
        if (cleared(replacement)) { return false; }
        Tuple latest = Tuple.read(replacement);
        latest.write(old);
        copies.put(old, new Copy(replacement, replacement.connection, latest));
        return true;
    }

    void prune(MinecraftServer server) {
        copies.entrySet().removeIf(entry -> entry.getKey().server != server
                || !unadopted(entry.getKey(), entry.getValue().replacement())
                || entry.getValue().connection() != entry.getKey().connection
                || !Objects.equals(entry.getValue().tuple(), Tuple.read(entry.getKey())));
    }

    void forget(ServerPlayer departing) {
        copies.entrySet().removeIf(entry -> entry.getKey() == departing || entry.getValue().replacement() == departing);
    }

    void clear() { copies.clear(); }

    private static boolean unadopted(ServerPlayer old, ServerPlayer replacement) {
        return old != null && old != replacement && old.server == replacement.server
                && old.getUUID().equals(replacement.getUUID()) && old.connection == replacement.connection
                && replacement.connection.player == old
                && replacement.server.getPlayerList().getPlayer(replacement.getUUID()) == replacement;
    }

    private static boolean cleared(ServerPlayer player) {
        return player.getRespawnPosition() == null && Level.OVERWORLD.equals(player.getRespawnDimension())
                && Float.floatToRawIntBits(player.getRespawnAngle()) == 0 && !player.isRespawnForced();
    }
}
