package io.github.sunthemoon.advancedrocketrycommunity.rocket.server;

import static org.junit.jupiter.api.Assertions.*;
import io.github.sunthemoon.advancedrocketrycommunity.celestial.service.SafeCelestialTravel;
import io.github.sunthemoon.advancedrocketrycommunity.testsupport.MinecraftBootstrap;
import io.github.sunthemoon.advancedrocketrycommunity.testsupport.UnstartedServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/** W242-R3: W230 section 5's trusted travel move detaches inside the internal passenger scope, never as an exit. */
final class BayPassengerExitTrustedMoveTest {
    @BeforeAll static void bootstrap() { MinecraftBootstrap.initialize(); }

    /** A real ServerPlayer class whose detach and teleport only record whether the bay-exit scope was active. */
    static final class Rider extends ServerPlayer {
        BayPassengerExitService exits;
        int detaches, moves;
        boolean scopedDetach, scopedMove;
        Rider() { super(null, null, null); } // never runs: UnstartedServer allocates the instance
        @Override public void stopRiding() { detaches++; scopedDetach = exits.state.internal(this); }
        @Override public void teleportTo(ServerLevel level, double x, double y, double z, float yaw, float pitch) {
            moves++; scopedMove = exits.state.internal(this);
        }
    }

    @Test void safeTravelDetachesAndMovesInsideTheInternalScopeAndClosesIt() {
        var server = UnstartedServer.owned();
        var exits = new BayPassengerExitService(server);
        var manager = UnstartedServer.allocate(RocketManager.class);
        UnstartedServer.set(RocketManager.class, manager, "passengerExits", exits);
        var rider = UnstartedServer.allocate(Rider.class);
        rider.exits = exits;
        UnstartedServer.set(net.minecraft.world.entity.Entity.class, rider, "deltaMovement", new Vec3(0, -1, 0));
        Object previous = UnstartedServer.get(RocketRuntime.class, null, "service");
        UnstartedServer.set(RocketRuntime.class, null, "service", manager);
        try {
            new SafeCelestialTravel().teleport(rider, null, new SafeCelestialTravel.Destination(8.5, 80, 8.5));
        } finally { UnstartedServer.set(RocketRuntime.class, null, "service", previous); }
        assertEquals(1, rider.detaches); assertEquals(1, rider.moves);
        assertTrue(rider.scopedDetach, "The travel detach ran outside the internal passenger scope");
        assertTrue(rider.scopedMove, "The travel teleport ran outside the internal passenger scope");
        assertFalse(exits.state.internal(rider), "The internal scope outlived the trusted move");
        assertTrue(exits.state.live.isEmpty());
    }
}
