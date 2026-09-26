package io.github.sunthemoon.advancedrocketrycommunity.gametest;

import com.mojang.authlib.GameProfile;
import io.github.sunthemoon.advancedrocketrycommunity.AdvancedRocketryCommunity;
import io.github.sunthemoon.advancedrocketrycommunity.celestial.CelestialIds;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.entity.RocketEntity;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketDestination;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketFlightAction;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketFlightState;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.persistence.RocketTransferSavedData;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.server.RocketManager;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.server.RocketRuntime;
import java.util.UUID;
import java.util.Map;
import java.util.function.Consumer;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/** Passenger decisions made after arrival must survive the next login. */
@GameTestHolder(AdvancedRocketryCommunity.MOD_ID)
@PrefixGameTestTemplate(false)
public final class RocketPassengerContinuedUseGameTests {
    private RocketPassengerContinuedUseGameTests() {
    }

    @GameTest(template = "rocket_test", batch = "passenger_logout_cleanup", timeoutTicks = 400)
    public static void logoutImmediatelyCancelsOnlyItsPendingReconnect(GameTestHelper helper) {
        afterLanding(helper, rocket -> {
            var level = (ServerLevel) rocket.level();
            var player = new TrackingPlayer(level, rocket.ownerId().orElseThrow());
            var other = new TrackingPlayer(level, UUID.randomUUID());
            player.setPos(20_000_000, 100, 20_000_000);
            other.setPos(player.position());
            helper.assertTrue(!level.areEntitiesLoaded(player.chunkPosition().toLong()),
                    "Fixture entity chunk unexpectedly loaded");
            try {
                // FakePlayer has no network channel for Forge's tier-sync login listener.
                installedManager().onPlayerLoggedIn(new PlayerEvent.PlayerLoggedInEvent(player));
                installedManager().onPlayerLoggedIn(new PlayerEvent.PlayerLoggedInEvent(other));
                helper.assertTrue(pendingReconnects().containsKey(player.getUUID())
                                && pendingReconnects().containsKey(other.getUUID()),
                        "Login events did not enqueue both waiting players");
                MinecraftForge.EVENT_BUS.post(new PlayerEvent.PlayerLoggedOutEvent(player));
                helper.assertTrue(!pendingReconnects().containsKey(player.getUUID()),
                        "Logout left the previous session's pending reconnect");
                helper.assertTrue(pendingReconnects().containsKey(other.getUUID()),
                        "Logout cancelled another player's pending reconnect");
                installedManager().onPlayerLoggedIn(new PlayerEvent.PlayerLoggedInEvent(player));
                helper.assertTrue(pendingReconnects().containsKey(player.getUUID()),
                        "A new session could not enqueue after logout");
                helper.assertTrue(player.teleports == 0 && player.getVehicle() == null,
                        "Unready session moved or mounted during logout/relogin");
                helper.assertTrue(rocket.flightData().orElseThrow().passengers()
                                .assignment(player.getUUID()).isPresent(),
                        "Logout removed the durable seat needed for reconnection");
            } finally {
                MinecraftForge.EVENT_BUS.post(new PlayerEvent.PlayerLoggedOutEvent(player));
                MinecraftForge.EVENT_BUS.post(new PlayerEvent.PlayerLoggedOutEvent(other));
            }
        });
    }

    /** Read-only test observation; do not add a public runtime API for private retry state. */
    private static Map<?, ?> pendingReconnects() {
        try {
            Object value = installedManager();
            for (String name : new String[] {"flights", "transfers", "recovery", "reconnects", "waiting"}) {
                var field = value.getClass().getDeclaredField(name);
                field.setAccessible(true);
                value = field.get(value);
            }
            return Map.copyOf((Map<?, ?>) value);
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException("Cannot inspect the installed reconnect queue", exception);
        }
    }

    private static RocketManager installedManager() {
        try {
            var installed = RocketRuntime.class.getDeclaredField("service");
            installed.setAccessible(true);
            return (RocketManager) installed.get(null);
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException("Cannot inspect the installed rocket manager", exception);
        }
    }

    @GameTest(template = "rocket_test", batch = "passenger_entity_readiness", timeoutTicks = 400)
    public static void loginWaitsForThePlayersEntityChunk(GameTestHelper helper) {
        afterLanding(helper, rocket -> {
            var level = (ServerLevel) rocket.level();
            var player = new TrackingPlayer(level, rocket.ownerId().orElseThrow());
            var manager = new RocketManager();
            manager.onInstalled();
            player.setPos(20_000_000, 100, 20_000_000);
            helper.assertTrue(!level.areEntitiesLoaded(player.chunkPosition().toLong()),
                    "Fixture entity chunk unexpectedly loaded");
            manager.onPlayerLoggedIn(new PlayerEvent.PlayerLoggedInEvent(player));
            helper.assertTrue(player.teleports == 0 && player.getVehicle() == null,
                    "Login moved the player before entity storage was ready");
            helper.assertTrue(!level.hasChunkAt(player.blockPosition()), "Login activated the distant fixture chunk");
            player.setPos(rocket.position());
            try {
                manager.onPlayerLoggedIn(new PlayerEvent.PlayerLoggedInEvent(player));
                helper.assertTrue(player.getVehicle() == rocket && player.teleports == 1,
                        "Ready login did not recover the passenger");
            } finally {
                player.stopRiding();
            }
        });
    }

    @GameTest(template = "rocket_test", batch = "passenger_refueled_disassembly", timeoutTicks = 400)
    public static void refueledArrivalDisassemblyReleasesTransferRecord(GameTestHelper helper) {
        afterLanding(helper, rocket -> {
            var player = new RocketDisassemblyGameTests.ConsentPlayer(
                    (ServerLevel) rocket.level(), rocket.ownerId().orElseThrow());
            player.setPos(rocket.position());
            var landed = rocket.flightData().orElseThrow();
            rocket.updateFlightData(landed.withFuel(landed.fuel().fill(1000).state(), rocket.level().getGameTime())
                    .withPassengers(landed.passengers().remove(player.getUUID())));
            helper.assertTrue(rocket.flightData().orElseThrow().state() == RocketFlightState.FUELED,
                    "Fixture did not refuel");
            var manager = new RocketManager();
            manager.requestDisassembly(player, rocket);
            helper.assertTrue(rocket.isAlive() && rocket.flightData().orElseThrow().fuel().amount() == 1000,
                    "Implicit fueled teardown changed arrival authority");
            helper.assertTrue(RocketDisassemblyGameTests.confirmCommand(manager, player) == 1
                    && player.discarded == 1000, "Explicit arrival disposal did not report its exact amount");
            helper.assertTrue(rocket.isRemoved(), "Refueled arrival did not disassemble");
            var journal = RocketTransferSavedData.get(helper.getLevel().getServer());
            helper.assertTrue(journal.findByLogicalRocket(rocket.assemblyTransactionId().orElseThrow()).isEmpty(),
                    "Disassembly left a transfer record that can rebuild the consumed rocket");
        });
    }

    @GameTest(template = "rocket_test", batch = "passenger_leave", timeoutTicks = 400)
    public static void departedPassengerLoginDoesNotTeleportBack(GameTestHelper helper) {
        afterLanding(helper, rocket -> {
            var player = new TrackingPlayer((ServerLevel) rocket.level(), rocket.ownerId().orElseThrow());
            var manager = new RocketManager();
            manager.onInstalled();
            player.setPos(rocket.position());
            helper.assertTrue(player.startRiding(rocket, true), "Fixture passenger did not mount");
            manager.requestFlightIntent(player, rocket.getId(), RocketFlightAction.LEAVE,
                    RocketDestination.EARTH, null, UUID.randomUUID());
            helper.assertTrue(rocket.flightData().orElseThrow().passengers().assignment(player.getUUID()).isEmpty(),
                    "Leave did not remove the passenger assignment");
            player.setPos(rocket.getX() + 40, rocket.getY(), rocket.getZ());
            manager.onPlayerLoggedIn(new PlayerEvent.PlayerLoggedInEvent(player));
            helper.assertTrue(player.teleports == 0, "Login moved a passenger who already left after landing");
            helper.assertTrue(player.getVehicle() == null, "Departed passenger was mounted again");
        });
    }

    @GameTest(template = "rocket_test", batch = "passenger_new_board", timeoutTicks = 400)
    public static void newlyBoardedPassengerReconnectsAfterLanding(GameTestHelper helper) {
        afterLanding(helper, rocket -> {
            var player = new TrackingPlayer((ServerLevel) rocket.level(), UUID.randomUUID());
            var manager = new RocketManager();
            manager.onInstalled();
            player.setPos(rocket.position());
            manager.requestFlightIntent(player, rocket.getId(), RocketFlightAction.BOARD,
                    RocketDestination.EARTH, null, UUID.randomUUID());
            helper.assertTrue(player.getVehicle() == rocket, "New passenger did not board");
            player.stopRiding();
            try {
                manager.onPlayerLoggedIn(new PlayerEvent.PlayerLoggedInEvent(player));
                helper.assertTrue(player.getVehicle() == rocket, "Login omitted the post-landing passenger");
                helper.assertTrue(player.teleports == 1, "Passenger login did not use the recovery adapter");
            } finally {
                player.stopRiding();
            }
        });
    }

    private static void afterLanding(GameTestHelper helper, Consumer<RocketEntity> check) {
        var earth = helper.getLevel();
        var moon = earth.getServer().getLevel(CelestialIds.MOON_LEVEL);
        helper.assertTrue(moon != null, "Moon unavailable");
        var owner = UUID.randomUUID();
        var source = RocketPassengerPersistenceGameTests.createRocket(helper, owner);
        var initial = source.flightData().orElseThrow();
        source.updateFlightData(initial.withFuel(initial.fuel().fill(1000).state(), earth.getGameTime())
                .withPassengers(initial.passengers().assign(owner).orElseThrow()));
        var transfer = UUID.randomUUID();
        var launched = RocketRuntime.requestAdminFlight(source, RocketDestination.MOON, transfer);
        helper.assertTrue(launched.success(), "Fixture launch failed: " + launched.code());
        helper.runAfterDelay(270, () -> {
            var journal = RocketTransferSavedData.get(earth.getServer());
            var record = journal.find(transfer).orElseThrow();
            var rocket = (RocketEntity) moon.getEntity(record.destinationEntityId().orElseThrow());
            try {
                helper.assertTrue(rocket != null && rocket.flightData().orElseThrow().state() == RocketFlightState.LANDED,
                        "Fixture did not land");
                check.accept(rocket);
                helper.succeed();
            } finally {
                if (rocket != null) {
                    rocket.ejectPassengers();
                    rocket.discard();
                }
                journal.remove(transfer);
                journal.flush(earth.getServer());
            }
        });
    }

    private static final class TrackingPlayer extends FakePlayer {
        private int teleports;

        private TrackingPlayer(ServerLevel level, UUID id) {
            super(level, new GameProfile(id, "ARCEContinuedUse"));
        }

        @Override
        public void teleportTo(ServerLevel level, double x, double y, double z, float yaw, float pitch) {
            teleports++;
            super.teleportTo(level, x, y, z, yaw, pitch);
        }
    }
}
