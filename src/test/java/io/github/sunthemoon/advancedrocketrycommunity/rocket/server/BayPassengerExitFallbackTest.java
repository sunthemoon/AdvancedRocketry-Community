package io.github.sunthemoon.advancedrocketrycommunity.rocket.server;

import static org.junit.jupiter.api.Assertions.*;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.entity.RocketEntity;
import io.github.sunthemoon.advancedrocketrycommunity.testsupport.MinecraftBootstrap;
import io.github.sunthemoon.advancedrocketrycommunity.testsupport.UnstartedServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/** W242-R8: without the exit service a player rider gets the ordinary dismount position, not the rocket's own. */
final class BayPassengerExitFallbackTest {
    @BeforeAll static void bootstrap() { MinecraftBootstrap.initialize(); }

    @Test void absentServiceOrAbsentExitServiceKeepsTheOrdinaryOffset() {
        var rocket = UnstartedServer.allocate(RocketEntity.class);
        UnstartedServer.set(Entity.class, rocket, "position", new Vec3(10.5, 64, -3.5));
        var player = UnstartedServer.allocate(ServerPlayer.class);
        var expected = new Vec3(12, 64, -3.5);
        Object previous = UnstartedServer.get(RocketRuntime.class, null, "service");
        try {
            UnstartedServer.set(RocketRuntime.class, null, "service", null);
            assertEquals(expected, RocketRuntime.bayPassengerExitLocation(rocket, player), "No installed service");
            // Before ServerStarting or after ServerStopped, the installed manager holds no exit service.
            UnstartedServer.set(RocketRuntime.class, null, "service", UnstartedServer.allocate(RocketManager.class));
            assertEquals(expected, RocketRuntime.bayPassengerExitLocation(rocket, player), "Manager without an exit service");
        } finally { UnstartedServer.set(RocketRuntime.class, null, "service", previous); }
    }
}
