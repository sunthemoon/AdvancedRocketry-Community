package io.github.sunthemoon.advancedrocketrycommunity.rocket.server;

import static io.github.sunthemoon.advancedrocketrycommunity.rocket.server.BayPassengerExitGameTestFixture.Case.*;
import io.github.sunthemoon.advancedrocketrycommunity.ModIdentity;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/** Mapped A1 adapter evidence, not packaged S1/S2 or two-client V2 evidence. */
@GameTestHolder(ModIdentity.MOD_ID)
@PrefixGameTestTemplate(false)
public final class BayPassengerExitGameTests {
    private BayPassengerExitGameTests() { }
    @GameTest(template = "rocket_test", batch = "bay_exit_climb", timeoutTicks = 400)
    public static void climbContactUsesOnlyTheChargedSnapshot(GameTestHelper helper) { new BayPassengerExitGameTestFixture(helper, CLIMB).start(); }
    @GameTest(template = "rocket_test", batch = "bay_exit_contention_reverse", timeoutTicks = 400)
    public static void reversedRealExitOrderSwapsTheWinner(GameTestHelper helper) { new BayPassengerExitGameTestFixture(helper, CONTENTION_REVERSE).start(); }
    @GameTest(template = "rocket_test", batch = "bay_exit_one_box", timeoutTicks = 400)
    public static void onlyGroundBoxLeavesTheSecondExitWithoutSafeSpace(GameTestHelper helper) { new BayPassengerExitGameTestFixture(helper, ONE_BOX).start(); }
    @GameTest(template = "rocket_test", batch = "bay_exit_one_box_reverse", timeoutTicks = 400)
    public static void reversedOnlyBoxOrderSwapsTheWinner(GameTestHelper helper) { new BayPassengerExitGameTestFixture(helper, ONE_BOX_REVERSE).start(); }
    @GameTest(template = "rocket_test", batch = "bay_exit_last_unit", timeoutTicks = 400)
    public static void lastCandidateUnitLeavesTheSecondExitZeroWork(GameTestHelper helper) { new BayPassengerExitGameTestFixture(helper, LAST_UNIT).start(); }
    @GameTest(template = "rocket_test", batch = "bay_exit_last_unit_reverse", timeoutTicks = 400)
    public static void reversedLastUnitOrderSwapsTheWinner(GameTestHelper helper) { new BayPassengerExitGameTestFixture(helper, LAST_UNIT_REVERSE).start(); }
    @GameTest(template = "rocket_test", batch = "bay_exit_delayed_one_box", timeoutTicks = 400)
    public static void earlierDelayedCaptureDoesNotPreemptTheImmediateExit(GameTestHelper helper) { new BayPassengerExitGameTestFixture(helper, DELAYED_ONE_BOX).start(); }
    @GameTest(template = "rocket_test", batch = "bay_exit_delayed_last", timeoutTicks = 400)
    public static void delayedCaptureGetsZeroWorkAfterTheLastUnitWasUsed(GameTestHelper helper) { new BayPassengerExitGameTestFixture(helper, DELAYED_LAST).start(); }
    @GameTest(template = "rocket_test", batch = "bay_exit_fifo", timeoutTicks = 400)
    public static void twoHookBypassesDrainInCaptureOrder(GameTestHelper helper) { new BayPassengerExitGameTestFixture(helper, FIFO).start(); }
    @GameTest(template = "rocket_test", batch = "bay_exit_after_teleport", timeoutTicks = 400)
    public static void failedMountAfterRealTrustedTeleportNeverRestoresGrant(GameTestHelper helper) { new BayPassengerExitGameTestFixture(helper, AFTER_TELEPORT).start(); }
    @GameTest(template = "rocket_test", batch = "bay_exit_released_success", timeoutTicks = 400)
    public static void releasedPassengerMoveIsNotAVoluntaryExit(GameTestHelper helper) { new BayPassengerExitGameTestFixture(helper, RELEASED_SUCCESS).start(); }
    @GameTest(template = "rocket_test", batch = "bay_exit_reentrant_damage", timeoutTicks = 400)
    public static void reentrantFallAttackDoesNotConsumeLandingAuthority(GameTestHelper helper) { new BayPassengerExitGameTestFixture(helper, REENTRANT_DAMAGE).start(); }
    @GameTest(template = "rocket_test", batch = "bay_exit_gantry", timeoutTicks = 400)
    public static void loadedGantryPrecedesTheGroundAnchor(GameTestHelper helper) { new BayPassengerExitGameTestFixture(helper, GANTRY).start(); }
    @GameTest(template = "rocket_test", batch = "bay_exit_local", timeoutTicks = 400)
    public static void unassignedRiderGetsOnlyLocalPlacement(GameTestHelper helper) { new BayPassengerExitGameTestFixture(helper, LOCAL_ONLY).start(); }
    @GameTest(template = "rocket_test", batch = "bay_exit_remote", timeoutTicks = 400)
    public static void menuSenderNotAboardCannotExitAnotherRider(GameTestHelper helper) { new BayPassengerExitGameTestFixture(helper, REMOTE).start(); }
    @GameTest(template = "rocket_test", batch = "bay_exit_hazard", timeoutTicks = 400)
    public static void hazardousPlatformIsNotSafePlacement(GameTestHelper helper) { new BayPassengerExitGameTestFixture(helper, HAZARD).start(); }
    @GameTest(template = "rocket_test", batch = "bay_exit_headroom", timeoutTicks = 400)
    public static void obstructedHeadroomIsNotSafePlacement(GameTestHelper helper) { new BayPassengerExitGameTestFixture(helper, HEADROOM).start(); }
    @GameTest(template = "rocket_test", batch = "bay_exit_partial", timeoutTicks = 400)
    public static void partialSupportIsNotSafePlacement(GameTestHelper helper) { new BayPassengerExitGameTestFixture(helper, PARTIAL).start(); }
    @GameTest(template = "rocket_test", batch = "bay_exit_budget", timeoutTicks = 400)
    public static void exhaustedSharedBudgetSpendsNoAdditionalProbe(GameTestHelper helper) { new BayPassengerExitGameTestFixture(helper, BUDGET).start(); }
    @GameTest(template = "rocket_test", batch = "bay_exit_contention", timeoutTicks = 400)
    public static void twoExitsChooseTheExactNextFreeAnchor(GameTestHelper helper) { new BayPassengerExitGameTestFixture(helper, CONTENTION).start(); }
    @GameTest(template = "rocket_test", batch = "bay_exit_nonfall_landing", timeoutTicks = 400)
    public static void nonFallCauseInsideVanillaLandingIsNotProtected(GameTestHelper helper) { new BayPassengerExitGameTestFixture(helper, NON_FALL_LANDING).start(); }
    @GameTest(template = "rocket_test", batch = "bay_exit_cancelled_fall", timeoutTicks = 400)
    public static void cancelledFallClosesTheOffer(GameTestHelper helper) { new BayPassengerExitGameTestFixture(helper, CANCELLED_FALL).start(); }
    @GameTest(template = "rocket_test", batch = "bay_exit_fluid", timeoutTicks = 400)
    public static void firstFluidContactClosesTheOffer(GameTestHelper helper) { new BayPassengerExitGameTestFixture(helper, FLUID).start(); }
    @GameTest(template = "rocket_test", batch = "bay_exit_0", timeoutTicks = 400)
    public static void heldSourceSelectsCheckedGroundWithoutLoading(GameTestHelper helper) { new BayPassengerExitGameTestFixture(helper, SAFE).start(); }
    @GameTest(template = "rocket_test", batch = "bay_exit_1", timeoutTicks = 400)
    public static void heldMenuLeaveRetainsAssignment(GameTestHelper helper) { new BayPassengerExitGameTestFixture(helper, MENU).start(); }
    @GameTest(template = "rocket_test", batch = "bay_exit_2", timeoutTicks = 400)
    public static void crewUsesTheSameSelfExit(GameTestHelper helper) { new BayPassengerExitGameTestFixture(helper, CREW).start(); }
    @GameTest(template = "rocket_test", batch = "bay_exit_3", timeoutTicks = 400)
    public static void cancelledDetachHasNoPlacementOrHistory(GameTestHelper helper) { new BayPassengerExitGameTestFixture(helper, CANCELLED).start(); }
    @GameTest(template = "rocket_test", batch = "bay_exit_4", timeoutTicks = 400)
    public static void hookBypassCompletesAtEnd(GameTestHelper helper) { new BayPassengerExitGameTestFixture(helper, BYPASS).start(); }
    @GameTest(template = "rocket_test", batch = "bay_exit_5", timeoutTicks = 400)
    public static void delayedExitCannotUndoNewerTeleport(GameTestHelper helper) { new BayPassengerExitGameTestFixture(helper, STALE_BYPASS).start(); }
    @GameTest(template = "rocket_test", batch = "bay_exit_6", timeoutTicks = 400)
    public static void bay_exit_ordinary_fall_only(GameTestHelper helper) { new BayPassengerExitGameTestFixture(helper, ORDINARY).start(); }
    @GameTest(template = "rocket_test", batch = "bay_exit_7", timeoutTicks = 400)
    public static void bay_exit_dripstone_damage_unchanged(GameTestHelper helper) { new BayPassengerExitGameTestFixture(helper, DRIPSTONE).start(); }
    @GameTest(template = "rocket_test", batch = "bay_exit_8", timeoutTicks = 400)
    public static void bay_exit_non_fall_cause_fall_damage_unchanged(GameTestHelper helper) { new BayPassengerExitGameTestFixture(helper, NON_FALL).start(); }
    @GameTest(template = "rocket_test", batch = "bay_exit_9", timeoutTicks = 400)
    public static void directFallDamageIsNotALanding(GameTestHelper helper) { new BayPassengerExitGameTestFixture(helper, DIRECT_FALL).start(); }
    @GameTest(template = "rocket_test", batch = "bay_exit_10", timeoutTicks = 400)
    public static void airborneCauseFallDamageDoesNotGrant(GameTestHelper helper) { new BayPassengerExitGameTestFixture(helper, AIRBORNE_CAUSE).start(); }
    @GameTest(template = "rocket_test", batch = "bay_exit_11", timeoutTicks = 400)
    public static void zeroDamageLandingCannotProtectLaterSameTickFall(GameTestHelper helper) { new BayPassengerExitGameTestFixture(helper, ZERO_LANDING).start(); }
    @GameTest(template = "rocket_test", batch = "bay_exit_12", timeoutTicks = 400)
    public static void bay_exit_inside_radius_teleport_revokes(GameTestHelper helper) { new BayPassengerExitGameTestFixture(helper, TELEPORT).start(); }
    @GameTest(template = "rocket_test", batch = "bay_exit_13", timeoutTicks = 400)
    public static void bay_exit_below_ceiling_upward_reposition_revokes(GameTestHelper helper) { new BayPassengerExitGameTestFixture(helper, UPWARD).start(); }
    @GameTest(template = "rocket_test", batch = "bay_exit_14", timeoutTicks = 400)
    public static void bay_exit_transfer_before_mount_failure_revokes(GameTestHelper helper) { new BayPassengerExitGameTestFixture(helper, FAILED_MOVE).start(); }
    @GameTest(template = "rocket_test", batch = "bay_exit_15", timeoutTicks = 400)
    public static void releasedMovePreflightFailureRevokes(GameTestHelper helper) { new BayPassengerExitGameTestFixture(helper, RELEASED_MOVE).start(); }
    @GameTest(template = "rocket_test", batch = "bay_exit_16", timeoutTicks = 400)
    public static void bay_exit_history_t_a_t_b_t_a(GameTestHelper helper) { new BayPassengerExitGameTestFixture(helper, HISTORY).start(); }
    @GameTest(template = "rocket_test", batch = "bay_exit_17", timeoutTicks = 400)
    public static void bay_exit_history_logout_new_instance(GameTestHelper helper) { new BayPassengerExitGameTestFixture(helper, LOGOUT).start(); }
    @GameTest(template = "rocket_test", batch = "bay_exit_18", timeoutTicks = 400)
    public static void bay_exit_history_death_clone_reentry(GameTestHelper helper) { new BayPassengerExitGameTestFixture(helper, DEATH).start(); }
    @GameTest(template = "rocket_test", batch = "bay_exit_19", timeoutTicks = 400)
    public static void firstDeadlineExpiresWithoutHistoryCleanup(GameTestHelper helper) { new BayPassengerExitGameTestFixture(helper, EXPIRY).start(); }
    @GameTest(template = "rocket_test", batch = "bay_exit_20", timeoutTicks = 400)
    public static void absentColumnRefusesBeforeAnyBlockRead(GameTestHelper helper) { new BayPassengerExitGameTestFixture(helper, UNLOADED).start(); }
}
