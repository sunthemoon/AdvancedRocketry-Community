package io.github.sunthemoon.advancedrocketrycommunity.resourceescrow;

import static org.junit.jupiter.api.Assertions.*;

import io.github.sunthemoon.advancedrocketrycommunity.rocket.entity.RocketEntity;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.*;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.persistence.RocketFlightNbtCodec;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.model.RocketNativeResources;
import io.github.sunthemoon.advancedrocketrycommunity.testsupport.MinecraftBootstrap;
import io.github.sunthemoon.advancedrocketrycommunity.testsupport.UnstartedServer;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestServer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

final class EscrowCargoTransferFixturesTest {
    @BeforeAll static void bootstrap() { MinecraftBootstrap.initialize(); }

    @Test void r1BuildsC1SyntheticArrivalFromAnUnfueledRocket() {
        var server = UnstartedServer.allocate(GameTestServer.class);
        UnstartedServer.set(MinecraftServer.class, server, "serverThread", Thread.currentThread());
        var level = UnstartedServer.allocate(ServerLevel.class);
        UnstartedServer.set(ServerLevel.class, level, "server", server);
        var rocket = UnstartedServer.allocate(RocketEntity.class);
        UnstartedServer.set(Entity.class, rocket, "level", level);
        UnstartedServer.set(Entity.class, rocket, "uuid", UUID.randomUUID());
        var logical = UUID.randomUUID();
        UnstartedServer.set(RocketEntity.class, rocket, "assemblyTransactionId", logical);
        UnstartedServer.set(RocketEntity.class, rocket, "ownerId", UUID.randomUUID());
        var recorded = EscrowCargoPreparationGameTests.snapshot(Level.OVERWORLD.location(), new BlockPos(64, 100, 64), false, List.of());
        var unfueled = RocketFlightData.initial(logical, recorded.stats().fuelCapacity(), recorded.stats().seatCount(),
                RocketFlightPlanner.EARTH.bodyId(), recorded.sourceDimension(), recorded.sourceOrigin(), 0)
                .withFuel(RocketFuelState.empty(recorded.propulsion().orElseThrow()), 0);
        UnstartedServer.set(RocketEntity.class, rocket, "flightData", unfueled);
        assertEquals(0, rocket.flightData().orElseThrow().fuel().amount());
        assertEquals(RocketFlightState.ASSEMBLED, unfueled.state());
        assertThrows(IllegalStateException.class, () -> EscrowCargoTransferFixtures.record(rocket, recorded, false));

        var record = EscrowCargoRecoveryProbe.r1Record(rocket, recorded);
        var fuel = RocketFuelState.empty(recorded.propulsion().orElseThrow())
                .withFluidBank(false, ResourceLocation.tryParse("minecraft:water"), null, 1_000);
        var plan = new RocketFlightPlan(3, record.transferId(), RocketFlightPlanner.EARTH.bodyId(), RocketFlightPlanner.MOON.bodyId(),
                recorded.sourceDimension(), recorded.sourceDimension(), 500, 0).withPropulsionDebit(new RocketPropulsionDebit(500, 0,
                fuel.propulsion().orElseThrow().revision(), fuel.resourceSignature(), RocketNativeResources.hash("W124 native cargo journal fixture")));
        assertEquals(plan, record.sourceFlightData().plan().orElseThrow());
        var expectedTransit = RocketFlightData.initial(logical, recorded.stats().fuelCapacity(), recorded.stats().seatCount(),
                plan.sourceBody(), recorded.sourceDimension(), record.sourceSnapshot().sourceOrigin(), 0)
                .withFuel(fuel, 0).withPlan(plan).startCountdown(0).completeCountdown(60).beginTransit(record.transferId(), 140);
        var expectedArrival = expectedTransit.arriveAtDestination(fuel.debit(plan).state(), plan.destinationBody(),
                recorded.sourceDimension(), recorded.sourceOrigin(), 160);
        assertEquals(expectedTransit, record.sourceFlightData());
        assertEquals(expectedArrival, record.destinationFlightData());
        assertEquals(RocketFlightNbtCodec.encode(expectedArrival.land(200)),
                RocketFlightNbtCodec.encode(record.destinationFlightData().land(200)));
        assertEquals(500, record.destinationFlightData().fuel().amount());
        assertEquals(recorded, record.destinationSnapshot());
        assertEquals(rocket.getUUID(), record.destinationEntityId().orElseThrow());
        assertEquals(RocketTransferPhase.COMMITTED, record.phase());
        assertSame(unfueled, rocket.flightData().orElseThrow(), "Building a record must not mutate the live rocket");

        // The package-private fuel overload must still reject a server outside the fixture gate.
        UnstartedServer.set(MinecraftServer.class, server, "serverThread", new Thread());
        assertThrows(IllegalArgumentException.class, () -> EscrowCargoRecoveryProbe.r1Record(rocket, recorded));
        assertThrows(IllegalArgumentException.class, () -> EscrowCargoTransferFixtures.record(rocket, recorded, false, null, fuel));
    }
}
