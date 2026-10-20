package io.github.sunthemoon.advancedrocketrycommunity.rocket.server;

import io.github.sunthemoon.advancedrocketrycommunity.AdvancedRocketryCommunity;
import io.github.sunthemoon.advancedrocketrycommunity.ModIdentity;
import io.github.sunthemoon.advancedrocketrycommunity.resourceescrow.EscrowBarrier;
import io.github.sunthemoon.advancedrocketrycommunity.resourceescrow.EscrowOperation;
import io.github.sunthemoon.advancedrocketrycommunity.resourceescrow.EscrowRuntime;
import io.github.sunthemoon.advancedrocketrycommunity.resourceescrow.EscrowTestEndpoints;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.entity.RocketEntity;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketFlightState;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketTransferRecoveryAction;
import java.io.IOException;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.TicketType;
import net.minecraft.util.Unit;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.entity.ChunkEntities;
import net.minecraft.world.level.entity.EntityPersistentStorage;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/**
 * W116 (W98-R12): coverage of the ordinary source-retirement coordinator that W98 left to unit tests or did not test:
 * the R5(c) fallback, contention with a governed proof, a superseding vanilla store and a survivor removed during its
 * proof. Each case reuses W98's detached fixture.
 */
@GameTestHolder(ModIdentity.MOD_ID)
@PrefixGameTestTemplate(false)
public final class RocketRetirementCoverageGameTests {
    private static final String COVERAGE = "ordinary_source_retirement_coverage";
    /** Each of these swaps or counts something shared by every test of its batch, so it runs alone. */
    private static final String STORAGE = "ordinary_source_retirement_storage";
    private static final String CONTENTION = "ordinary_source_retirement_contention";
    private static final String SHARED_SLOT = "ordinary_source_retirement_shared_slot";
    private RocketRetirementCoverageGameTests() { }

    /**
     * R5(c): while another mod's decorator replaces the Level's entity storage, a countdown cancellation keeps today's
     * ordering: T leaves RAM after the in-memory return, with no own-store proof, and the request completes once on the
     * removal's acknowledgement. The decorator is in place only for the cancellation's call.
     */
    @GameTest(template = "rocket_test", batch = STORAGE, timeoutTicks = 300)
    public static void anotherModsEntityStorageKeepsTodaysOrderingWithOneWarning(GameTestHelper helper) {
        new RocketSourceRetirementGameTests.Case(helper) {
            void act() {
                source = flight.spawnCountdown(helper); prepare(true);
                var manager = level().entityManager; var vanilla = manager.permanentStorage;
                manager.permanentStorage = new ForeignStorage(vanilla);
                try {
                    helper.assertTrue(service.failBackToSource(server(), journal, record, source, RocketTransferReturnReason.COUNTDOWN_CANCELLED,
                            completion()), "Countdown cancellation was refused");
                } finally { manager.permanentStorage = vanilla; }
                var flightData = source.flightData().orElseThrow();
                helper.assertTrue(flightData.state() == RocketFlightState.FUELED || flightData.state() == RocketFlightState.ASSEMBLED,
                        "The source did not return to its pad");
                // The entry itself stays until the removal's acknowledgement runs the request's effect.
                helper.assertTrue(journal.find(record.transferId()).isEmpty(), "Another mod's entity storage still waited for an own-store proof (R5(c))");
                helper.assertTrue(accepted.get() == 0, "The request completed before the removal's acknowledgement");
            }
            boolean before() {
                if (!journal.clean() || accepted.get() == 0) { return false; }
                helper.assertTrue(accepted.get() == 1 && discarded.get() == 0, "The request did not complete exactly once");
                helper.assertTrue(!recovery().retirements().pending(record.logicalRocketId()), "The acknowledged removal left its entry");
                helper.assertTrue(!RocketSourceRetirementFixtures.live(service).contains(record.transferId())
                        && !RocketSourceRetirementFixtures.settled(service).contains(record.transferId()), "The removed record kept its membership");
                close(); helper.succeed();
                return false;
            }
        }.start();
    }

    /** A governed proof of another entity slot, staged in the same Level batch as an ordinary return, is not disturbed. */
    @GameTest(template = "rocket_test", batch = CONTENTION, timeoutTicks = 400)
    public static void aGovernedProofOfAnotherSlotInTheSameBatchIsNotDisturbed(GameTestHelper helper) {
        new Contention(helper, false).start();
    }

    /**
     * W98-R1, observed and not fixed (a named R-035 residual until governed admission opens): a governed proof of the
     * ordinary survivor's own slot, in the same batch. The test asserts only the ordinary return's safety and logs the
     * governed outcome for the record.
     */
    @GameTest(template = "rocket_test", batch = SHARED_SLOT, timeoutTicks = 400)
    public static void aGovernedProofOfTheSurvivorsOwnSlotIsObserved(GameTestHelper helper) {
        new Contention(helper, true).start();
    }

    /** A vanilla store of the survivor's slot after the attempt's own capture supersedes the proof; T stays, a retry proves it. */
    @GameTest(template = "rocket_test", batch = COVERAGE, timeoutTicks = 400)
    public static void aVanillaStoreOfTheSurvivorsSlotSupersedesTheProof(GameTestHelper helper) {
        new RocketSourceRetirementGameTests.Case(helper) {
            boolean cancelled, superseded;
            void act() { source = flight.spawnCountdown(helper); prepare(true); }
            boolean before() {
                // Case calls this on every tick: once the superseded attempt is seen, the retirement wait owns the record.
                if (superseded) { return true; }
                var retirements = recovery().retirements(); UUID logical = record.logicalRocketId();
                if (!cancelled) {
                    if (!cancelOnAFreeCaptureTick(this)) { return false; }
                    helper.assertTrue(retirements.attempts(logical) == 1, "The first attempt did not capture the survivor's slot at once");
                    // The store an autosave or an unload makes, after the attempt's own capture of the same slot.
                    helper.assertTrue(level().entityManager.storeChunkSections(flight.originSlot().toLong(), entity -> { }), "The vanilla store was refused");
                    cancelled = true;
                    return false;
                }
                helper.assertTrue(journal.find(record.transferId()).orElse(null) == record, "The proof survived the vanilla store and removed the record");
                superseded = "SUPERSEDING_SAVE".equals(retirements.reason(logical));
                return superseded;
            }
        }.start();
    }

    /**
     * A survivor removed while its proof is pending (for example by /kill): the coordinator abandons the return with
     * SURVIVOR_GONE, discards the request and unclassifies T, and recovery rebuilds the source from T and retires it.
     */
    @GameTest(template = "rocket_test", batch = COVERAGE, timeoutTicks = 400)
    public static void aSurvivorRemovedDuringItsProofIsRebuiltFromTheRecord(GameTestHelper helper) {
        new RocketSourceRetirementGameTests.Case(helper) {
            RocketEntity original; int stage;
            void act() { source = flight.spawnCountdown(helper); prepare(true); }
            boolean before() {
                var server = server(); var retirements = recovery().retirements(); UUID logical = record.logicalRocketId();
                if (stage == 0) {
                    if (!cancelOnAFreeCaptureTick(this)) { return false; }
                    original = source; original.discard();
                    stage = 1;
                    return false;
                }
                // Case calls this on every tick: once the rebuilt return is pending, the retirement wait owns the record.
                if (stage == 3) { return true; }
                helper.assertTrue(journal.find(record.transferId()).orElse(null) == record, "T left RAM without a proved survivor");
                if (stage == 1) {
                    if (retirements.pending(logical)) { return false; }
                    helper.assertTrue(discarded.get() == 1 && accepted.get() == 0, "The abandoned return's request was not discarded");
                    helper.assertTrue(!RocketSourceRetirementFixtures.live(service).contains(record.transferId())
                            && !RocketSourceRetirementFixtures.settled(service).contains(record.transferId()), "SURVIVOR_GONE left T classified");
                    completion = false; stage = 2;
                    return false;
                }
                if (stage == 2) {
                    if (!EscrowRuntime.of(server).barrier().availableOrdinary(level().dimension().location().toString(), server.getTickCount())) { return false; }
                    var result = recovery().recoverById(server, journal, record.transferId());
                    if (!retirements.pending(logical)) {
                        helper.assertTrue(result.status() == RocketTransferRecoveryService.Status.RETRY_LATER, "Recovery status " + result.status());
                        return false;
                    }
                    source = RocketTransferEntities.loadedMatches(server, record, false).stream().findFirst().orElse(null);
                    helper.assertTrue(source != null && !source.getUUID().equals(original.getUUID()), "Recovery did not rebuild the source");
                    stage = 3;
                    return false;
                }
                return true;
            }
            void retired() {
                helper.assertTrue(discarded.get() == 1 && accepted.get() == 0, "The abandoned request completed");
                var report = recovery().recoverById(server(), journal, record.transferId());
                helper.assertTrue(report.status() == RocketTransferRecoveryService.Status.RECOVERED
                        && report.action() == RocketTransferRecoveryAction.REBUILD_SOURCE, "The rebuild was not reported: " + report.status());
            }
        }.start();
    }

    /** An uncrewed countdown cancellation on a tick whose capture is free, so its first attempt captures at once. */
    private static boolean cancelOnAFreeCaptureTick(RocketSourceRetirementGameTests.Case test) {
        var server = test.server();
        if (!EscrowRuntime.of(server).barrier().availableOrdinary(test.level().dimension().location().toString(), server.getTickCount())) {
            return false;
        }
        test.helper.assertTrue(test.service.failBackToSource(server, test.journal, test.record, test.source,
                RocketTransferReturnReason.COUNTDOWN_CANCELLED, test.completion()), "Countdown cancellation was refused");
        test.assertReturnedAndHeld(true);
        return true;
    }

    /**
     * A governed proof of a test endpoint's bank entity, begun one tick before an ordinary countdown cancellation, so
     * that both captures share the Level's 20-tick batch. With {@code shared} the bank stands in the survivor's slot.
     */
    private static final class Contention extends RocketSourceRetirementGameTests.Case {
        private static final BlockPos FAR = new BlockPos(610_001, 100, 610_001);
        final boolean shared;
        EscrowTestEndpoints.Fixture endpoints;
        EscrowOperation operation;
        BlockPos chest;
        ChunkPos ticketed;
        EscrowBarrier.Result governed;
        long governedTick = -1;
        int[] counters;
        boolean returned, observed;

        Contention(GameTestHelper helper, boolean shared) { super(helper); this.shared = shared; }

        boolean ready() {
            if (!super.ready()) { return false; }
            if (chest == null) {
                // The bank stands two blocks east of its chest; both stay inside one chunk column.
                chest = shared ? new BlockPos((origin.getX() & ~15) + 1, origin.getY() + 6, origin.getZ()) : FAR;
                if (!shared) {
                    ticketed = new ChunkPos(FAR);
                    level().getChunkSource().addRegionTicket(TicketType.START, ticketed, 2, Unit.INSTANCE);
                    level().getChunk(ticketed.x, ticketed.z);
                }
            }
            var bank = chest.offset(2, 0, 0);
            return level().areEntitiesLoaded(new ChunkPos(bank).toLong()) && level().isPositionEntityTicking(bank);
        }

        void act() {
            source = flight.spawnCountdown(helper); prepare(true);
            endpoints = EscrowTestEndpoints.create(level(), chest, UUID.randomUUID());
            operation = endpoints.operation(1); endpoints.apply(operation);
            helper.assertTrue(new ChunkPos(endpoints.bank().blockPosition()).equals(flight.originSlot()) == shared,
                    "The bank is not where the case needs it");
            var barrier = EscrowRuntime.of(server()).barrier();
            counters = new int[] {barrier.ioFailures(), barrier.supersedingSaves(), barrier.refusals()};
        }

        boolean before() {
            // Case calls this on every tick: the governed outcome is observed once, then the retirement wait goes on.
            if (observed) { return true; }
            var server = server(); long now = server.getTickCount();
            var runtime = EscrowRuntime.of(server); var barrier = runtime.barrier();
            if (governedTick < 0) {
                var frontier = endpoints.bankState();
                if (barrier.begin(runtime.store(level()), operation.destination(), frontier,
                        EscrowTestEndpoints.validator(operation.destination(), frontier), now, result -> governed = result)) {
                    governedTick = now;
                }
                return false;
            }
            if (!returned) {
                helper.assertTrue(now - governedTick < 10, "The ordinary return could not join the governed proof's batch");
                if (!barrier.availableOrdinary(level().dimension().location().toString(), now)) { return false; }
                helper.assertTrue(service.failBackToSource(server, journal, record, source, RocketTransferReturnReason.COUNTDOWN_CANCELLED,
                        completion()), "Countdown cancellation was refused");
                assertReturnedAndHeld(true);
                helper.assertTrue(recovery().retirements().attempts(record.logicalRocketId()) == 1,
                        "The ordinary attempt did not capture in the governed proof's batch");
                returned = true;
                return false;
            }
            if (governed == null) { return false; }
            if (shared) {
                AdvancedRocketryCommunity.LOGGER.info("W116_SHARED_SLOT_OBSERVATION governed_confirmed={} governed_reason={} superseding_saves_delta={} io_failures_delta={}",
                        governed.confirmed(), governed.reason(), barrier.supersedingSaves() - counters[1], barrier.ioFailures() - counters[0]);
            } else {
                helper.assertTrue(governed.confirmed(), "The governed proof of another slot did not confirm: " + governed.reason());
                assertCountersUnchanged(barrier);
            }
            observed = true;
            return true;
        }

        void retired() {
            if (shared) {
                AdvancedRocketryCommunity.LOGGER.info("W116_SHARED_SLOT_OBSERVATION_AFTER_RETIREMENT governed_proof_valid={}",
                        governed.proof() != null && governed.proof().valid());
                return;
            }
            helper.assertTrue(governed.proof().valid(), "The ordinary capture of another slot invalidated the governed proof");
            assertCountersUnchanged(EscrowRuntime.of(server()).barrier());
        }

        void assertCountersUnchanged(EscrowBarrier barrier) {
            helper.assertTrue(barrier.ioFailures() == counters[0] && barrier.supersedingSaves() == counters[1] && barrier.refusals() == counters[2],
                    "The ordinary attempt changed the governed barrier's counts");
        }

        void restore() {
            if (governed != null && governed.proof() != null) { governed.proof().release(); }
            if (endpoints != null) { endpoints.bank().discard(); level().setBlockAndUpdate(chest, Blocks.AIR.defaultBlockState()); }
            if (ticketed != null) { level().getChunkSource().removeRegionTicket(TicketType.START, ticketed, 2, Unit.INSTANCE); }
        }
    }

    /** Another mod's entity storage decorator: it stores through the Level's own storage but is not the vanilla class. */
    private record ForeignStorage(EntityPersistentStorage<Entity> delegate) implements EntityPersistentStorage<Entity> {
        @Override public CompletableFuture<ChunkEntities<Entity>> loadEntities(ChunkPos pos) { return delegate.loadEntities(pos); }
        @Override public void storeEntities(ChunkEntities<Entity> entities) { delegate.storeEntities(entities); }
        @Override public void flush(boolean sync) { delegate.flush(sync); }
        @Override public void close() throws IOException { delegate.close(); }
    }
}
