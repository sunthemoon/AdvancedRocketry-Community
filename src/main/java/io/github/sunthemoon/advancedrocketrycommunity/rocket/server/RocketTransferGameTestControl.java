package io.github.sunthemoon.advancedrocketrycommunity.rocket.server;

import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import net.minecraft.gametest.framework.GameTestServer;
import net.minecraft.server.MinecraftServer;

/** Service-session test control: one fixture owns its spawn step, without pausing unrelated transfers or checked writes. */
final class RocketTransferGameTestControl {
    private final Set<UUID> held = new HashSet<>();

    void holdDestinationSpawn(MinecraftServer server, UUID transfer, boolean hold) {
        requireOwner(server);
        Objects.requireNonNull(transfer, "transfer");
        if (hold) { held.add(transfer); }
        else { held.remove(transfer); }
    }

    boolean destinationSpawnHeld(UUID transfer) { return held.contains(transfer); }

    void clear() { held.clear(); }

    static void requireOwner(MinecraftServer server) {
        if (!(server instanceof GameTestServer) || !server.isSameThread()) {
            throw new IllegalStateException("Hold fixture requires GameTest owner thread");
        }
    }
}
