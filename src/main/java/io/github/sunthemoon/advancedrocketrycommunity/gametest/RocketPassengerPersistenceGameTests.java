package io.github.sunthemoon.advancedrocketrycommunity.gametest;

import com.mojang.authlib.GameProfile;
import io.github.sunthemoon.advancedrocketrycommunity.AdvancedRocketryCommunity;
import io.github.sunthemoon.advancedrocketrycommunity.registry.ModBlocks;
import io.github.sunthemoon.advancedrocketrycommunity.registry.ModEntities;
import io.github.sunthemoon.advancedrocketrycommunity.celestial.CelestialIds;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.RocketLimits;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.forge.RocketBlockEntityAdapters;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.entity.RocketEntity;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketDestination;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketFlightState;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.persistence.RocketTransferSavedData;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.model.RocketPosition;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.scan.RocketStructureScanTask;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.forge.ServerLevelRocketScanWorld;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.server.RocketRuntime;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.server.RocketManager;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/** Exercises vanilla persistence entry points, not a parallel serialization model. */
@GameTestHolder(AdvancedRocketryCommunity.MOD_ID)
@PrefixGameTestTemplate(false)
public final class RocketPassengerPersistenceGameTests {
    private static final String TEMPLATE = "rocket_test";

    private RocketPassengerPersistenceGameTests() {
    }

    @GameTest(template = TEMPLATE)
    public static void mountedRocketStaysWorldOwnedAcrossPassengerCounts(GameTestHelper helper) {
        var level = helper.getLevel();
        var first = new FakePlayer(level, new GameProfile(UUID.randomUUID(), "ARCEPersistOne"));
        var second = new FakePlayer(level, new GameProfile(UUID.randomUUID(), "ARCEPersistTwo"));
        RocketEntity rocket = createRocket(helper, first.getUUID());
        try {
            var flight = rocket.flightData().orElseThrow();
            var manifest = flight.passengers().assign(first.getUUID()).orElseThrow()
                    .assign(second.getUUID()).orElseThrow();
            rocket.updateFlightData(flight.withPassengers(manifest));
            helper.assertTrue(rocket.shouldBeSaved(), "Unoccupied rocket must be world-owned");
            helper.assertTrue(first.startRiding(rocket, true), "First passenger could not mount");
            assertWorldOwned(helper, rocket, first);
            helper.assertTrue(second.startRiding(rocket, true), "Second passenger could not mount");
            assertWorldOwned(helper, rocket, first);
            assertWorldOwned(helper, rocket, second);
            first.stopRiding();
            assertWorldOwned(helper, rocket, second);
            var snapshot = rocket.snapshot().orElseThrow();
            var saved = new CompoundTag();
            helper.assertTrue(rocket.save(saved), "World entity serialization failed");
            var restored = ModEntities.ROCKET.get().create(level);
            helper.assertTrue(restored != null, "Rocket type unavailable");
            restored.load(saved);
            helper.assertTrue(restored.getUUID().equals(rocket.getUUID()), "Entity identity changed");
            helper.assertTrue(restored.snapshot().orElseThrow().contentHash().equals(snapshot.contentHash()),
                    "World save changed snapshot");
            helper.assertTrue(restored.flightData().orElseThrow().passengers().equals(manifest),
                    "World save changed passenger assignments");
            helper.succeed();
        } finally {
            first.stopRiding();
            second.stopRiding();
            rocket.discard();
        }
    }

    private static void assertWorldOwned(GameTestHelper helper, RocketEntity rocket, FakePlayer player) {
        helper.assertTrue(rocket.shouldBeSaved(), "Mounted rocket was excluded from world saving");
        var savedPlayer = player.saveWithoutId(new CompoundTag());
        helper.assertTrue(!savedPlayer.contains("RootVehicle"), "Player save embeds a second rocket authority");
    }

    @GameTest(template = TEMPLATE, batch = "legacy_passenger_recovery", timeoutTicks = 400)
    public static void legacyVehicleLoginKeepsRefueledJournalAuthority(GameTestHelper helper) {
        var earth = helper.getLevel();
        var moon = earth.getServer().getLevel(CelestialIds.MOON_LEVEL);
        helper.assertTrue(moon != null, "Moon unavailable");
        var owner = UUID.randomUUID();
        var source = createRocket(helper, owner);
        var flight = source.flightData().orElseThrow();
        source.updateFlightData(flight.withFuel(flight.fuel().fill(flight.fuel().capacity()).state(), earth.getGameTime())
                .withPassengers(flight.passengers().assign(owner).orElseThrow()));
        var transfer = UUID.randomUUID();
        var launch = RocketRuntime.requestAdminFlight(source, RocketDestination.MOON, transfer);
        helper.assertTrue(launch.success(), "Fixture launch failed: " + launch.code());
        helper.runAfterDelay(270, () -> {
            var journal = RocketTransferSavedData.get(earth.getServer());
            var record = journal.find(transfer).orElseThrow();
            var authority = (RocketEntity) moon.getEntity(record.destinationEntityId().orElseThrow());
            helper.assertTrue(authority != null && authority.flightData().orElseThrow().state() == RocketFlightState.LANDED,
                    "Fixture did not land");
            var player = new FakePlayer(moon, new GameProfile(owner, "ARCELegacyRider"));
            var stale = ModEntities.ROCKET.get().create(moon);
            helper.assertTrue(stale != null, "Rocket type unavailable");
            try {
                var saved = new CompoundTag();
                authority.save(saved);
                stale.load(saved);
                stale.setUUID(new UUID(Long.MIN_VALUE, 0));
                helper.assertTrue(moon.addFreshEntity(stale), "Legacy fixture spawn failed");
                player.setPos(stale.position());
                helper.assertTrue(player.startRiding(stale, true), "Legacy passenger failed to attach");
                var landed = authority.flightData().orElseThrow();
                authority.updateFlightData(landed.withFuel(landed.fuel().fill(landed.fuel().capacity()).state(), moon.getGameTime()));
                helper.assertTrue(authority.flightData().orElseThrow().state() == RocketFlightState.FUELED,
                        "Refuel fixture did not advance state");
                // FakePlayer has no negotiated channel for Forge's unrelated login listeners.
                // Exercise the production rocket listener directly; native clients cover dispatch.
                var manager = new RocketManager();
                manager.onInstalled();
                for (int attempt = 0; attempt < 2; attempt++) {
                    manager.onPlayerLoggedIn(new PlayerEvent.PlayerLoggedInEvent(player));
                    helper.assertTrue(player.getVehicle() == authority, "Login selected the stale vehicle");
                    helper.assertTrue(stale.isRemoved(), "Login left duplicate authority alive");
                    helper.assertTrue(authority.flightData().orElseThrow().fuel().amount() == 1000,
                            "Login replaced post-landing fuel with an older snapshot");
                    helper.assertTrue(journal.find(transfer).orElseThrow().destinationEntityId()
                            .filter(authority.getUUID()::equals).isPresent(), "Journal authority changed");
                }
                helper.succeed();
            } finally {
                player.stopRiding();
                stale.discard();
                authority.discard();
                journal.remove(transfer);
                journal.flush(earth.getServer());
            }
        });
    }

    static RocketEntity createRocket(GameTestHelper helper, UUID owner) {
        var origin = new BlockPos(2, 2, 2);
        helper.setBlock(origin, ModBlocks.ROCKET_MOTOR.get());
        helper.setBlock(origin.above(), ModBlocks.ROCKET_SEAT.get());
        helper.setBlock(origin.above().east(), ModBlocks.ROCKET_SEAT.get());
        helper.setBlock(origin.above(2), ModBlocks.GUIDANCE_COMPUTER.get());
        helper.setBlock(origin.west(), ModBlocks.ROCKET_FUEL_TANK.get());
        var level = helper.getLevel();
        var absolute = helper.absolutePos(origin);
        var scan = new RocketStructureScanTask(
                new ServerLevelRocketScanWorld(level, RocketBlockEntityAdapters.defaults()),
                level.dimension().location(),
                new RocketPosition(absolute.getX(), absolute.getY(), absolute.getZ()),
                UUID.randomUUID(), level.getGameTime());
        var snapshot = scan.step(RocketLimits.MAX_SCAN_INSPECTIONS_PER_TICK).snapshot().orElseThrow();
        var rocket = ModEntities.ROCKET.get().create(level);
        helper.assertTrue(rocket != null, "Rocket type unavailable");
        rocket.initialize(snapshot, UUID.randomUUID(), owner);
        helper.assertTrue(level.addFreshEntity(rocket), "Rocket spawn failed");
        return rocket;
    }
}
