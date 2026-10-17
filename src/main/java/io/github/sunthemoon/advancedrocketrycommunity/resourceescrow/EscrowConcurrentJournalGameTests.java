package io.github.sunthemoon.advancedrocketrycommunity.resourceescrow;

import io.github.sunthemoon.advancedrocketrycommunity.ModIdentity;
import io.github.sunthemoon.advancedrocketrycommunity.gametest.GameTestTickPacer;
import io.github.sunthemoon.advancedrocketrycommunity.persistence.migration.CheckedSavedDataFile;
import io.github.sunthemoon.advancedrocketrycommunity.persistence.migration.ManagedSavedDataType;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.entity.RocketEntity;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.*;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.persistence.RocketJournalGameTestSupport;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.persistence.RocketJournalSchedulingGameTestSupport;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.persistence.RocketTransferSavedData;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.model.RocketNativeResources;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.model.RocketPosition;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/** G4 isolates checked ARMED fixtures; real F3 and F4 executors share one detached production-sized journal. */
@GameTestHolder(ModIdentity.MOD_ID)
@PrefixGameTestTemplate(false)
public final class EscrowConcurrentJournalGameTests {
    private EscrowConcurrentJournalGameTests() { }
    @GameTest(template = "rocket_test", batch = "journal_concurrent_flight", timeoutTicks = 650)
    public static void anotherGovernedSourceAdvancesWhileF4AwaitsItsWriter(GameTestHelper helper) { new Scenario(helper).start(); }

    private static final class Scenario {
        final GameTestHelper helper;
        final RocketTransferSavedData journal = new RocketTransferSavedData();
        final RocketEntity[] sources = new RocketEntity[2];
        final RocketTransferRecord[] records = new RocketTransferRecord[2];
        final EscrowGovernedSourceFlight sourceFlight = new EscrowGovernedSourceFlight();
        final EscrowGovernedDestination destination = new EscrowGovernedDestination();
        final GameTestTickPacer pacer;
        ResourceEscrowSavedData previous, data;
        RocketJournalSchedulingGameTestSupport.HeldWrite held;
        Path directory, file;
        int stage, waits, rises;
        boolean closed, swapped;
        Scenario(GameTestHelper helper) { this.helper = helper; pacer = new GameTestTickPacer(helper.getLevel().getServer()); }
        void start() { helper.onEachTick(this::tick); }
        void tick() {
            if (closed) { return; }
            try {
                helper.assertTrue(++waits < 600, "Governed concurrency fixture exceeded its bounded wait");
                var server = helper.getLevel().getServer();
                if (stage == 0) { setup(); }
                else if (stage == 1) {
                    var result = sourceFlight.tick(server, journal, records[0], sources[0], () -> true);
                    helper.assertTrue(result != EscrowGovernedSourceFlight.Result.HELD, "First governed source was held before F4");
                    if (result == EscrowGovernedSourceFlight.Result.DESTINATION_DUE) {
                        journal.setDirty(); held = RocketJournalSchedulingGameTestSupport.hold(helper, journal, file); stage = 2;
                    }
                } else if (stage == 2 && held.entered()) {
                    // F4 uses the runtime's source-clock binding; establish it against the same exact source.
                    EscrowRuntime.of(server).sourceFlights().tick(server, journal, records[0], sources[0], () -> true);
                    destination.tick(server, journal, records[0], sources[0], (record, source, arrival) -> true);
                    helper.assertTrue(journal.find(records[0].transferId()).orElseThrow().phase() == RocketTransferPhase.DESTINATION_SPAWNED
                            && journal.outstandingEffectCount() == 1, "F4 did not queue its real destination continuation");
                    stage = 3;
                } else if (stage == 3) {
                    journal.pollWriter(server.getTickCount());
                    helper.assertTrue(EscrowRuntime.of(server).governedFlights().classify(server, journal, records[1]).route()
                            == EscrowGovernedDispatch.Route.ACTIVE, "Disjoint F4 introduced a governed journal fence");
                    var before = sources[1].flightData().orElseThrow(); double y = sources[1].getY();
                    var result = sourceFlight.tick(server, journal, records[1], sources[1], () -> true);
                    helper.assertTrue(result != EscrowGovernedSourceFlight.Result.HELD, "Disjoint F4 held the second source executor");
                    if (before.state() == RocketFlightState.ASCENT && before.stateStartedGameTime() < helper.getLevel().getGameTime()) {
                        helper.assertTrue(sources[1].getY() > y, "Actionable governed ascent missed a movement step"); rises++;
                    }
                    helper.assertTrue(helper.getLevel().getEntity(records[0].nativeTransfer().orElseThrow().armed().destination()) == null,
                            "F4 published before acknowledgement");
                    if (result == EscrowGovernedSourceFlight.Result.DESTINATION_DUE) {
                        helper.assertTrue(rises >= RocketFlightLimits.ASCENT_TICKS - 1, "Governed fixture did not observe the full ascent");
                        held.close(); stage = 4;
                    }
                } else if (stage == 4) {
                    journal.pollWriter(server.getTickCount()); journal.retryChecked(server);
                    if (journal.clean()) {
                        var loaded = RocketTransferSavedData.load(CheckedSavedDataFile.readPayload(file, ManagedSavedDataType.ROCKET_TRANSFERS).orElseThrow());
                        try {
                            helper.assertTrue(loaded.find(records[0].transferId()).orElseThrow().phase() == RocketTransferPhase.DESTINATION_SPAWNED
                                    && loaded.find(records[1].transferId()).orElseThrow().phase() == RocketTransferPhase.PREPARED,
                                    "Checked reload lost the independent governed phases");
                        } finally { loaded.abort(); }
                        close(); helper.succeed();
                    }
                }
                if (!closed) { pacer.pace(); }
            } catch (java.io.IOException failure) { close(); throw new java.io.UncheckedIOException(failure); }
            catch (RuntimeException | Error failure) { close(); throw failure; }
        }
        void setup() throws java.io.IOException {
            var level = helper.getLevel(); var server = level.getServer();
            for (int i = 0; i < 2; i++) {
                if (!level.isPositionEntityTicking(helper.absolutePos(new BlockPos(3, 2, 3 + i * 5)))) { return; }
            }
            previous = ResourceEscrowSavedData.forTests(server);
            if (!previous.bind(server).writerIdle()) { return; }
            directory = Files.createTempDirectory("arce-governed-concurrency-");
            file = directory.resolve(ManagedSavedDataType.ROCKET_TRANSFERS.fileName());
            RocketJournalGameTestSupport.confirmDetached(helper, journal, file);
            var root = EscrowLedger.empty(UUID.randomUUID());
            for (int i = 0; i < 2; i++) {
                long now = level.getGameTime(); var pos = helper.absolutePos(new BlockPos(3, 2, 3 + i * 5));
                var rocket = RocketSettlementIdleGameTests.createRocket(level, pos, UUID.randomUUID()); sources[i] = rocket;
                var base = rocket.flightData().orElseThrow(); var fuel = base.fuel().withFluidBank(false, ModIdentity.id("rocket_fuel"), null, 1000);
                rocket.updateFlightData(base.withFuel(fuel, now));
                var tag = rocket.saveWithoutId(new CompoundTag());
                tag.getCompound("RocketEntityData").put("resource_escrow", new RocketEscrowSection(root.id(), 7, List.of()).encode()); rocket.load(tag);
                var from = rocket.snapshot().orElseThrow(); var to = from.relocated(UUID.randomUUID(), from.sourceDimension(),
                        new RocketPosition(pos.getX() + 10, pos.getY(), pos.getZ()), now);
                var plan = new RocketFlightPlan(3, UUID.randomUUID(), RocketFlightPlanner.EARTH.bodyId(), RocketFlightPlanner.MOON.bodyId(),
                        from.sourceDimension(), to.sourceDimension(), 500, now).withPropulsionDebit(new RocketPropulsionDebit(500, 0,
                        fuel.propulsion().orElseThrow().revision(), fuel.resourceSignature(), RocketNativeResources.hash("G4 journal fixture")));
                var guard = journal.logicalGuard(base.logicalRocketId()).orElseThrow();
                var prepared = EscrowGovernedLaunch.prepare(root, EscrowGovernedEnvelope.capture(rocket), from,
                        rocket.flightData().orElseThrow().withPlan(plan).startCountdown(now), to, UUID.randomUUID(),
                        new EscrowGuard(guard.logical(), null, guard.sequence(), guard.rootDigest()));
                root = prepared.preflight(root, root.writerGeneration() + 1);
                var arm = root.armedFlights().stream().filter(value -> value.logical().equals(base.logicalRocketId())).findFirst().orElseThrow();
                records[i] = prepared.record(arm); journal.put(records[i]);
            }
            var ledgerFile = directory.resolve(ManagedSavedDataType.RESOURCE_ESCROW.fileName()); var checkedRoot = root;
            CheckedSavedDataFile.replace(ledgerFile, ManagedSavedDataType.RESOURCE_ESCROW, () -> EscrowCodec.encode(checkedRoot));
            data = ResourceEscrowSavedData.load(CheckedSavedDataFile.readPayload(ledgerFile, ManagedSavedDataType.RESOURCE_ESCROW).orElseThrow());
            var writer = new EscrowLedgerWriter(ledgerFile, root.writerGeneration()); writer.baseline(data.ledger());
            data.attach(new EscrowCoordinator(data, writer), () -> true);
            server.overworld().getDataStorage().set(ManagedSavedDataType.RESOURCE_ESCROW.dataName(), data); swapped = true;
            for (var rocket : sources) { helper.assertTrue(level.addFreshEntity(rocket), "Governed source was not published"); }
            RocketJournalGameTestSupport.confirmDetached(helper, journal, file); stage = 1;
        }
        void close() {
            if (closed) { return; } closed = true; if (held != null) { held.close(); }
            destination.close(); sourceFlight.clear(); journal.abort();
            for (var record : records) { if (record != null) {
                var arrival = helper.getLevel().getEntity(record.nativeTransfer().orElseThrow().armed().destination());
                if (arrival != null) { arrival.discard(); }
            } }
            for (var source : sources) { if (source != null) { source.discard(); } }
            if (data != null) { data.abort(); }
            if (swapped) { helper.getLevel().getServer().overworld().getDataStorage().set(ManagedSavedDataType.RESOURCE_ESCROW.dataName(), previous); }
            if (directory != null) {
                try (var paths = Files.walk(directory)) {
                    for (var path : paths.sorted(java.util.Comparator.reverseOrder()).toList()) { Files.delete(path); }
                } catch (java.io.IOException failure) { throw new java.io.UncheckedIOException(failure); }
            }
        }
    }
}
