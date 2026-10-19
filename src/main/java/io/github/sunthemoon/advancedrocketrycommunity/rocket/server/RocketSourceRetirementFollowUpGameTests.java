package io.github.sunthemoon.advancedrocketrycommunity.rocket.server;

import io.github.sunthemoon.advancedrocketrycommunity.ModIdentity;
import io.github.sunthemoon.advancedrocketrycommunity.resourceescrow.EscrowRuntime;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/**
 * W98F: the W98 review's follow-ups to the ordinary source-retirement coordinator (P1-N3 leaf N3-A1, Root decision R35).
 * Each case reuses W98's detached fixture, so the record still leaves RAM only after its survivor's slot is on disk.
 */
@GameTestHolder(ModIdentity.MOD_ID)
@PrefixGameTestTemplate(false)
public final class RocketSourceRetirementFollowUpGameTests {
    private static final String BATCH = "ordinary_source_retirement_followups";
    private RocketSourceRetirementFollowUpGameTests() { }

    /** W98-R5: an attempt whose first capture cannot run this tick releases the reservation it was given and waits. */
    @GameTest(template = "rocket_test", batch = BATCH, timeoutTicks = 400)
    public static void aBusyCaptureBudgetReleasesTheFirstAttemptsReservation(GameTestHelper helper) {
        new RocketSourceRetirementGameTests.Case(helper) {
            boolean waited;
            void act() {
                source = flight.spawnInFlight(helper, level()); prepare(false);
                completion = false;
            }
            boolean before() {
                if (waited) { return true; }
                var server = server(); long now = server.getTickCount();
                var runtime = EscrowRuntime.of(server); var barrier = runtime.barrier();
                String id = level().dimension().location().toString();
                // Take this tick's one capture, unless another test already has: the survivor's capture cannot run now.
                if (barrier.availableOrdinary(id, now)) {
                    barrier.beginOrdinary(runtime.store(level()), id, flight.originSlot(), root -> true, now, label -> { },
                            result -> { if (result.proof() != null) { result.proof().release(); } });
                }
                helper.assertTrue(!barrier.availableOrdinary(id, now), "The fixture could not occupy this tick's capture");
                var result = recovery().recoverById(server, journal, record.transferId());
                helper.assertTrue(result.status() == RocketTransferRecoveryService.Status.RETRY_LATER, "Recovery reported " + result.status());
                if (!recovery().retirements().pending(record.logicalRocketId())) { return false; }
                waited = true;
                assertReturnedAndHeld(false);
                helper.assertTrue(journal.actionable(record.logicalRocketId()) && journal.clean(),
                        "A return waiting for capture budget kept its reservation (W98-R5)");
                return false;
            }
            void retired() {
                var report = recovery().recoverById(server(), journal, record.transferId());
                helper.assertTrue(report.status() == RocketTransferRecoveryService.Status.RECOVERED
                        && report.action() == io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketTransferRecoveryAction.KEEP_SOURCE,
                        "The completed recovery was not reported: " + report.status());
            }
        }.start();
    }
}
