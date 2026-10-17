package io.github.sunthemoon.advancedrocketrycommunity.rocket.server;

import io.github.sunthemoon.advancedrocketrycommunity.ModIdentity;
import io.github.sunthemoon.advancedrocketrycommunity.persistence.migration.CheckedSavedDataFile;
import io.github.sunthemoon.advancedrocketrycommunity.persistence.migration.ManagedSavedDataType;
import io.github.sunthemoon.advancedrocketrycommunity.registry.ModEntities;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketFlightLimits;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketFlightState;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketTransferPhase;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.persistence.RocketJournalGameTestSupport;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.persistence.RocketJournalSchedulingGameTestSupport;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.persistence.RocketTransferSavedData;
import java.util.HashSet;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.Entity;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/** G2: late checked completion must revalidate the captured entity, not just its UUID. */
@GameTestHolder(ModIdentity.MOD_ID)
@PrefixGameTestTemplate(false)
public final class RocketUnknownSchedulingGameTests {
    private RocketUnknownSchedulingGameTests() { }

    @GameTest(template = "rocket_test", batch = "journal_concurrent_flight", timeoutTicks = 1100)
    public static void unknownBeyondTicketExpiryRechecksLoadedAndUnloadedCaptures(GameTestHelper helper) {
        new Scenario(helper).start();
    }

    private static final class Scenario {
        final GameTestHelper helper;
        final RocketConcurrentFlightGameTests.Scenario fixture;
        RocketJournalSchedulingGameTestSupport.FailedWrite fault;
        RocketTransferRecoveryService recovery;
        CompoundTag unloaded;
        int stage, waits, unknownSince;
        boolean closed;
        Scenario(GameTestHelper helper) { this.helper = helper; fixture = new RocketConcurrentFlightGameTests.Scenario(helper, false); }
        void start() { helper.onEachTick(this::tick); }
        void tick() {
            if (closed) { return; }
            try {
                helper.assertTrue(++waits < 1050, "UNKNOWN fixture exceeded its bounded wait");
                var server = helper.getLevel().getServer(); var journal = fixture.journal;
                if (stage == 0) {
                    fixture.setup(); if (fixture.stage != 2) { return; }
                    for (int i = 0; i < 2; i++) {
                        fixture.sources[i].updateFlightData(fixture.initial[i].sourceFlightData());
                        RocketTransferEntities.positionAtAltitude(fixture.sources[i], fixture.initial[i].sourceSnapshot().sourceOrigin());
                        RocketTransferEntities.recoveryEntityChunksLoaded(server, fixture.initial[i]);
                    }
                    journal.setDirty(); fault = RocketJournalSchedulingGameTestSupport.fail(helper, journal, fixture.file, true);
                    stage = 1;
                } else if (stage == 1 && fault.entered()) {
                    for (int i = 0; i < 2; i++) { fixture.service.spawnDestination(server, journal, fixture.initial[i], fixture.sources[i]); }
                    helper.assertTrue(journal.outstandingEffectCount() == 2, "Both real spawn continuations must be queued");
                    fault.releaseFailure(); stage = 2;
                } else if (stage == 2) {
                    journal.pollWriter(server.getTickCount());
                    if (journal.checkedWriteResult().outcome() == RocketTransferSavedData.WriteOutcome.UNKNOWN) {
                        unknownSince = server.getTickCount();
                        unloaded = fixture.sources[1].saveWithoutId(new CompoundTag());
                        fixture.sources[1].remove(Entity.RemovalReason.UNLOADED_TO_CHUNK);
                        stage = 3;
                    }
                } else if (stage == 3) {
                    journal.pollWriter(server.getTickCount());
                    for (var record : journal.entries()) {
                        helper.assertTrue(!journal.actionable(record.logicalRocketId()), "UNKNOWN granted flight authority");
                        fixture.service.tickLive(server, journal, record);
                        helper.assertTrue(RocketTransferEntities.loadedMatches(server, record, true).isEmpty(), "UNKNOWN published a destination");
                    }
                    helper.assertTrue(journal.outstandingEffectCount() == 2 && fixture.sources[0].isAlive()
                            && fixture.sources[0].flightData().orElseThrow().state() == RocketFlightState.TRANSIT,
                            "UNKNOWN discarded a continuation or advanced the loaded flight");
                    if (server.getTickCount() - unknownSince > RocketFlightLimits.FLIGHT_TICKET_TIMEOUT_TICKS + 10) {
                        for (var record : fixture.initial) {
                            helper.assertTrue(RocketTransferFenceGameTests.flightTickets(server, record.transferId()) == 0,
                                    "UNKNOWN renewed an expired flight ticket");
                        }
                        fault.recover(); stage = 4;
                    }
                } else if (stage == 4) {
                    journal.pollWriter(server.getTickCount()); journal.retryChecked(server);
                    if (!journal.clean()) { return; }
                    var a = journal.find(fixture.initial[0].transferId()).orElseThrow();
                    var b = journal.find(fixture.initial[1].transferId()).orElseThrow();
                    helper.assertTrue(RocketTransferEntities.loadedMatches(server, a, true).size() == 1
                            && RocketTransferEntities.loadedMatches(server, b, true).isEmpty()
                            && !RocketFailBackAdmissionGameTests.live(fixture.service).contains(b.transferId()),
                            "Late completion did not publish the loaded capture and drop the unloaded capture's live ID");
                    helper.assertTrue(!fixture.sources[1].isAddedToWorld(), "Stale capture was republished");
                    var restored = ModEntities.ROCKET.get().create(helper.getLevel());
                    helper.assertTrue(restored != null, "No reloaded source"); restored.load(unloaded);
                    helper.assertTrue(helper.getLevel().addFreshEntity(restored), "Entity reload failed"); fixture.sources[1] = restored;
                    recovery = new RocketTransferRecoveryService(RocketFailBackAdmissionGameTests.live(fixture.service), new HashSet<>());
                    stage = 5;
                } else if (stage == 5) {
                    journal.pollWriter(server.getTickCount()); journal.retryChecked(server);
                    if (journal.find(fixture.initial[1].transferId()).isPresent()) { recovery.recoverNext(server, journal); }
                    var a = journal.find(fixture.initial[0].transferId()).orElseThrow();
                    fixture.service.tickLive(server, journal, a);
                    if (journal.clean() && journal.find(fixture.initial[1].transferId()).isEmpty()
                            && a.phase() == RocketTransferPhase.COMMITTED
                            && RocketTransferEntities.loadedMatches(server, a, true).get(0).flightData().orElseThrow().state() == RocketFlightState.LANDED) {
                        helper.assertTrue(fixture.sources[1].isAlive() && fixture.sources[1].flightData().orElseThrow().fuel().amount() == 1000
                                && fixture.sources[1].flightData().orElseThrow().state() == RocketFlightState.FUELED,
                                "Recovery did not preserve the one unloaded flight's complete source");
                        var loaded = RocketTransferSavedData.load(CheckedSavedDataFile.readPayload(fixture.file,
                                ManagedSavedDataType.ROCKET_TRANSFERS).orElseThrow());
                        try {
                            helper.assertTrue(loaded.find(a.transferId()).orElseThrow().phase() == RocketTransferPhase.COMMITTED
                                    && loaded.find(fixture.initial[1].transferId()).isEmpty(), "Checked reload changed the recovered outcome");
                            helper.assertTrue(RocketTransferEntities.loadedMatches(server, a, true).size() == 1
                                    && RocketTransferEntities.loadedMatches(server, a, false).isEmpty()
                                    && RocketTransferEntities.loadedMatches(server, fixture.initial[1], false).size() == 1,
                                    "Late completion or recovery duplicated an entity");
                        } finally { loaded.abort(); }
                        close(); helper.succeed();
                    }
                }
                if (!closed) { fixture.pacer.pace(); }
            } catch (java.io.IOException failure) { close(); throw new java.io.UncheckedIOException(failure); }
            catch (RuntimeException | Error failure) { close(); throw failure; }
        }
        void close() {
            if (closed) { return; } closed = true; if (fault != null) { fault.close(); }
            if (recovery != null) { recovery.clear(); }
            for (var record : fixture.initial) {
                if (record != null) { RocketTransferEntities.releaseFlightTickets(helper.getLevel().getServer(), record); }
            }
            fixture.close();
        }
    }
}
