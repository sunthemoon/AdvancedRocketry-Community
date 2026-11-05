package io.github.sunthemoon.advancedrocketrycommunity.rocket.server;

import io.github.sunthemoon.advancedrocketrycommunity.ModIdentity;
import net.minecraft.gametest.framework.AfterBatch;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import static io.github.sunthemoon.advancedrocketrycommunity.rocket.server.RocketStaleSeatFixture.Case.*;

/** Native departure stimuli and durable-seat reconciliation, not a real-client or native-restart claim. */
@GameTestHolder(ModIdentity.MOD_ID)
@PrefixGameTestTemplate(false)
public final class RocketStaleSeatGameTests {
    private RocketStaleSeatGameTests() { }

    @GameTest(template = "rocket_test", batch = "rocket_stale_seats", timeoutTicks = 240)
    public static void deathAndRespawnReleaseTheStationarySeat(GameTestHelper h) { new RocketStaleSeatFixture(h, DEATH).start(); }
    @GameTest(template = "rocket_test", batch = "rocket_stale_seats", timeoutTicks = 240)
    public static void teleportCommandReleasesTheStationarySeat(GameTestHelper h) { new RocketStaleSeatFixture(h, TELEPORT).start(); }
    @GameTest(template = "rocket_test", batch = "rocket_stale_seats", timeoutTicks = 240)
    public static void spectatorCommandReleasesTheStationarySeat(GameTestHelper h) { new RocketStaleSeatFixture(h, SPECTATOR).start(); }
    @GameTest(template = "rocket_test", batch = "rocket_stale_seats", timeoutTicks = 240)
    public static void rideDismountCommandReleasesTheStationarySeat(GameTestHelper h) { new RocketStaleSeatFixture(h, DISMOUNT).start(); }
    @GameTest(template = "rocket_test", batch = "rocket_stale_seats", timeoutTicks = 240)
    public static void enderPearlImpactReleasesTheStationarySeat(GameTestHelper h) { new RocketStaleSeatFixture(h, PEARL).start(); }
    @GameTest(template = "rocket_test", batch = "rocket_stale_seats", timeoutTicks = 240)
    public static void legacySavedOnlineUnriddenSeatIsReleased(GameTestHelper h) { new RocketStaleSeatFixture(h, LEGACY).start(); }
    @GameTest(template = "rocket_test", batch = "rocket_stale_seats", timeoutTicks = 240)
    public static void landedLogoutKeepsSeatAndLoginRestoresItBeforeCleanup(GameTestHelper h) { new RocketStaleSeatFixture(h, OFFLINE).start(); }
    @GameTest(template = "rocket_test", batch = "rocket_stale_seats", timeoutTicks = 240)
    public static void internalMoveKeepsSeatUntilItsScopeCloses(GameTestHelper h) { new RocketStaleSeatFixture(h, INTERNAL).start(); }
    @GameTest(template = "rocket_test", batch = "rocket_stale_pending_rejoin", timeoutTicks = 240)
    public static void requiredRejoinKeepsSeatUntilObligationEnds(GameTestHelper h) { new RocketStaleSeatFixture(h, REJOIN_PENDING).start(); }
    @GameTest(template = "rocket_test", batch = "rocket_stale_active_rejoin", timeoutTicks = 240)
    public static void activeRejoinKeepsSeatUntilGuardEnds(GameTestHelper h) { new RocketStaleSeatFixture(h, REJOIN_ACTIVE).start(); }
    @GameTest(template = "rocket_test", batch = "rocket_stale_frozen", timeoutTicks = 240)
    public static void frozenRootKeepsAbsentSeatWithoutThrowing(GameTestHelper h) { new RocketStaleSeatFixture(h, FROZEN).start(); }
    @GameTest(template = "rocket_test", batch = "rocket_stale_seats", timeoutTicks = 240)
    public static void activeTransferKeepsAbsentSeatUntilTransferEnds(GameTestHelper h) { new RocketStaleSeatFixture(h, ACTIVE_TRANSFER).start(); }
    @GameTest(template = "rocket_test", batch = "rocket_stale_seats", timeoutTicks = 240)
    public static void inFlightAbsenceKeepsSeatUntilStationary(GameTestHelper h) { new RocketStaleSeatFixture(h, IN_FLIGHT).start(); }
    @GameTest(template = "rocket_test", batch = "rocket_stale_seats", timeoutTicks = 240)
    public static void releasingAbsentSeatDoesNotDismountAnotherVehicle(GameTestHelper h) { new RocketStaleSeatFixture(h, OTHER_VEHICLE).start(); }
    @GameTest(template = "rocket_test", batch = "rocket_stale_seats", timeoutTicks = 240)
    public static void pendingReconnectRunsBeforeAbsenceCleanup(GameTestHelper h) { new RocketStaleSeatFixture(h, RECONNECT_PENDING).start(); }
    @GameTest(template = "rocket_test", batch = "rocket_stale_seats", timeoutTicks = 240)
    public static void ridingAgainResetsTheAbsenceGrace(GameTestHelper h) { new RocketStaleSeatFixture(h, RETURNED).start(); }
    @GameTest(template = "rocket_test", batch = "rocket_stale_seats", timeoutTicks = 240)
    public static void heldBayExitKeepsSeatUntilHoldEnds(GameTestHelper h) { new RocketStaleSeatFixture(h, HELD).start(); }
    @GameTest(template = "rocket_test", batch = "rocket_stale_seats", timeoutTicks = 240)
    public static void operatorReleasesOneOfflineNamedSeatAndPersistsIt(GameTestHelper h) { new RocketStaleSeatFixture(h, COMMAND).start(); }

    @AfterBatch(batch = "rocket_stale_seats")
    public static void closeMain(ServerLevel level) { RocketStaleSeatFixture.closeOpen(level); }
    @AfterBatch(batch = "rocket_stale_pending_rejoin")
    public static void closePending(ServerLevel level) { RocketStaleSeatFixture.closeOpen(level); }
    @AfterBatch(batch = "rocket_stale_active_rejoin")
    public static void closeActive(ServerLevel level) { RocketStaleSeatFixture.closeOpen(level); }
    @AfterBatch(batch = "rocket_stale_frozen")
    public static void closeFrozen(ServerLevel level) { RocketStaleSeatFixture.closeOpen(level); }
}
