package io.github.sunthemoon.advancedrocketrycommunity.gametest;

import static io.github.sunthemoon.advancedrocketrycommunity.gametest.RocketFlightGameTestFixtures.*;

import com.mojang.authlib.GameProfile;
import io.github.sunthemoon.advancedrocketrycommunity.AdvancedRocketryCommunity;
import io.github.sunthemoon.advancedrocketrycommunity.celestial.CelestialIds;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.entity.RocketEntity;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketDestination;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketFlightAction;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketFlightState;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.menu.RocketFlightMenu;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.menu.RocketFlightSelection;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.persistence.RocketTransferSavedData;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.server.RocketRuntime;
import io.github.sunthemoon.advancedrocketrycommunity.station.persistence.StationRegistrySavedData;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.Block;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import net.minecraftforge.common.util.FakePlayer;

/** Server-authority and hostile flight-intent scenarios. */
@GameTestHolder(AdvancedRocketryCommunity.MOD_ID)
@PrefixGameTestTemplate(false)
public final class RocketFlightSecurityGameTests {
    private static final String TEMPLATE = "rocket_test";

    private RocketFlightSecurityGameTests() {
    }

    @GameTest(template = TEMPLATE, batch = "flight_security", timeoutTicks = 200)
    public static void hostileFlightIntentsCannotChangeAuthority(GameTestHelper helper) {
        ServerLevel earth = helper.getLevel();
        ServerLevel moon = earth.getServer().getLevel(CelestialIds.MOON_LEVEL);
        helper.assertTrue(moon != null, "Moon Level is unavailable");
        clearTransferJournal(earth);
        primePadChunks(moon);
        helper.runAfterDelay(40, () -> {
            clearPadRockets(earth);
            clearPadRockets(moon);
            ServerPlayer owner = new FlightFakePlayer(earth);
            RocketEntity unauthorizedRocket = assembleFueledRocket(
                    helper,
                    new BlockPos(3, 2, 3),
                    UUID.randomUUID()
            );
            long unauthorizedFuel = unauthorizedRocket.flightData().orElseThrow().fuel().amount();
            owner.setPos(unauthorizedRocket.getX(), unauthorizedRocket.getY(), unauthorizedRocket.getZ());
            RocketRuntime.requestFlightIntent(
                    owner,
                    unauthorizedRocket.getId(),
                    RocketFlightAction.LAUNCH,
                    RocketDestination.MOON,
                    UUID.randomUUID()
            );
            assertUnchangedSecurityState(
                    helper,
                    earth,
                    unauthorizedRocket,
                    unauthorizedFuel,
                    "unauthorized request"
            );
            unauthorizedRocket.discard();

            RocketEntity rocket = assembleFueledRocket(helper, new BlockPos(12, 2, 3), owner.getUUID());
            long fuelBefore = rocket.flightData().orElseThrow().fuel().amount();

            owner.setPos(rocket.getX() + 100.0D, rocket.getY(), rocket.getZ());
            RocketRuntime.requestFlightIntent(
                    owner,
                    rocket.getId(),
                    RocketFlightAction.LAUNCH,
                    RocketDestination.MOON,
                    UUID.randomUUID()
            );
            assertUnchangedSecurityState(helper, earth, rocket, fuelBefore, "far request");

            owner.setPos(rocket.getX(), rocket.getY(), rocket.getZ());
            RocketRuntime.requestFlightIntent(
                    owner,
                    rocket.getId(),
                    RocketFlightAction.LAUNCH,
                    RocketDestination.EARTH,
                    UUID.randomUUID()
            );
            assertUnchangedSecurityState(helper, earth, rocket, fuelBefore, "invalid destination");

            UUID launchId = UUID.randomUUID();
            StationRegistrySavedData quoteStations = StationRegistrySavedData.get(earth.getServer());
            UUID quoteStationId = UUID.randomUUID();
            quoteStations.reserve(
                    quoteStationId,
                    owner.getUUID(),
                    "Quote Probe",
                    CelestialIds.EARTH_ID,
                    earth.getGameTime()
            );
            quoteStations.commit(quoteStationId);
            RocketFlightMenu console = new RocketFlightMenu(7, owner.getInventory(), rocket);
            helper.assertTrue(console.activePlan().destination() == null,
                    "An unplanned console invented an active cancellation target");
            var quotesBefore = console.quotes();
            helper.assertTrue(quotesBefore.moon().canLaunch() && quotesBefore.station().canLaunch(),
                    "Fueled console did not expose both existing route quotes");
            helper.assertTrue(quotesBefore.moon().requiredFuel() - quotesBefore.station().requiredFuel() == 42,
                    "Station preview reused the default Moon fuel quote");
            RocketRuntime.requestFlightIntent(
                    owner,
                    rocket.getId(),
                    RocketFlightAction.LAUNCH,
                    RocketDestination.MOON,
                    launchId
            );
            helper.assertTrue(
                    rocket.flightData().orElseThrow().state() == RocketFlightState.COUNTDOWN,
                    "Valid owner launch did not start countdown"
            );
            helper.assertTrue(rocket.flightData().orElseThrow().plan().orElseThrow().requiredFuel()
                            == quotesBefore.moon().requiredFuel(),
                    "Menu preview did not match the server launch plan");
            helper.assertTrue(!console.quotes().moon().canLaunch() && !console.quotes().station().canLaunch(),
                    "Countdown console still offered a launchable quote");
            RocketRuntime.requestFlightIntent(
                    owner,
                    rocket.getId(),
                    RocketFlightAction.LAUNCH,
                    RocketDestination.MOON,
                    launchId
            );
            helper.assertTrue(
                    RocketTransferSavedData.get(earth.getServer()).entries().size() == 1,
                    "Replayed launch created another transfer"
            );
            RocketFlightSelection selection = new RocketFlightSelection(java.util.List.of());
            selection.initialize(RocketDestination.MOON, RocketDestination.EARTH, null);
            selection.select(RocketDestination.EARTH);
            var cancellation = selection.target(RocketFlightAction.CANCEL, console.activePlan());
            helper.assertTrue(cancellation.destination() == RocketDestination.MOON,
                    "A console opened before launch did not observe the server's cancellation target");
            RocketRuntime.requestFlightIntent(
                    owner,
                    rocket.getId(),
                    RocketFlightAction.CANCEL,
                    cancellation.destination(),
                    cancellation.stationId(),
                    UUID.randomUUID()
            );
            helper.assertTrue(
                    rocket.flightData().orElseThrow().state() == RocketFlightState.FUELED,
                    "Countdown cancellation did not restore FUELED"
            );
            quoteStations.delete(quoteStationId);
            helper.assertTrue(
                    rocket.flightData().orElseThrow().fuel().amount() == fuelBefore,
                    "Hostile or cancelled request consumed fuel"
            );
            helper.assertTrue(
                    RocketTransferSavedData.get(earth.getServer()).entries().isEmpty(),
                    "Countdown cancellation retained a transfer journal"
            );
            rocket.discard();
            helper.succeed();
        });
    }

    /** Network-free server actor for authority and hostile-intent validation. */
    private static final class FlightFakePlayer extends FakePlayer {
        private FlightFakePlayer(ServerLevel level) {
            super(level, new GameProfile(
                    UUID.fromString("637c42c9-f7f6-4d42-b44c-40ded65e760f"),
                    "ARCEFlightTest"
            ));
        }
    }

    private static void assertUnchangedSecurityState(
            GameTestHelper helper,
            ServerLevel earth,
            RocketEntity rocket,
            long expectedFuel,
            String request
    ) {
        helper.assertTrue(
                rocket.flightData().orElseThrow().state() == RocketFlightState.FUELED,
                request + " changed rocket state"
        );
        helper.assertTrue(
                rocket.flightData().orElseThrow().fuel().amount() == expectedFuel,
                request + " changed rocket fuel"
        );
        helper.assertTrue(
                RocketTransferSavedData.get(earth.getServer()).entries().isEmpty(),
                request + " created a transfer journal"
        );
    }

}
