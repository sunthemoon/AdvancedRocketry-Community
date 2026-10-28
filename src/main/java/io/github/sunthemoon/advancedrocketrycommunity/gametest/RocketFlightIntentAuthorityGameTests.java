package io.github.sunthemoon.advancedrocketrycommunity.gametest;

import com.mojang.authlib.GameProfile;
import io.github.sunthemoon.advancedrocketrycommunity.ModIdentity;
import io.github.sunthemoon.advancedrocketrycommunity.celestial.CelestialIds;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.entity.RocketEntity;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketFlightAction;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketFlightData;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketFlightRequestCode;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.menu.RocketFlightMenu;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.server.RocketFlightMenuAuthority;
import io.github.sunthemoon.advancedrocketrycommunity.travel.model.TravelTarget;
import io.netty.channel.embedded.EmbeddedChannel;
import java.util.Locale;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.level.GameType;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/**
 * W182 (finding W179-F3): the flight screen is the only client that sends LAUNCH, CANCEL, BOARD or LEAVE, so the
 * server serves each only to a connected, living, non-spectator player that is not a fake player and has that
 * rocket's flight menu open and valid, and answers every other sender UNAUTHORIZED. Each test uses one connected
 * player, the rocket's owner, so that only the checked condition changes between its steps (see {@link Step}). The
 * rocket is assembled and unfueled: a LAUNCH is served with {@code INVALID_STATE} (not launch-ready) and a CANCEL
 * with {@code INVALID_STATE} (no countdown), as the menu-authority matrix counts its CANCEL; BOARD and LEAVE succeed.
 * Three more tests (W182-R5) send every intent from a {@link StandIn} that holds a valid flight menu.
 */
@GameTestHolder(ModIdentity.MOD_ID)
@PrefixGameTestTemplate(false)
public final class RocketFlightIntentAuthorityGameTests {
    static final String BATCH = "flight_intent_authority";
    private static final BlockPos ROCKET = new BlockPos(8, 2, 8);

    private RocketFlightIntentAuthorityGameTests() {
    }

    /** The steps of every test, in order. */
    private enum Step {
        /** Positive control: with the flight menu open, the intent is served and the action itself answers. */
        CONTROL,
        /** The same player as a spectator, the menu still open: refused, and the flight data does not change. */
        SPECTATOR,
        /** The same player in survival without an open menu: refused, and the flight data does not change. */
        NO_MENU,
        /** With the menu open again the intent is served, so the refusals did not come from the rocket's state. */
        FINAL_CONTROL;

        boolean served() {
            return this == CONTROL || this == FINAL_CONTROL;
        }
    }

    @GameTest(template = "rocket_test", batch = BATCH, timeoutTicks = 40)
    public static void launchIsServedOnlyFromTheOpenFlightMenuOfAnAdmittedPlayer(GameTestHelper helper) {
        run(helper, RocketFlightAction.LAUNCH, (fixture, step) -> fixture.expect(step, "launch",
                fixture.intent(RocketFlightAction.LAUNCH, new TravelTarget.BodySurface(CelestialIds.MOON_ID)),
                RocketFlightRequestCode.INVALID_STATE));
    }

    @GameTest(template = "rocket_test", batch = BATCH, timeoutTicks = 40)
    public static void cancelIsServedOnlyFromTheOpenFlightMenuOfAnAdmittedPlayer(GameTestHelper helper) {
        run(helper, RocketFlightAction.CANCEL, (fixture, step) -> fixture.expect(step, "cancel",
                fixture.intent(RocketFlightAction.CANCEL, fixture.currentTarget()), RocketFlightRequestCode.INVALID_STATE));
    }

    /** The seat is free at every step: a served boarding leaves again at once. */
    @GameTest(template = "rocket_test", batch = BATCH, timeoutTicks = 40)
    public static void boardIsServedOnlyFromTheOpenFlightMenuOfAnAdmittedPlayer(GameTestHelper helper) {
        run(helper, RocketFlightAction.BOARD, (fixture, step) -> {
            fixture.expect(step, "board", fixture.intent(RocketFlightAction.BOARD, fixture.currentTarget()),
                    RocketFlightRequestCode.SUCCESS);
            if (step.served()) {
                helper.assertTrue(fixture.owner.getVehicle() == fixture.rocket, "The served boarding did not mount");
                fixture.expect(step, "leave after boarding",
                        fixture.intent(RocketFlightAction.LEAVE, fixture.currentTarget()), RocketFlightRequestCode.SUCCESS);
            }
        });
    }

    /**
     * The owner holds the seat at every step: the control boards first and boards again after leaving. Switching to
     * spectator dismounts the player, as vanilla does, but keeps the seat, so the later steps still have one to leave.
     */
    @GameTest(template = "rocket_test", batch = BATCH, timeoutTicks = 40)
    public static void leaveIsServedOnlyFromTheOpenFlightMenuOfAnAdmittedPlayer(GameTestHelper helper) {
        run(helper, RocketFlightAction.LEAVE, (fixture, step) -> {
            if (step == Step.CONTROL) {
                fixture.expect(step, "board before leaving",
                        fixture.intent(RocketFlightAction.BOARD, fixture.currentTarget()), RocketFlightRequestCode.SUCCESS);
            }
            helper.assertTrue(fixture.flight().passengers().assignment(fixture.owner.getUUID()).isPresent(),
                    "The owner holds no seat to leave before the " + step);
            fixture.expect(step, "leave", fixture.intent(RocketFlightAction.LEAVE, fixture.currentTarget()),
                    RocketFlightRequestCode.SUCCESS);
            if (step.served()) {
                helper.assertTrue(fixture.owner.getVehicle() == null
                        && fixture.flight().passengers().assignment(fixture.owner.getUUID()).isEmpty(),
                        "The served leave kept the player mounted or seated");
            }
            if (step == Step.CONTROL) {
                fixture.expect(step, "board again",
                        fixture.intent(RocketFlightAction.BOARD, fixture.currentTarget()), RocketFlightRequestCode.SUCCESS);
            }
        });
    }

    /**
     * W182-R5: a server player other than an admitted client's, with the owner's profile, so it has the owner's rights
     * and seat, and with its own valid flight menu for the rocket. Each fails exactly one condition of the boundary.
     */
    private enum StandIn {
        /** A fake player with a live game connection: only the fake-player condition refuses it. */
        FAKE_PLAYER,
        /** A server player that never had a game connection. */
        UNCONNECTED,
        /** A server player whose game connection is closed, as a client that has just left leaves it. */
        DISCONNECTED;

        ServerPlayer create(ServerLevel level, GameProfile profile) {
            return switch (this) {
                case FAKE_PLAYER -> RocketFlightConsoleFixtures.connect(new FakePlayer(level, profile));
                case UNCONNECTED -> new ServerPlayer(level.getServer(), level, profile);
                case DISCONNECTED -> {
                    ServerPlayer player = new ServerPlayer(level.getServer(), level, profile);
                    Connection connection = new Connection(PacketFlow.SERVERBOUND);
                    EmbeddedChannel channel = new EmbeddedChannel(connection);
                    new ServerGamePacketListenerImpl(level.getServer(), connection, player);
                    channel.close();
                    yield player;
                }
            };
        }

        /** Whether {@code player} fails this condition and no other condition of the boundary but the menu's. */
        boolean holds(ServerPlayer player) {
            boolean fake = player instanceof FakePlayer;
            boolean connected = player.connection != null && player.connection.connection.isConnected();
            boolean failsThisCondition = switch (this) {
                case FAKE_PLAYER -> fake && connected;
                case UNCONNECTED -> !fake && player.connection == null;
                case DISCONNECTED -> !fake && player.connection != null && !connected;
            };
            return failsThisCondition && player.isAlive() && !player.isSpectator();
        }
    }

    @GameTest(template = "rocket_test", batch = BATCH, timeoutTicks = 40)
    public static void aConnectedFakePlayerWithAValidFlightMenuIsRefusedEveryIntent(GameTestHelper helper) {
        refuseStandIn(helper, StandIn.FAKE_PLAYER);
    }

    @GameTest(template = "rocket_test", batch = BATCH, timeoutTicks = 40)
    public static void aPlayerWithoutAConnectionWithAValidFlightMenuIsRefusedEveryIntent(GameTestHelper helper) {
        refuseStandIn(helper, StandIn.UNCONNECTED);
    }

    @GameTest(template = "rocket_test", batch = BATCH, timeoutTicks = 40)
    public static void aPlayerWithAClosedConnectionWithAValidFlightMenuIsRefusedEveryIntent(GameTestHelper helper) {
        refuseStandIn(helper, StandIn.DISCONNECTED);
    }

    /**
     * W182-R5. Positive control: the joined owner's CANCEL and BOARD are served from its open menu, so the owner holds
     * the seat. The stand-in then sends every intent from its own menu, asserted valid right before each one, so the
     * boundary's menu condition passes; each intent is refused UNAUTHORIZED and leaves the flight data and the owner's
     * seat as they were. Final control: the owner's LEAVE is served.
     */
    private static void refuseStandIn(GameTestHelper helper, StandIn kind) {
        ServerLevel level = helper.getLevel();
        UUID ownerId = UUID.randomUUID();
        RocketEntity rocket = FluidFuelLoaderFixtures.rocket(helper, ROCKET, ownerId);
        ServerPlayer owner = MenuAuthorityMatrix.join(level.getServer(), ownerId,
                "w182r5" + kind.name().toLowerCase(Locale.ROOT).substring(0, 4), level, rocket.blockPosition().north(2));
        Fixture fixture = new Fixture(helper, rocket, owner);
        ServerPlayer standIn = null;
        try {
            helper.assertTrue(RocketFlightConsoleFixtures.open(owner, rocket), "The owner could not open a valid flight menu");
            fixture.expect(Step.CONTROL, "cancel", fixture.intent(RocketFlightAction.CANCEL, fixture.currentTarget()),
                    RocketFlightRequestCode.INVALID_STATE);
            fixture.expect(Step.CONTROL, "board", fixture.intent(RocketFlightAction.BOARD, fixture.currentTarget()),
                    RocketFlightRequestCode.SUCCESS);
            helper.assertTrue(owner.getVehicle() == rocket, "The owner's boarding did not mount");

            standIn = kind.create(level, owner.getGameProfile());
            // The ServerPlayer constructor binds the UUID's advancements to the newest player; give them back.
            level.getServer().getPlayerList().getPlayerAdvancements(owner);
            standIn.moveTo(owner.getX(), owner.getY(), owner.getZ(), 0.0F, 0.0F);
            RocketFlightMenu menu = new RocketFlightMenu(1, standIn.getInventory(), rocket);
            standIn.containerMenu = menu;
            helper.assertTrue(kind.holds(standIn), "The stand-in is not a " + kind);
            for (RocketFlightAction action : RocketFlightAction.values()) {
                helper.assertTrue(standIn.containerMenu == menu && menu.stillValid(standIn),
                        "The " + kind + "'s flight menu was not valid before its " + action);
                TravelTarget target = action == RocketFlightAction.LAUNCH
                        ? new TravelTarget.BodySurface(CelestialIds.MOON_ID) : fixture.currentTarget();
                RocketFlightData before = fixture.flight();
                RocketFlightRequestCode code = fixture.intent(standIn, action, target);
                helper.assertTrue(code == RocketFlightRequestCode.UNAUTHORIZED, "The " + kind + "'s " + action
                        + " was answered " + code + ", not UNAUTHORIZED");
                helper.assertTrue(fixture.flight().equals(before), "The " + kind + "'s refused " + action
                        + " changed the rocket's flight data");
            }
            helper.assertTrue(owner.getVehicle() == rocket, "The refused intents dismounted the owner");
            helper.assertTrue(RocketFlightConsoleFixtures.open(owner, rocket), "The owner's flight menu was not valid at the end");
            fixture.expect(Step.FINAL_CONTROL, "leave", fixture.intent(RocketFlightAction.LEAVE, fixture.currentTarget()),
                    RocketFlightRequestCode.SUCCESS);
            helper.assertTrue(owner.getVehicle() == null, "The owner's served leave kept the owner mounted");
            helper.succeed();
        } finally {
            if (standIn != null) {
                standIn.containerMenu = standIn.inventoryMenu;
            }
            owner.stopRiding();
            owner.closeContainer();
            level.getServer().getPlayerList().remove(owner);
            rocket.discard();
        }
    }

    @FunctionalInterface
    private interface Intent {
        void run(Fixture fixture, Step step);
    }

    private static void run(GameTestHelper helper, RocketFlightAction action, Intent intent) {
        ServerLevel level = helper.getLevel();
        UUID ownerId = UUID.randomUUID();
        RocketEntity rocket = FluidFuelLoaderFixtures.rocket(helper, ROCKET, ownerId);
        ServerPlayer owner = MenuAuthorityMatrix.join(level.getServer(), ownerId,
                "w182" + action.name().toLowerCase(Locale.ROOT), level, rocket.blockPosition().north(2));
        Fixture fixture = new Fixture(helper, rocket, owner);
        try {
            for (Step step : Step.values()) {
                if (step == Step.NO_MENU) {
                    owner.closeContainer();
                } else {
                    helper.assertTrue(RocketFlightConsoleFixtures.open(owner, rocket),
                            "The owner could not open a valid flight menu before the " + step);
                }
                if (step == Step.SPECTATOR) {
                    owner.setGameMode(GameType.SPECTATOR);
                }
                RocketFlightData before = fixture.flight();
                intent.run(fixture, step);
                helper.assertTrue(step.served() || fixture.flight().equals(before),
                        "The refused " + step + " changed the rocket's flight data");
                owner.setGameMode(GameType.SURVIVAL);
            }
            helper.succeed();
        } finally {
            owner.stopRiding();
            owner.closeContainer();
            level.getServer().getPlayerList().remove(owner);
            rocket.discard();
        }
    }

    private static final class Fixture {
        final GameTestHelper helper;
        final RocketEntity rocket;
        final ServerPlayer owner;

        Fixture(GameTestHelper helper, RocketEntity rocket, ServerPlayer owner) {
            this.helper = helper;
            this.rocket = rocket;
            this.owner = owner;
        }

        RocketFlightRequestCode intent(RocketFlightAction action, TravelTarget target) {
            return intent(owner, action, target);
        }

        RocketFlightRequestCode intent(ServerPlayer actor, RocketFlightAction action, TravelTarget target) {
            return RocketFlightMenuAuthority.request(actor, rocket, action, target);
        }

        TravelTarget currentTarget() {
            return flight().currentTarget().orElseThrow();
        }

        RocketFlightData flight() {
            return rocket.flightData().orElseThrow();
        }

        /** A served step must get {@code served}; every other step UNAUTHORIZED. */
        void expect(Step step, String intent, RocketFlightRequestCode code, RocketFlightRequestCode served) {
            RocketFlightRequestCode expected = step.served() ? served : RocketFlightRequestCode.UNAUTHORIZED;
            helper.assertTrue(code == expected, "At the " + step + " step the " + intent + " was answered " + code
                    + ", not " + expected);
        }
    }
}
