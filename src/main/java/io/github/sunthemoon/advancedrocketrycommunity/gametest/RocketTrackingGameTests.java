package io.github.sunthemoon.advancedrocketrycommunity.gametest;

import io.github.sunthemoon.advancedrocketrycommunity.ModIdentity;
import io.github.sunthemoon.advancedrocketrycommunity.celestial.CelestialIds;
import io.github.sunthemoon.advancedrocketrycommunity.registry.ModBlocks;
import io.github.sunthemoon.advancedrocketrycommunity.registry.ModEntities;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.entity.RocketEntity;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketFlightData;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketFlightPlan;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketFlightState;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundMoveEntityPacket;
import net.minecraft.network.protocol.game.ClientboundTeleportEntityPacket;
import net.minecraft.server.level.ServerEntity;
import net.minecraft.world.level.Level;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/** Uses the registered type and native tracker, without enrolling a synthetic flight in the runtime. */
@GameTestHolder(ModIdentity.MOD_ID)
@PrefixGameTestTemplate(false)
public final class RocketTrackingGameTests {
    private RocketTrackingGameTests() { }

    @GameTest(template = "rocket_test", batch = "rocket_tracking", timeoutTicks = 100)
    public static void nativeTrackerOnlyUsesFastCadenceDuringAscentAndDescent(GameTestHelper helper) {
        BlockPos origin = new BlockPos(3, 2, 3);
        RocketFlightGameTestFixtures.placeLegalRocket(helper, origin);
        helper.setBlock(origin.west(), ModBlocks.ROCKET_FUEL_TANK.get());
        var snapshot = RocketFlightGameTestFixtures.successfulSnapshot(helper, origin);
        var type = ModEntities.ROCKET.get();
        helper.assertTrue(type.updateInterval() == 20, "Parked tracker interval changed");
        for (RocketFlightState state : RocketFlightState.values()) {
            if (state == RocketFlightState.DISASSEMBLED) { continue; }
            RocketEntity rocket = type.create(helper.getLevel());
            helper.assertTrue(rocket != null, "Registered rocket factory returned null");
            try {
                rocket.initialize(snapshot, UUID.randomUUID(), UUID.randomUUID());
                RocketFlightData initial = rocket.flightData().orElseThrow();
                boolean descent = state == RocketFlightState.DESCENT;
                boolean planned = state.inMotion();
                var plan = planned ? new RocketFlightPlan(3, UUID.randomUUID(),
                        descent ? CelestialIds.MOON_ID : CelestialIds.EARTH_ID,
                        descent ? CelestialIds.EARTH_ID : CelestialIds.MOON_ID,
                        descent ? CelestialIds.MOON_LEVEL.location() : Level.OVERWORLD.location(),
                        descent ? Level.OVERWORLD.location() : CelestialIds.MOON_LEVEL.location(), 1, 0) : null;
                rocket.updateFlightData(RocketFlightData.restore(initial.schemaVersion(), initial.logicalRocketId(),
                        state, initial.fuel().fill(initial.fuel().capacity()).state(), plan, initial.passengers(),
                        initial.currentBody(), initial.currentDimension(), initial.currentOrigin(), 0,
                        state == RocketFlightState.TRANSIT || descent ? UUID.randomUUID() : null,
                        initial.currentTarget().orElseThrow()));
                List<Packet<?>> packets = new ArrayList<>();
                ServerEntity tracker = new ServerEntity(helper.getLevel(), rocket, type.updateInterval(),
                        type.trackDeltas(), packets::add);
                tracker.sendChanges(); // Prime the tracker and clear the initial data update.
                packets.clear();
                int impulses = 0;
                int lastPositionTick = 0;
                boolean fast = state == RocketFlightState.ASCENT || descent;
                for (int tick = 1; tick <= 40; tick++) {
                    rocket.tickCount = tick; // ServerLevel normally owns this increment.
                    rocket.tick();
                    if (rocket.hasImpulse) { impulses++; }
                    rocket.setPos(rocket.getX(), rocket.getY() + (descent ? -0.25D : 0.25D), rocket.getZ());
                    int before = packets.size();
                    tracker.sendChanges();
                    if (packets.subList(before, packets.size()).stream().anyMatch(RocketTrackingGameTests::position)) {
                        lastPositionTick = tick;
                    }
                    if (fast) {
                        helper.assertTrue(tick - lastPositionTick < 2, "Flight position stale at tick " + tick + " in " + state);
                    }
                    helper.assertTrue(!rocket.hasImpulse, "Native tracker did not consume the update flag");
                }
                long positions = packets.stream().filter(RocketTrackingGameTests::position).count();
                helper.assertTrue(impulses == (fast ? 20 : 0), "Wrong fast-update count for " + state + ": " + impulses);
                helper.assertTrue(positions == (fast ? 20 : 2), "Wrong position packet count for " + state + ": " + positions);
            } finally {
                rocket.discard();
            }
        }
        helper.succeed();
    }

    private static boolean position(Packet<?> packet) {
        return packet instanceof ClientboundMoveEntityPacket move && move.hasPosition()
                || packet instanceof ClientboundTeleportEntityPacket;
    }
}
