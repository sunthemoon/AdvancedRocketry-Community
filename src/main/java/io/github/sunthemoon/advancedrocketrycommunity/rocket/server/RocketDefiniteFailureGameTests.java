package io.github.sunthemoon.advancedrocketrycommunity.rocket.server;

import io.github.sunthemoon.advancedrocketrycommunity.ModIdentity;
import io.github.sunthemoon.advancedrocketrycommunity.registry.ModEntities;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.entity.RocketEntity;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketFlightState;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketTransferPhase;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketTransferRecord;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.persistence.RocketJournalGameTestSupport;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.persistence.RocketJournalSchedulingGameTestSupport;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.persistence.RocketTransferSavedData;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/** G3's ordinary landing and launch use the real service while the previous disk acknowledgement survives. */
@GameTestHolder(ModIdentity.MOD_ID)
@PrefixGameTestTemplate(false)
public final class RocketDefiniteFailureGameTests {
    private RocketDefiniteFailureGameTests() { }
    @GameTest(template = "rocket_test", batch = "journal_concurrent_flight", timeoutTicks = 650)
    public static void definiteFailureAllowsAcknowledgedDescentButQueuesTheNextLaunchStep(GameTestHelper helper) {
        new Scenario(helper).start();
    }
    private static final class Scenario {
        final GameTestHelper helper;
        final RocketConcurrentFlightGameTests.Scenario fixture;
        RocketJournalSchedulingGameTestSupport.FailedWrite fault;
        RocketEntity arrival;
        RocketTransferRecord landed;
        int stage, waits;
        boolean closed;
        Scenario(GameTestHelper helper) { this.helper = helper; fixture = new RocketConcurrentFlightGameTests.Scenario(helper, false); }
        void start() { helper.onEachTick(this::tick); }
        void tick() {
            if (closed) { return; }
            try {
                helper.assertTrue(++waits < 600, "Definite-failure fixture exceeded its bounded wait");
                var server = helper.getLevel().getServer(); var journal = fixture.journal;
                if (stage == 0) {
                    fixture.setup(); if (fixture.stage != 2) { return; }
                    fixture.sources[0].discard();
                    arrival = ModEntities.ROCKET.get().create(helper.getLevel()); helper.assertTrue(arrival != null, "No arrival");
                    var record = fixture.initial[0];
                    landed = record.destinationSpawned(arrival.getUUID()).advance(RocketTransferPhase.PASSENGERS_TRANSFERRED)
                            .advance(RocketTransferPhase.SOURCE_REMOVED).advance(RocketTransferPhase.COMMITTED);
                    arrival.initializeTransferred(record.destinationSnapshot(), record.logicalRocketId(), record.ownerId(), record.destinationFlightData());
                    RocketTransferEntities.positionAtAltitude(arrival, record.destinationSnapshot().sourceOrigin());
                    helper.assertTrue(helper.getLevel().addFreshEntity(arrival), "Arrival was not added");
                    journal.put(landed); RocketJournalGameTestSupport.confirmDetached(helper, journal, fixture.file);
                    journal.put(RocketFailBackAdmissionGameTests.record(helper, java.util.UUID.randomUUID(), java.util.UUID.randomUUID(),
                            java.util.UUID.randomUUID(), null, helper.absolutePos(new net.minecraft.core.BlockPos(3, 2, 15)), 256));
                    fault = RocketJournalSchedulingGameTestSupport.fail(helper, journal, fixture.file, false); stage = 1;
                } else if (stage == 1 && fault.entered()) {
                    fault.releaseFailure(); stage = 2;
                } else if (stage == 2) {
                    journal.pollWriter(server.getTickCount());
                    if (journal.checkedWriteResult().outcome() != RocketTransferSavedData.WriteOutcome.NOT_COMMITTED) { return; }
                    helper.assertTrue(journal.actionable(landed.logicalRocketId()), "Definite failure revoked acknowledged descent");
                    fixture.service.tickLive(server, journal, landed);
                    var launching = journal.find(fixture.initial[1].transferId()).orElseThrow();
                    fixture.service.tickLive(server, journal, launching);
                    launching = journal.find(launching.transferId()).orElseThrow();
                    helper.assertTrue(RocketTransferEntities.loadedMatches(server, launching, true).isEmpty(),
                            "Launch published before its failed writer retried");
                    if (launching.phase() == RocketTransferPhase.DESTINATION_SPAWNED) {
                        helper.assertTrue(arrival.flightData().orElseThrow().state() == RocketFlightState.LANDED
                                && journal.outstandingEffectCount() == 1 && !journal.actionable(launching.logicalRocketId()),
                                "Acknowledged landing or queued launch did not follow the definite-failure rule");
                        fault.recover(); stage = 3;
                    }
                } else if (stage == 3) {
                    journal.pollWriter(server.getTickCount()); journal.retryChecked(server);
                    var launching = journal.find(fixture.initial[1].transferId()).orElseThrow();
                    fixture.service.tickLive(server, journal, launching);
                    if (journal.clean() && launching.phase() == RocketTransferPhase.COMMITTED
                            && RocketTransferEntities.loadedMatches(server, launching, true).get(0).flightData().orElseThrow().state() == RocketFlightState.LANDED) {
                        helper.assertTrue(RocketTransferEntities.loadedMatches(server, launching, true).size() == 1
                                && RocketTransferEntities.loadedMatches(server, launching, false).isEmpty(), "Retried launch duplicated an entity");
                        RocketJournalGameTestSupport.confirmDetached(helper, journal, fixture.file);
                        close(); helper.succeed();
                    }
                }
                if (!closed) { fixture.pacer.pace(); }
            } catch (java.io.IOException failure) { close(); throw new java.io.UncheckedIOException(failure); }
            catch (RuntimeException | Error failure) { close(); throw failure; }
        }
        void close() { if (closed) { return; } closed = true; if (fault != null) { fault.close(); } if (arrival != null) { arrival.discard(); } fixture.close(); }
    }
}
