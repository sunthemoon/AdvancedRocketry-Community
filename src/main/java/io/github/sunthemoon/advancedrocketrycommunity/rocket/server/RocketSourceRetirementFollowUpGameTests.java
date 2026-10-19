package io.github.sunthemoon.advancedrocketrycommunity.rocket.server;

import io.github.sunthemoon.advancedrocketrycommunity.ModIdentity;
import io.github.sunthemoon.advancedrocketrycommunity.celestial.CelestialIds;
import io.github.sunthemoon.advancedrocketrycommunity.resourceescrow.EscrowRuntime;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.entity.RocketEntity;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketTransferRecoveryAction;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.TicketType;
import net.minecraft.world.level.ChunkPos;
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
            void retired() { assertRecovered(this, RocketTransferRecoveryAction.KEEP_SOURCE); }
        }.start();
    }

    /** W98-R9: a /save-off issued after the survivor's capture holds the discarded copy's capture, so T stays. */
    @GameTest(template = "rocket_test", batch = BATCH, timeoutTicks = 400)
    public static void aSaveOffDuringAnAttemptHoldsItsNextCapture(GameTestHelper helper) {
        new RocketSourceRetirementGameTests.Case(helper) {
            ServerLevel space; ChunkPos forced; RocketEntity destination; boolean wasOff, began; int held;
            ServerLevel level() { return space; }
            boolean ready() {
                if (space == null) {
                    space = helper.getLevel().getServer().getLevel(CelestialIds.SPACE_LEVEL);
                    helper.assertTrue(space != null, "No Space Level");
                    // The survivor and the discarded copy in two neighbouring chunks, both entity-ticking under one ticket.
                    origin = new BlockPos(2116, 80, 2120); target = new BlockPos(2132, 80, 2120);
                    forced = new ChunkPos(origin);
                    space.getChunkSource().addRegionTicket(TicketType.FORCED, forced, 3, forced);
                }
                return space.isPositionEntityTicking(origin) && space.isPositionEntityTicking(target);
            }
            void act() {
                source = flight.spawnInFlight(helper, space); destination = flight.spawnDestination(helper, space);
                prepare(false);
                completion = false; wasOff = space.noSave;
                helper.assertTrue(!flight.originSlot().equals(flight.destinationSlot()), "The census needs a second slot");
            }
            boolean before() {
                var server = server();
                if (!began) {
                    long now = server.getTickCount(); var barrier = EscrowRuntime.of(server).barrier();
                    String id = space.dimension().location().toString();
                    // Start only on a tick whose capture is free, so the survivor's slot is captured at once.
                    if (!barrier.availableOrdinary(id, now)) { return false; }
                    var result = recovery().recoverById(server, journal, record.transferId());
                    helper.assertTrue(result.status() == RocketTransferRecoveryService.Status.RETRY_LATER, "Recovery reported " + result.status());
                    if (!recovery().retirements().pending(record.logicalRocketId())) { return false; }
                    helper.assertTrue(!barrier.availableOrdinary(id, now) && destination.isRemoved(),
                            "The attempt did not capture the survivor's slot at once");
                    began = true;
                    // The discarded copy's slot can be captured on the next tick at the earliest.
                    space.noSave = true;
                    return false;
                }
                if (held++ < 40) {
                    helper.assertTrue(journal.find(record.transferId()).orElse(null) == record,
                            "A save-off during the attempt still let its next capture remove the record (W98-R9)");
                    helper.assertTrue(held < 2 || journal.actionable(record.logicalRocketId()) && journal.clean(),
                            "A return held by save-off kept its reservation");
                    return false;
                }
                space.noSave = wasOff;
                return true;
            }
            void retired() {
                helper.assertTrue(RocketSourceRetirementFixtures.diskCopies(space, flight.destinationSlot(), record.logicalRocketId()) == 0,
                        "The discarded destination copy is still saved");
                assertRecovered(this, RocketTransferRecoveryAction.REMOVE_DESTINATION_KEEP_SOURCE);
            }
            void restore() {
                if (space != null) { space.noSave = wasOff; space.getChunkSource().removeRegionTicket(TicketType.FORCED, forced, 3, forced); }
            }
        }.start();
    }

    private static void assertRecovered(RocketSourceRetirementGameTests.Case test, RocketTransferRecoveryAction action) {
        var report = test.recovery().recoverById(test.server(), test.journal, test.record.transferId());
        test.helper.assertTrue(report.status() == RocketTransferRecoveryService.Status.RECOVERED && report.action() == action,
                "The completed recovery was not reported: " + report.status());
    }
}
