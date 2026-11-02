package io.github.sunthemoon.advancedrocketrycommunity.rocket.server;

import static org.junit.jupiter.api.Assertions.*;
import io.github.sunthemoon.advancedrocketrycommunity.testsupport.MinecraftBootstrap;
import io.github.sunthemoon.advancedrocketrycommunity.testsupport.UnstartedServer;
import java.util.Set;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundPlayerPositionPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.entity.RelativeMovement;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/** Pinned vanilla semantics: these execute teleport unchanged and capture only the outgoing packet. */
final class BayPassengerExitCorrectionObservationTest {
    @BeforeAll static void bootstrap() { MinecraftBootstrap.initialize(); }

    @Test void movingCorrectionAlsoLeavesThePendingTargetEqualToCurrentPosition() throws Exception {
        var player = player();
        Vec3 before = player.position();
        player.connection.teleport(1, 79, 0, 0, 0);
        assertNotEquals(before, player.position());
        assertEquals(player.position(), pending(player));
    }

    @Test void absoluteAndRelativeNoOpHaveIdenticalCounterTargetAndPositionObservations() throws Exception {
        var absolute = player(); var relative = player();
        absolute.connection.teleport(0, 80, 0, 0, 0);
        relative.connection.teleport(0, 80, 0, 0, 0, Set.of(RelativeMovement.X, RelativeMovement.Y, RelativeMovement.Z));
        assertEquals(counter(absolute), counter(relative));
        assertEquals(absolute.position(), relative.position());
        assertEquals(pending(absolute), pending(relative));
        assertTrue(((Capture) absolute.connection).packet.getRelativeArguments().isEmpty());
        assertFalse(((Capture) relative.connection).packet.getRelativeArguments().isEmpty());
    }

    private static Vec3 pending(ServerPlayer player) throws Exception {
        return UnstartedServer.get(ServerGamePacketListenerImpl.class, player.connection, "awaitingPositionFromClient");
    }
    private static int counter(ServerPlayer player) {
        return UnstartedServer.get(ServerGamePacketListenerImpl.class, player.connection, "awaitingTeleport");
    }
    private static Player player() {
        var player = UnstartedServer.allocate(Player.class);
        player.absMoveTo(0, 80, 0, 0, 0);
        var listener = UnstartedServer.allocate(Capture.class);
        listener.player = player; player.connection = listener;
        return player;
    }
    private static final class Capture extends ServerGamePacketListenerImpl {
        ClientboundPlayerPositionPacket packet;
        private Capture() { super(null, null, null); }
        @Override public void send(Packet<?> packet) { this.packet = (ClientboundPlayerPositionPacket) packet; }
    }
    private static final class Player extends ServerPlayer {
        private Player() { super(null, null, null); }
        @Override public float getYRot() { return 0; }
        @Override public float getXRot() { return 0; }
        @Override public void absMoveTo(double x, double y, double z, float yaw, float pitch) {
            UnstartedServer.set(net.minecraft.world.entity.Entity.class, this, "position", new Vec3(x, y, z));
        }
    }
}
