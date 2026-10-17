package io.github.sunthemoon.advancedrocketrycommunity.gametest;

import static io.github.sunthemoon.advancedrocketrycommunity.gametest.RocketFlightGameTestFixtures.clearSnapshotBlocks;
import static io.github.sunthemoon.advancedrocketrycommunity.gametest.RocketFlightGameTestFixtures.clearTransferJournal;
import static io.github.sunthemoon.advancedrocketrycommunity.gametest.RocketFlightGameTestFixtures.placeLegalRocket;
import static io.github.sunthemoon.advancedrocketrycommunity.gametest.RocketFlightGameTestFixtures.successfulSnapshot;

import io.github.sunthemoon.advancedrocketrycommunity.AdvancedRocketryCommunity;
import io.github.sunthemoon.advancedrocketrycommunity.ModIdentity;
import io.github.sunthemoon.advancedrocketrycommunity.celestial.CelestialIds;
import io.github.sunthemoon.advancedrocketrycommunity.persistence.migration.CheckedSavedDataFile;
import io.github.sunthemoon.advancedrocketrycommunity.persistence.migration.CheckedSavedDataPolicy;
import io.github.sunthemoon.advancedrocketrycommunity.persistence.migration.ManagedSavedDataType;
import io.github.sunthemoon.advancedrocketrycommunity.progression.classic.firstevent.FirstEvent;
import io.github.sunthemoon.advancedrocketrycommunity.registry.ModBlocks;
import io.github.sunthemoon.advancedrocketrycommunity.registry.ModEntities;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.entity.RocketEntity;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketFlightData;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketFlightPlan;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketFuelState;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketPropulsionQuoter;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketTransferPhase;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketTransferRecord;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketTransferRecoveryReport;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.persistence.RocketJournalGameTestSupport;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.persistence.RocketTransferSavedData;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.fuel.RocketFluidFuelCatalog;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.model.RocketPosition;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.model.RocketPropulsion;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.persistence.RocketTransactionSavedData;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.server.RocketLandedDisassemblyGameTestSupport;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.server.RocketLandedDisassemblyGameTestSupport.Requester;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.server.RocketRuntime;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.transaction.RocketFailureInjector;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.transaction.RocketFailurePoint;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.transaction.RocketRegion;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.transaction.RocketTransactionAbortException;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.validation.RocketValidationCode;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.BooleanSupplier;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.TicketType;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/**
 * W85 section 7 A1 (P1-F8, Design M): real disassembly, transaction and transfer services with a landed arrival on the
 * Moon. The world-journal batches use the session's services and the fixture drain; the writer batch drives an
 * isolated checked journal file whose writes, failures and acknowledgements the test performs, as in W77.
 */
@GameTestHolder(AdvancedRocketryCommunity.MOD_ID)
@PrefixGameTestTemplate(false)
public final class RocketLandedDisassemblyGameTests {
    private static final TicketType<ChunkPos> TICKET = TicketType.create("arce_landed_disassembly_test",
            Comparator.comparingLong(ChunkPos::toLong), 200);
    private static final BlockPos ORIGIN = new BlockPos(3, 2, 3);
    private static final String CODE = "validation.advancedrocketrycommunity.rocket.";
    private static final ManagedSavedDataType TYPE = ManagedSavedDataType.ROCKET_TRANSFERS;

    private RocketLandedDisassemblyGameTests() { }

    /** G1, G4, G5, G10, G14, G15 and G16 through the player path and the release-test hook, world journal. */
    @GameTest(template = "rocket_test", batch = "landed_disassembly_requests", timeoutTicks = 1200)
    public static void landedRequestsKeepTheClaimUntilRestorationAndAnswerPending(GameTestHelper helper) {
        var script = new Script(helper);
        script.landed(false, false, RocketLandedDisassemblyGameTests::obstructedThenRestoredOnTheFirstRequest);
        script.landed(false, false, RocketLandedDisassemblyGameTests::releaseTestHookAnswersPendingThenRestores);
        script.landed(true, false, RocketLandedDisassemblyGameTests::fueledConsentAndChangedFuel);
        script.landed(true, false, RocketLandedDisassemblyGameTests::repeatedRequestsOnlyReportPending);
        script.landed(false, false, RocketLandedDisassemblyGameTests::destroyedArrivalIsARefusedInsert);
        script.start();
    }

    /** G2, G2b, G3 and G3b: failures after the release, world journal and an injected v0.5 fault. */
    @GameTest(template = "rocket_test", batch = "landed_disassembly_rollbacks", timeoutTicks = 1200)
    public static void cleanFailuresWriteTheIdenticalRecordBackAndUncertainOnesNever(GameTestHelper helper) {
        var script = new Script(helper);
        script.landed(false, false, RocketLandedDisassemblyGameTests::obstructionAfterTheRequestWritesBack);
        script.landed(false, false, RocketLandedDisassemblyGameTests::completeRollbackWritesBack);
        script.landed(false, false, RocketLandedDisassemblyGameTests::failedRollbackIsOwnedByTheTransactionRecovery);
        script.start();
    }

    /** G6, G7, G8, G11, G12, G13 and G13b against an isolated checked journal written by the test. */
    @GameTest(template = "rocket_test", batch = "landed_disassembly_writer", timeoutTicks = 2400)
    public static void lateDiscardedAndFailingWritesNeverRestoreLateOrReleaseUnacknowledgedState(GameTestHelper helper) {
        var script = new Script(helper);
        script.landed(false, true, RocketLandedDisassemblyGameTests::writerHeldPastTheLifetime);
        script.landed(false, true, RocketLandedDisassemblyGameTests::fullEffectQueueRefusesBeforeRelease);
        script.landed(false, true, RocketLandedDisassemblyGameTests::unknownRemovalThenLaterGeneration);
        script.landed(false, true, RocketLandedDisassemblyGameTests::committedRemovalWithADelayedSuccessor);
        script.landed(false, true, RocketLandedDisassemblyGameTests::unknownMovedRemovalRetriedLate);
        script.landed(false, true, (s, a) -> failingWriteBack(s, a, false));
        script.landed(false, true, (s, a) -> failingWriteBack(s, a, true));
        script.start();
    }

    // ---- world journal: requests ----

    private static void obstructedThenRestoredOnTheFirstRequest(Script s, Arrival a) {
        s.then(() -> {
            var journal = a.journal();
            var owner = a.requester(a.owner);
            // G1: an obstruction at request answers its code and changes nothing.
            a.obstruct(1);
            long sequence = journal.mutationSequence();
            a.support.requestDisassembly(owner, a.rocket);
            s.check(code(owner, "target_occupied"), "Obstruction at request did not answer TARGET_OCCUPIED");
            s.check(journal.find(a.landed.transferId()).orElse(null) == a.landed && journal.mutationSequence() == sequence
                    && journal.acknowledgedCurrent() && !a.support.pending(a.rocket) && a.rocket.isAlive(),
                    "A predictable failure released or rewrote the landed record");
            s.check(a.support.launchReservations(journal).contains(a.region()), "A second flight could take T's region");
            a.clearObstruction(1);
            // G4 and G5: one request answers pending; the claim stays in RAM until the removal is acknowledged.
            a.support.requestDisassembly(owner, a.rocket);
            s.check(code(owner, "disassembly_pending") && a.support.pending(a.rocket)
                    && journal.find(a.landed.transferId()).isEmpty() && journal.mutationSequence() == sequence + 1,
                    "The first request did not release T into a pending entry");
            s.check(a.rocket.isAlive() && !a.anyRestored(), "A block or the entity changed before the acknowledgement");
            a.support.requestDisassembly(owner, a.rocket);
            s.check(code(owner, "disassembly_pending") && owner.offers == 0, "A repeated request was not only a status");
            s.check(!a.support.resourceMutationAllowed(a.rocket) && !RocketRuntime.resourceMutationAllowed(a.rocket),
                    "Refuelling or launch was allowed while the removal is pending");
            s.check(a.support.launchReservations(journal).contains(a.region())
                    && a.support.slotsInUse(journal) == journal.entries().size() + 1,
                    "P did not keep T's region and slot while T is absent from RAM");
            RocketJournalGameTestSupport.acknowledgeFixture(s.helper);
            s.check(a.rocket.isRemoved() && a.allRestored() && journal.find(a.landed.transferId()).isEmpty()
                    && !a.support.pending(a.rocket) && journal.mutationSequence() == sequence + 1,
                    "The acknowledged removal did not restore the rocket exactly once");
            s.check(!owner.actionBar.contains(CODE + "region_busy"), "The first request answered REGION_BUSY");
        });
    }

    private static void releaseTestHookAnswersPendingThenRestores(Script s, Arrival a) {
        s.then(() -> {
            var journal = a.journal();
            s.check(a.support.disassembleForReleaseTest(a.rocket, -1L) == RocketValidationCode.DISASSEMBLY_PENDING
                    && a.support.disassembleForReleaseTest(a.rocket, -1L) == RocketValidationCode.DISASSEMBLY_PENDING,
                    "The zero-fuel release-test hook did not answer pending twice");
            s.check(journal.find(a.landed.transferId()).isEmpty() && a.rocket.isAlive(), "The hook did not release T");
            RocketJournalGameTestSupport.acknowledgeFixture(s.helper);
            s.check(a.rocket.isRemoved() && a.allRestored() && !a.support.pending(a.rocket),
                    "The hook's acknowledged removal did not restore the rocket");
        });
    }

    private static void fueledConsentAndChangedFuel(Script s, Arrival a) {
        s.then(() -> {
            var journal = a.journal();
            var owner = a.requester(a.owner);
            long fuel = a.rocket.flightData().orElseThrow().fuel().totalAmount();
            s.check(fuel > 0, "The fueled fixture has no fuel");
            // G14: a fueled confirmation whose fuel changes before M2 is not restored; T is written back.
            a.support.requestDisassembly(owner, a.rocket);
            s.check(owner.offers == 1 && a.support.confirmDisassembly(owner, owner.token)
                    && code(owner, "disassembly_pending") && owner.discarded == 0, "Confirmed request was not pending");
            s.check(!a.support.confirmDisassembly(owner, owner.token)
                    && owner.messages.contains("message.advancedrocketrycommunity.rocket.disassembly_confirmation_invalid"),
                    "A replayed confirmation token was not invalid");
            var flight = a.rocket.flightData().orElseThrow();
            a.rocket.updateFlightData(flight.withFuel(flight.fuel().withFluidBank(false, ModIdentity.id("rocket_fuel"),
                    null, fuel - 1), a.moon.getGameTime()));
            RocketJournalGameTestSupport.drainFixture(s.helper);
            s.check(a.rocket.isAlive() && !a.anyRestored() && code(owner, "region_busy") && owner.discarded == 0,
                    "A changed arrival was restored or its fuel discarded");
            s.check(journal.find(a.landed.transferId()).orElse(null) == a.landed
                    && "WRITE_BACK".equals(a.support.stage(a.logical())) && !a.support.resourceMutationAllowed(a.rocket),
                    "The identical T was not written back with P holding the rocket");
            RocketJournalGameTestSupport.acknowledgeFixture(s.helper);
            s.check(!a.support.pending(a.rocket) && a.support.resourceMutationAllowed(a.rocket),
                    "The acknowledged write-back did not settle T and end P");
            // The next request works, and reports the exact consented disposal.
            long changed = a.rocket.flightData().orElseThrow().fuel().totalAmount();
            a.support.requestDisassembly(owner, a.rocket);
            s.check(owner.offers == 2 && a.support.confirmDisassembly(owner, owner.token), "The next request was refused");
            RocketJournalGameTestSupport.acknowledgeFixture(s.helper);
            s.check(a.rocket.isRemoved() && a.allRestored() && owner.discarded == changed
                    && owner.messages.contains("message.advancedrocketrycommunity.rocket.disassembly_fuel_discarded"),
                    "The confirmed disassembly did not restore and report its exact fuel");
        });
    }

    private static void repeatedRequestsOnlyReportPending(Script s, Arrival a) {
        s.then(() -> {
            var journal = a.journal();
            var owner = a.requester(a.owner);
            a.support.requestDisassembly(owner, a.rocket);
            s.check(a.support.confirmDisassembly(owner, owner.token), "Confirmed request was not accepted");
            long sequence = journal.mutationSequence();
            // G15: owner, hook, unauthorized and distant requests while P exists.
            a.support.requestDisassembly(owner, a.rocket);
            s.check(code(owner, "disassembly_pending") && owner.offers == 1, "The owner's repeat issued an offer");
            s.check(a.support.disassembleForReleaseTest(a.rocket, a.rocket.flightData().orElseThrow().fuel().totalAmount())
                    == RocketValidationCode.DISASSEMBLY_PENDING, "The hook's repeat was not pending");
            var stranger = a.requester(UUID.randomUUID());
            a.support.requestDisassembly(stranger, a.rocket);
            s.check(code(stranger, "unauthorized") && stranger.offers == 0, "An unauthorized player was not refused");
            var distant = a.requester(a.owner);
            distant.setPos(a.rocket.getX() + 64, a.rocket.getY(), a.rocket.getZ());
            a.support.requestDisassembly(distant, a.rocket);
            s.check(code(distant, "out_of_range") && distant.offers == 0, "A distant player was not refused");
            s.check(journal.mutationSequence() == sequence && "RELEASED".equals(a.support.stage(a.logical()))
                    && a.rocket.isAlive(), "A status request changed something");
            RocketJournalGameTestSupport.acknowledgeFixture(s.helper);
            s.check(a.rocket.isRemoved() && a.allRestored(), "The pending request did not complete");
        });
    }

    private static void destroyedArrivalIsARefusedInsert(Script s, Arrival a) {
        s.then(() -> {
            var journal = a.journal();
            var owner = a.requester(a.owner);
            a.support.requestDisassembly(owner, a.rocket);
            s.check(code(owner, "disassembly_pending"), "Request was not pending");
            // G16: E destroyed in a loaded section; no restoration, and a refused insert (reason=mismatch).
            a.rocket.discard();
            RocketJournalGameTestSupport.acknowledgeFixture(s.helper);
            s.check(!a.anyRestored() && journal.find(a.landed.transferId()).isEmpty() && !a.support.pending(a.rocket)
                    && code(owner, "region_busy"), "A destroyed arrival was restored or its record written back");
        });
    }

    // ---- world journal: failures after the release ----

    private static void obstructionAfterTheRequestWritesBack(Script s, Arrival a) {
        s.then(() -> {
            var journal = a.journal();
            var owner = a.requester(a.owner);
            CompoundTag before = a.diskRecord();
            a.support.requestDisassembly(owner, a.rocket);
            // G2: an obstruction placed between the request and the acknowledgement.
            a.obstruct(1);
            RocketJournalGameTestSupport.drainFixture(s.helper);
            s.check(code(owner, "target_occupied") && a.rocket.isAlive() && a.onlyObstruction(1),
                    "The obstructed restoration did not fail without mutation");
            s.check(journal.find(a.landed.transferId()).orElse(null) == a.landed
                    && "WRITE_BACK".equals(a.support.stage(a.logical())), "T was not written back identically");
            // G2b: until its own acknowledgement, P keeps the rocket locked and is not counted a second time.
            s.check(!a.support.resourceMutationAllowed(a.rocket) && !RocketRuntime.resourceMutationAllowed(a.rocket)
                    && a.support.slotsInUse(journal) == journal.entries().size()
                    && a.support.launchReservations(journal).stream().filter(a.region()::equals).count() == 1,
                    "The written-back T was unlocked or counted twice");
            RocketJournalGameTestSupport.acknowledgeFixture(s.helper);
            s.check(!a.support.pending(a.rocket) && a.support.resourceMutationAllowed(a.rocket)
                    && before.equals(a.diskRecord()), "The acknowledged write-back is not byte-identical and settled");
            s.check(a.support.launchReservations(journal).contains(a.region()), "A second flight could take T's region");
            a.clearObstruction(1);
            a.support.requestDisassembly(owner, a.rocket);
            RocketJournalGameTestSupport.acknowledgeFixture(s.helper);
            s.check(a.rocket.isRemoved() && a.allRestored(), "The request after clearing did not succeed");
        });
    }

    private static void completeRollbackWritesBack(Script s, Arrival a) {
        s.then(() -> {
            var journal = a.journal();
            var owner = a.requester(a.owner);
            // G3: a fault after two placements; the v0.5 rollback removes both (n of n).
            var faulty = RocketLandedDisassemblyGameTestSupport.create(s.helper, (point, progress) -> {
                if (point == RocketFailurePoint.DURING_RESTORATION && progress == 2) {
                    throw new RocketTransactionAbortException(RocketValidationCode.EXTRACTION_FAILED, "W85 G3 rollback");
                }
            });
            s.check(faulty.recover(a.landed.transferId()).status() == RocketTransferRecoveryReport.Status.RECOVERED,
                    "The faulty session did not settle T");
            faulty.requestDisassembly(owner, a.rocket);
            RocketJournalGameTestSupport.drainFixture(s.helper);
            s.check(code(owner, "extraction_failed") && a.rocket.isAlive() && !a.anyRestored(),
                    "The complete rollback left a block or lost the entity");
            s.check(journal.find(a.landed.transferId()).orElse(null) == a.landed
                    && "WRITE_BACK".equals(faulty.stage(a.logical())), "A certified clean rollback did not write T back");
            RocketJournalGameTestSupport.acknowledgeFixture(s.helper);
            s.check(!faulty.pending(a.rocket) && faulty.resourceMutationAllowed(a.rocket), "The write-back did not settle");
        });
    }

    private static void failedRollbackIsOwnedByTheTransactionRecovery(Script s, Arrival a) {
        s.then(() -> {
            var journal = a.journal();
            var owner = a.requester(a.owner);
            // G3b: ROLLBACK_FAILED. The v0.5 FAILED record is the only recovery owner; T is never written back.
            var faulty = RocketLandedDisassemblyGameTestSupport.create(s.helper, (point, progress) -> {
                if (point == RocketFailurePoint.DURING_RESTORATION && progress == 2) {
                    throw new RocketTransactionAbortException(RocketValidationCode.ROLLBACK_FAILED, "W85 G3b rollback");
                }
            });
            s.check(faulty.recover(a.landed.transferId()).status() == RocketTransferRecoveryReport.Status.RECOVERED,
                    "The faulty session did not settle T");
            faulty.requestDisassembly(owner, a.rocket);
            RocketJournalGameTestSupport.acknowledgeFixture(s.helper);
            s.check(code(owner, "rollback_failed") && a.rocket.isAlive() && !faulty.pending(a.rocket)
                    && journal.find(a.landed.transferId()).isEmpty(), "ROLLBACK_FAILED wrote T back or kept P");
            s.check(!RocketTransactionSavedData.get(s.helper.getLevel().getServer()).entries().isEmpty(),
                    "The v0.5 FAILED record is missing");
            var other = a.requester(a.owner);
            a.support.requestDisassembly(other, a.rocket);
            s.check(code(other, "region_busy") && a.rocket.isAlive(), "An overlapping transaction was not REGION_BUSY");
        });
        s.waitUntil(() -> a.ready() && RocketTransactionSavedData.get(s.helper.getLevel().getServer()).entries().isEmpty());
        s.then(() -> {
            var owner = a.requester(a.owner);
            a.support.requestDisassembly(owner, a.rocket);
            s.check(a.rocket.isRemoved() && a.allRestored(), "After v0.5 recovery the plain disassembly did not succeed");
        });
    }

    // ---- isolated checked journal: late, discarded and failing writes ----

    private static void writerHeldPastTheLifetime(Script s, Arrival a) {
        long[] requested = new long[1];
        var owner = new Requester[1];
        s.then(() -> {
            owner[0] = a.requester(a.owner);
            s.check(a.release(owner[0]) == RocketValidationCode.DISASSEMBLY_PENDING, "Detached request was not pending");
            requested[0] = a.earth.getGameTime();
        });
        // G6: the removal's acknowledgement arrives more than 200 ticks after the request.
        s.waitUntil(() -> a.ready() && a.earth.getGameTime() > requested[0] + 200);
        s.then(() -> {
            a.write(CheckedSavedDataPolicy.atomic(TYPE), true);
            s.check(code(owner[0], "region_busy") && !a.anyRestored() && a.rocket.isAlive()
                    && a.detached.find(a.landed.transferId()).orElse(null) == a.landed
                    && "WRITE_BACK".equals(a.support.stage(a.logical())), "A late acknowledgement restored or kept T out");
            a.write(CheckedSavedDataPolicy.atomic(TYPE), true);
            s.check(!a.support.pending(a.rocket) && a.diskTransfers().contains(a.landed.transferId()),
                    "The write-back did not end P with T on disk");
            s.check(a.release(owner[0]) == RocketValidationCode.DISASSEMBLY_PENDING, "The next request was refused");
            a.write(CheckedSavedDataPolicy.atomic(TYPE), true);
            s.check(a.rocket.isRemoved() && a.allRestored() && a.diskTransfers().isEmpty(), "The next request failed");
        });
    }

    private static void fullEffectQueueRefusesBeforeRelease(Script s, Arrival a) {
        s.then(() -> {
            // G7: two queued generations leave no continuation slot, so the request changes nothing.
            var journal = a.detached;
            s.check(journal.electFirstMoonLanding(moonEvent()), "Fixture election refused");
            RocketJournalGameTestSupport.queueDetached(s.helper, journal, UUID.randomUUID(), () -> true, () -> { });
            s.check(journal.deliverFirstMoonLanding(journal.firstMoonLandingRam().event()), "Fixture delivery refused");
            RocketJournalGameTestSupport.queueDetached(s.helper, journal, UUID.randomUUID(), () -> true, () -> { });
            s.check(!journal.canQueueEffect(), "The fixture did not fill the continuation queue");
            long sequence = journal.mutationSequence();
            s.check(a.release(a.requester(a.owner)) == RocketValidationCode.REGION_BUSY, "A full queue did not refuse");
            s.check(journal.find(a.landed.transferId()).orElse(null) == a.landed && journal.mutationSequence() == sequence
                    && !a.support.pending(a.rocket) && a.rocket.isAlive(), "A refused request changed something");
            a.write(CheckedSavedDataPolicy.atomic(TYPE), true);
        });
    }

    private static void unknownRemovalThenLaterGeneration(Script s, Arrival a) {
        s.then(() -> {
            var owner = a.requester(a.owner);
            a.release(owner);
            // G8: the removal ends UNKNOWN; a later generation is acknowledged first and discards its continuation.
            a.write(moveThenError(), true);
            s.check(a.detached.checkedWriteResult().outcome() == RocketTransferSavedData.WriteOutcome.UNKNOWN,
                    "The removal did not end UNKNOWN");
            s.check(a.detached.electFirstMoonLanding(moonEvent()), "Fixture successor refused");
            a.write(CheckedSavedDataPolicy.atomic(TYPE), true);
            s.check(!a.anyRestored() && a.rocket.isAlive() && code(owner, "region_busy")
                    && a.detached.find(a.landed.transferId()).orElse(null) == a.landed,
                    "The discarded removal restored or did not write T back");
            a.write(CheckedSavedDataPolicy.atomic(TYPE), true);
            s.check(!a.support.pending(a.rocket) && a.diskTransfers().contains(a.landed.transferId()),
                    "The written-back T is not acknowledged on disk");
        });
    }

    private static void committedRemovalWithADelayedSuccessor(Script s, Arrival a) {
        long[] requested = new long[1];
        s.then(() -> {
            a.release(a.requester(a.owner));
            requested[0] = a.earth.getGameTime();
            // G11: the removal commits, but a successor generation is in RAM before its continuation can run.
            a.write(CheckedSavedDataPolicy.atomic(TYPE), false);
            s.check(a.detached.electFirstMoonLanding(moonEvent()), "Fixture successor refused");
            RocketJournalGameTestSupport.pollDetached(s.helper, a.detached);
            s.check("RELEASED".equals(a.support.stage(a.logical())) && !a.anyRestored(),
                    "The continuation ran before the whole journal was acknowledged");
        });
        s.waitUntil(() -> a.ready() && a.earth.getGameTime() > requested[0] + 200);
        s.then(() -> {
            a.write(CheckedSavedDataPolicy.atomic(TYPE), true);
            s.check(!a.anyRestored() && a.rocket.isAlive() && "WRITE_BACK".equals(a.support.stage(a.logical())),
                    "A late committed removal restored the rocket");
            a.write(CheckedSavedDataPolicy.atomic(TYPE), true);
            s.check(!a.support.pending(a.rocket) && a.diskTransfers().contains(a.landed.transferId()) && !a.anyRestored(),
                    "The write-back did not finish without a block");
        });
    }

    private static void unknownMovedRemovalRetriedLate(Script s, Arrival a) {
        long[] requested = new long[1];
        s.then(() -> {
            a.release(a.requester(a.owner));
            requested[0] = a.earth.getGameTime();
            // G12: the removal's write moves the file and then reports UNKNOWN.
            a.write(moveThenError(), true);
            s.check(a.diskTransfers().isEmpty() && "RELEASED".equals(a.support.stage(a.logical())),
                    "The UNKNOWN removal did not reach disk or ran its continuation");
        });
        s.waitUntil(() -> a.ready() && a.earth.getGameTime() > requested[0] + 200);
        s.then(() -> {
            a.write(CheckedSavedDataPolicy.atomic(TYPE), true);
            s.check(!a.anyRestored() && "WRITE_BACK".equals(a.support.stage(a.logical())),
                    "The late retry restored the rocket");
            a.write(CheckedSavedDataPolicy.atomic(TYPE), true);
            s.check(!a.support.pending(a.rocket) && a.diskTransfers().contains(a.landed.transferId()) && !a.anyRestored(),
                    "The write-back did not finish without a block");
        });
    }

    private static void failingWriteBack(Script s, Arrival a, boolean unknown) {
        var owner = new Requester[1];
        CompoundTag[] before = new CompoundTag[1];
        s.then(() -> {
            owner[0] = a.requester(a.owner);
            before[0] = a.diskRecord();
            a.release(owner[0]);
            a.obstruct(1);
            a.write(CheckedSavedDataPolicy.atomic(TYPE), true);
            s.check(code(owner[0], "target_occupied") && "WRITE_BACK".equals(a.support.stage(a.logical())),
                    "The clean failure did not queue a write-back");
        });
        // G2b: the write-back's acknowledgement is held while P keeps the rocket locked.
        s.pause(20);
        s.then(() -> {
            s.check("WRITE_BACK".equals(a.support.stage(a.logical())) && !a.support.resourceMutationAllowed(a.rocket)
                    && a.support.slotsInUse(a.detached) == a.detached.entries().size(),
                    "A held write-back released the rocket or was counted twice");
            // G13 and G13b: the write-back keeps failing; T, P and every interlock stay, with no time limit.
            a.write(unknown ? garbageThenRefuse() : refuse(), true);
            a.support.reportUnacknowledged();
            a.support.reportUnacknowledged();
            s.check(a.detached.checkedWriteResult().outcome() == (unknown ? RocketTransferSavedData.WriteOutcome.UNKNOWN
                    : RocketTransferSavedData.WriteOutcome.NOT_COMMITTED), "The write-back did not fail as injected");
            s.check(a.detached.find(a.landed.transferId()).orElse(null) == a.landed && a.support.pending(a.rocket)
                    && !a.support.resourceMutationAllowed(a.rocket), "A failing write-back released T or P");
            a.support.requestDisassembly(owner[0], a.rocket);
            s.check(code(owner[0], "disassembly_pending"), "A request during the failing write-back was not a status");
            s.check(a.detached.electFirstMoonLanding(moonEvent()), "Disjoint generation refused");
            a.write(unknown ? garbageThenRefuse() : refuse(), true);
            a.support.reportUnacknowledged();
            s.check(a.support.pending(a.rocket) && a.rocket.isAlive(), "The second failure released P");
        });
        s.pause(20);
        s.then(() -> {
            s.check(a.support.pending(a.rocket), "P ended without an acknowledgement");
            a.write(CheckedSavedDataPolicy.atomic(TYPE), true);
            s.check(!a.support.pending(a.rocket) && a.detached.acknowledgedCurrent()
                    && before[0].equals(a.diskRecord()) && a.rocket.isAlive() && a.onlyObstruction(1),
                    "Repaired storage did not acknowledge the identical T and end P");
            a.clearObstruction(1);
        });
    }

    // ---- fixture ----

    private static boolean code(Requester player, String key) {
        return (CODE + key).equals(player.lastActionBar());
    }

    private static FirstEvent.MoonLanding moonEvent() {
        return FirstEvent.MoonLanding.create(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                UUID.randomUUID(), "0".repeat(64), 0, 0, 0, 0L, List.of());
    }

    private static CheckedSavedDataPolicy refuse() {
        return new CheckedSavedDataPolicy(TYPE, (staged, target) -> {
            throw new IOException("W85 injected write-back refusal before the move");
        });
    }

    private static CheckedSavedDataPolicy garbageThenRefuse() {
        return new CheckedSavedDataPolicy(TYPE, (staged, target) -> {
            Files.write(target, new byte[] {1, 2, 3});
            throw new IOException("W85 injected unknown write-back outcome");
        });
    }

    private static CheckedSavedDataPolicy moveThenError() {
        return new CheckedSavedDataPolicy(TYPE, (staged, target) -> {
            CheckedSavedDataFile.atomicMove(staged, target);
            throw new AssertionError("W85 injected unknown outcome after the move");
        });
    }

    /** One onEachTick registered at test start; steps advance through a mutable index (WORKER-RULES). */
    private static final class Script {
        final GameTestHelper helper;
        private final List<BooleanSupplier> steps = new ArrayList<>();
        private final GameTestTickPacer pacer;
        private int index;

        Script(GameTestHelper helper) {
            this.helper = helper;
            pacer = new GameTestTickPacer(helper.getLevel().getServer());
            then(() -> clearTransferJournal(helper.getLevel()));
        }

        Script then(Runnable action) {
            steps.add(() -> { action.run(); return true; });
            return this;
        }

        Script waitUntil(BooleanSupplier ready) {
            steps.add(() -> {
                if (ready.getAsBoolean()) { return true; }
                pacer.pace();
                return false;
            });
            return this;
        }

        Script pause(int ticks) {
            int[] left = {ticks};
            steps.add(() -> --left[0] <= 0);
            return this;
        }

        void check(boolean condition, String message) {
            helper.assertTrue(condition, message);
        }

        /** A landed arrival E with its COMMITTED record T, settled in a fresh session; then the scenario; then cleanup. */
        void landed(boolean fueled, boolean detached, java.util.function.BiConsumer<Script, Arrival> scenario) {
            Arrival arrival = new Arrival(helper);
            then(arrival::prepare);
            waitUntil(arrival::ready);
            then(() -> arrival.land(fueled, detached));
            scenario.accept(this, arrival);
            then(arrival::close);
        }

        void start() {
            helper.onEachTick(() -> {
                while (index < steps.size() && steps.get(index).getAsBoolean()) { index++; }
                if (index == steps.size()) { index++; helper.succeed(); }
            });
        }
    }

    /**
     * A landed arrival on the Moon, from the same scan and plan as the resource-recovery fixture. Scenario steps are
     * written before it exists; {@link #prepare} builds it when the script reaches it.
     */
    private static final class Arrival {
        final GameTestHelper helper;
        final ServerLevel earth;
        final ServerLevel moon;
        final UUID owner = UUID.randomUUID();
        RocketTransferRecord record;
        RocketEntity rocket;
        RocketTransferRecord landed;
        RocketLandedDisassemblyGameTestSupport support;
        RocketTransferSavedData detached;
        Path directory;

        Arrival(GameTestHelper helper) {
            this.helper = helper;
            earth = helper.getLevel();
            moon = earth.getServer().getLevel(CelestialIds.MOON_LEVEL);
            helper.assertTrue(moon != null, "Moon Level unavailable");
        }

        void prepare() {
            placeLegalRocket(helper, ORIGIN);
            helper.setBlock(ORIGIN.west(), ModBlocks.ROCKET_FUEL_TANK.get());
            helper.setBlock(ORIGIN.east(), ModBlocks.ROCKET_FUEL_TANK.get());
            helper.setBlock(ORIGIN.north(), ModBlocks.ROCKET_FUEL_TANK.get());
            var snapshot = successfulSnapshot(helper, ORIGIN);
            clearSnapshotBlocks(earth, snapshot);
            var sourceOrigin = snapshot.sourceOrigin();
            var destinationOrigin = new RocketPosition(sourceOrigin.x(),
                    Math.max(moon.getMinBuildHeight() + 16, sourceOrigin.y()), sourceOrigin.z());
            var destination = snapshot.relocated(UUID.randomUUID(), moon.dimension().location(), destinationOrigin,
                    earth.getGameTime());
            clearSnapshotBlocks(moon, destination);
            var fuel = RocketFuelState.empty(snapshot.propulsion().orElseThrow()).withFluidBank(false,
                    ModIdentity.id("rocket_fuel"), null, 3_000);
            UUID logical = UUID.randomUUID();
            UUID transfer = UUID.randomUUID();
            long now = earth.getGameTime();
            var base = new RocketFlightPlan(3, transfer, CelestialIds.EARTH_ID, CelestialIds.MOON_ID,
                    earth.dimension().location(), moon.dimension().location(), 417, now);
            var catalog = new RocketFluidFuelCatalog(Map.of(
                    new RocketFluidFuelCatalog.Key(RocketPropulsion.Role.MONO, ModIdentity.id("rocket_fuel")),
                    new RocketFluidFuelCatalog.Efficiency(417, 1_000)));
            var plan = RocketPropulsionQuoter.quote(fuel, base, catalog).plan();
            var flight = RocketFlightData.initial(logical, 3_000, snapshot.stats().seatCount(), CelestialIds.EARTH_ID,
                    earth.dimension().location(), snapshot.sourceOrigin(), now).withFuel(fuel, now);
            var transit = flight.withPlan(plan).startCountdown(now).completeCountdown(now).beginTransit(transfer, now);
            var arrival = transit.arriveAtDestination(fuel.debit(plan).state(), CelestialIds.MOON_ID,
                    moon.dimension().location(), destination.sourceOrigin(), now);
            record = RocketTransferRecord.create(transfer, logical, owner, UUID.randomUUID(), snapshot, destination,
                    transit, arrival, plan.requiredFuel(), now);
        }

        boolean ready() {
            keepLoaded(earth, record.sourceSnapshot());
            keepLoaded(moon, record.destinationSnapshot());
            var origin = record.destinationSnapshot().sourceOrigin();
            long chunk = ChunkPos.asLong(origin.x() >> 4, origin.z() >> 4);
            return moon.areEntitiesLoaded(chunk) && earth.areEntitiesLoaded(chunk);
        }

        private static void keepLoaded(ServerLevel level,
                                       io.github.sunthemoon.advancedrocketrycommunity.rocket.model.RocketStructureSnapshot snapshot) {
            var chunks = new java.util.HashSet<ChunkPos>();
            for (var block : snapshot.blocks()) {
                var absolute = snapshot.sourceOrigin().add(block.position());
                var at = new BlockPos(absolute.x(), absolute.y(), absolute.z());
                var chunk = new ChunkPos(at);
                if (chunks.add(chunk)) {
                    level.getChunkSource().addRegionTicket(TICKET, chunk, 2, chunk);
                    level.getChunkAt(at);
                }
            }
        }

        void land(boolean fueled, boolean isolated) {
            rocket = ModEntities.ROCKET.get().create(moon);
            rocket.initializeTransferred(record.destinationSnapshot(), record.logicalRocketId(), owner,
                    record.destinationFlightData().land(moon.getGameTime()));
            helper.assertTrue(moon.addFreshEntity(rocket), "Arrival entity admission failed");
            if (!fueled) {
                var flight = rocket.flightData().orElseThrow();
                rocket.updateFlightData(flight.withFuel(RocketFuelState.empty(
                        record.destinationSnapshot().propulsion().orElseThrow()), moon.getGameTime()));
            }
            landed = record.destinationSpawned(rocket.getUUID()).advance(RocketTransferPhase.PASSENGERS_TRANSFERRED)
                    .advance(RocketTransferPhase.SOURCE_REMOVED).advance(RocketTransferPhase.COMMITTED);
            support = RocketLandedDisassemblyGameTestSupport.create(helper);
            if (isolated) {
                try { directory = Files.createTempDirectory("arce-w92-landed-"); }
                catch (IOException failure) { throw new UncheckedIOException(failure); }
                detached = new RocketTransferSavedData();
                detached.put(landed);
                RocketJournalGameTestSupport.confirmDetached(helper, detached, file());
                return;
            }
            journal().put(landed);
            RocketJournalGameTestSupport.acknowledgeFixture(helper);
            var installed = RocketJournalGameTestSupport.recoverFixture(helper,
                    () -> RocketRuntime.recoverTransfer(earth.getServer(), landed.transferId()));
            helper.assertTrue(installed.status() == RocketTransferRecoveryReport.Status.RECOVERED,
                    "Installed recovery did not settle the arrival: " + installed);
            helper.assertTrue(support.recover(landed.transferId()).status() == RocketTransferRecoveryReport.Status.RECOVERED,
                    "The session did not settle the arrival");
            helper.assertTrue(support.resourceMutationAllowed(rocket), "The settled arrival is not landed authority");
        }

        RocketTransferSavedData journal() { return RocketTransferSavedData.get(earth.getServer()); }
        UUID logical() { return record.logicalRocketId(); }
        RocketRegion region() { return RocketRegion.fromSnapshot(record.destinationSnapshot()); }
        Path file() { return directory.resolve(TYPE.fileName()); }

        Requester requester(UUID id) {
            var player = new Requester(moon, id);
            player.setPos(rocket.position());
            return player;
        }

        RocketValidationCode release(Requester requester) { return support.releaseDetached(rocket, requester, detached); }

        void write(CheckedSavedDataPolicy policy, boolean poll) {
            RocketJournalGameTestSupport.writeDetached(helper, detached, file(), policy, poll);
        }

        BlockPos footprint(int index) {
            var snapshot = record.destinationSnapshot();
            var at = snapshot.sourceOrigin().add(snapshot.blocks().get(index).position());
            return new BlockPos(at.x(), at.y(), at.z());
        }

        void obstruct(int index) { moon.setBlockAndUpdate(footprint(index), Blocks.STONE.defaultBlockState()); }
        void clearObstruction(int index) { moon.setBlockAndUpdate(footprint(index), Blocks.AIR.defaultBlockState()); }

        boolean onlyObstruction(int index) {
            for (int i = 0; i < record.destinationSnapshot().blocks().size(); i++) {
                var state = moon.getBlockState(footprint(i));
                if (i == index ? !state.is(Blocks.STONE) : !state.isAir()) { return false; }
            }
            return true;
        }

        boolean anyRestored() {
            for (int i = 0; i < record.destinationSnapshot().blocks().size(); i++) {
                if (!moon.getBlockState(footprint(i)).isAir() && !moon.getBlockState(footprint(i)).is(Blocks.STONE)) {
                    return true;
                }
            }
            return false;
        }

        boolean allRestored() {
            var snapshot = record.destinationSnapshot();
            for (int i = 0; i < snapshot.blocks().size(); i++) {
                var id = net.minecraftforge.registries.ForgeRegistries.BLOCKS.getKey(moon.getBlockState(footprint(i)).getBlock());
                if (!snapshot.blocks().get(i).state().blockId().equals(id)) { return false; }
            }
            return true;
        }

        /** T's encoded record in the world journal's checked file, or in the isolated file. */
        CompoundTag diskRecord() {
            Path path = detached != null ? file() : earth.getServer().getWorldPath(LevelResource.ROOT)
                    .resolve("data").resolve(TYPE.fileName());
            var payload = CheckedSavedDataFile.readPayload(path, TYPE).orElseThrow();
            for (var raw : payload.getList("transfers", 10)) {
                var tag = (CompoundTag) raw;
                if (tag.getUUID("transfer_id").equals(landed.transferId())) { return tag.copy(); }
            }
            return new CompoundTag();
        }

        List<UUID> diskTransfers() {
            var payload = CheckedSavedDataFile.readPayload(file(), TYPE).orElseThrow();
            return RocketTransferSavedData.load(payload).entries().stream().map(RocketTransferRecord::transferId).toList();
        }

        void close() {
            if (rocket != null && !rocket.isRemoved()) { rocket.discard(); }
            clearSnapshotBlocks(moon, record.destinationSnapshot());
            clearSnapshotBlocks(earth, record.sourceSnapshot());
            if (detached != null) {
                detached.stop();
                try {
                    Files.deleteIfExists(file());
                    try (var stale = Files.list(directory)) {
                        for (var path : stale.toList()) { Files.deleteIfExists(path); }
                    }
                    Files.delete(directory);
                } catch (IOException failure) {
                    throw new UncheckedIOException(failure);
                }
            } else {
                clearTransferJournal(earth);
                RocketJournalGameTestSupport.acknowledgeFixture(helper);
            }
        }
    }
}
