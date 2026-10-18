package io.github.sunthemoon.advancedrocketrycommunity.resourceescrow;

import io.github.sunthemoon.advancedrocketrycommunity.ModIdentity;
import io.github.sunthemoon.advancedrocketrycommunity.gametest.EscrowGameTestProof;
import io.github.sunthemoon.advancedrocketrycommunity.gametest.GameTestTickPacer;
import io.github.sunthemoon.advancedrocketrycommunity.persistence.migration.CheckedSavedDataPolicy;
import io.github.sunthemoon.advancedrocketrycommunity.persistence.migration.ManagedSavedDataType;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.entity.RocketEntity;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.*;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.persistence.RocketJournalGameTestSupport;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.persistence.RocketTransferSavedData;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.model.RocketNativeResources;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.model.RocketPosition;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.TicketType;
import net.minecraft.util.Unit;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/** Checked startup fixtures and real entity-slot reloads; these are A1 tests, not process-restart S2 cuts. */
@GameTestHolder(ModIdentity.MOD_ID)
@PrefixGameTestTemplate(false)
public final class EscrowGovernedReadinessGameTests {
    private enum End { DESTINATION, SOURCE_RESTART, UNAVAILABLE }
    private EscrowGovernedReadinessGameTests() { }

    // Each fixture reloads the shared checked journal. Separate batches prevent another test's write racing it.
    @GameTest(template = "empty", batch = "escrow_governed_unloaded_destination", timeoutTicks = 800)
    public static void activeFlightLoadsItsRecordedDestinationBeforeProgressing(GameTestHelper helper) {
        new Scenario(helper, End.DESTINATION).start();
    }

    @GameTest(template = "empty", batch = "escrow_governed_unloaded_source_restart", timeoutTicks = 800)
    public static void restartedCheckedFlightLoadsItsUnloadedNativeSource(GameTestHelper helper) {
        new Scenario(helper, End.SOURCE_RESTART).start();
    }

    @GameTest(template = "empty", batch = "escrow_governed_unavailable_end", timeoutTicks = 800)
    public static void unavailableRecordedLevelKeepsFlightUnchangedWithoutRepairFence(GameTestHelper helper) {
        new Scenario(helper, End.UNAVAILABLE).start();
    }

    private static final class Scenario {
        private final GameTestHelper helper;
        private final ServerLevel level;
        private final GameTestTickPacer pacer;
        private final End end;
        private final BlockPos position;
        private final BlockPos destination;
        private final UUID transfer = UUID.randomUUID();
        private RocketEntity source;
        private ResourceEscrowSavedData data;
        private EscrowGovernedLaunch prepared;
        private RocketTransferRecord record;
        private CompoundTag frozen;
        private EscrowFlightTables.Armed arm;
        private boolean stored;
        private EscrowGameTestProof<EscrowBarrier.Result> sourceProof;
        private boolean observedWait;
        private boolean observedAscent;
        private long installedAt;
        private int step;

        Scenario(GameTestHelper helper, End end) {
            this.helper = helper; this.end = end; level = helper.getLevel();
            pacer = new GameTestTickPacer(level.getServer());
            position = new BlockPos(610_008 + 512 * end.ordinal(), 100, 610_008);
            destination = position.offset(64, 0, 0);
        }
        void start() {
            var chunk = new ChunkPos(position);
            level.getChunkSource().addRegionTicket(TicketType.START, chunk, 2, Unit.INSTANCE);
            level.getChunk(chunk.x, chunk.z);
            helper.onEachTick(this::tick);
        }
        private boolean ready(BlockPos pos) {
            return level.areEntitiesLoaded(new ChunkPos(pos).toLong()) && level.isPositionEntityTicking(pos);
        }
        private void tick() {
            if (step == 4) { return; }
            pacer.pace(); var server = level.getServer();
            var journal = RocketTransferSavedData.get(server);
            if (step == 5) { return; }
            if (step == 0) {
                if (!ready(position) || !journal.retryChecked(server)) { return; }
                data = ResourceEscrowSavedData.forTests(server);
                if (!data.bind(server).writerIdle()) { return; }
                prepare(journal); step = 1; return;
            }
            if (step == 1) {
                if (!stored) { checkpoint(); return; }
                if (end == End.SOURCE_RESTART) {
                    level.getChunkSource().removeRegionTicket(TicketType.START, new ChunkPos(position), 2, Unit.INSTANCE);
                    step = 2; return;
                }
                installCheckedRoots(journal); return;
            }
            if (step == 2) {
                // No active journal is installed yet, so ordinary native unloading can save and evict the source.
                if (level.areEntitiesLoaded(new ChunkPos(position).toLong()) || level.getEntity(source.getUUID()) != null) { return; }
                helper.assertTrue(source.isRemoved(), "Source fixture did not actually unload");
                installCheckedRoots(journal); return;
            }
            helper.assertTrue(data.ledger().armedFlights().contains(arm)
                    && journal.find(transfer).filter(record::equals).isPresent(), "Readiness wait changed checked authority");
            boolean loaded = ready(position) && end != End.UNAVAILABLE && ready(destination);
            var entity = level.getEntity(record.sourceEntityId());
            if (!loaded) {
                observedWait = true;
                if (entity instanceof RocketEntity rocket) {
                    helper.assertTrue(frozen.equals(EscrowNativeEnvelope.rocket(rocket)), "Loading wait changed the source");
                }
                helper.assertTrue(level.getEntity(arm.destination()) == null, "Loading wait published a destination");
                if (end == End.UNAVAILABLE && server.getTickCount() - installedAt >= 80) {
                    helper.assertTrue(!ready(destination) && level.getChunkSource().getChunkNow(destination.getX() >> 4,
                            destination.getZ() >> 4) == null, "Unavailable Level redirected a ticket to the local Level");
                    helper.assertTrue(EscrowRuntime.of(server).governedFlights().classify(server, journal, record).route()
                            == EscrowGovernedDispatch.Route.ACTIVE && !RocketEscrowGate.idle(source),
                            "Loading hold lost its classification or released resources");
                    finish(journal); return;
                }
                return;
            }
            helper.assertTrue(entity instanceof RocketEntity, "Ready source slot lost its checkpointed entity");
            var rocket = (RocketEntity) entity;
            helper.assertTrue(rocket.flightData().orElseThrow().fuel().equals(record.sourceFlightData().fuel())
                    && rocket.escrowSection().equals(RocketEscrowSection.decode(frozen.getCompound("wrapper")))
                    && rocket.flightData().orElseThrow().passengers().equals(record.sourceFlightData().passengers())
                    && !RocketEscrowGate.idle(rocket), "Loading/restart changed source resources, assignments or hold");
            if (end == End.SOURCE_RESTART) { helper.assertTrue(rocket != source, "Restart reused the old entity instance"); }
            var state = rocket.flightData().orElseThrow().state();
            observedAscent |= state == RocketFlightState.ASCENT && rocket.getY() > position.getY();
            if (state != RocketFlightState.TRANSIT) { return; }
            helper.assertTrue(observedWait && observedAscent && rocket.getY() == position.getY() + RocketFlightLimits.FLIGHT_ALTITUDE_BLOCKS,
                    "Recorded-end tickets did not resume a held flight through its source phases");
            finish(journal);
        }

        private void prepare(RocketTransferSavedData journal) {
            helper.assertTrue(!ready(destination) && level.getChunkSource().getChunkNow(destination.getX() >> 4,
                    destination.getZ() >> 4) == null, "Destination fixture was already loaded");
            source = RocketSettlementIdleGameTests.createRocket(level, position, UUID.randomUUID());
            var base = source.flightData().orElseThrow(); long now = level.getGameTime();
            var fuel = base.fuel().withFluidBank(false, ModIdentity.id("rocket_fuel"), null, 1000);
            source.updateFlightData(base.withFuel(fuel, now).withPassengers(base.passengers().assign(UUID.randomUUID()).orElseThrow()));
            var tag = source.saveWithoutId(new CompoundTag());
            tag.getCompound("RocketEntityData").put("resource_escrow", new RocketEscrowSection(data.ledger().id(), 7, List.of()).encode());
            source.load(tag); helper.assertTrue(level.addFreshEntity(source), "Readiness source was not added");
            frozen = EscrowNativeEnvelope.rocket(source); var from = source.snapshot().orElseThrow();
            var dimension = end == End.UNAVAILABLE ? ModIdentity.id("readiness_missing_level") : from.sourceDimension();
            var to = from.relocated(UUID.randomUUID(), dimension, new RocketPosition(destination.getX(), destination.getY(), destination.getZ()), now);
            var plan = new RocketFlightPlan(3, transfer, RocketFlightPlanner.EARTH.bodyId(), RocketFlightPlanner.MOON.bodyId(),
                    from.sourceDimension(), dimension, 500, now).withPropulsionDebit(new RocketPropulsionDebit(500, 0,
                    fuel.propulsion().orElseThrow().revision(), fuel.resourceSignature(), RocketNativeResources.hash("Readiness fixture")));
            var guard = journal.acknowledgedGuard(source.assemblyTransactionId().orElseThrow()).orElseThrow();
            prepared = EscrowGovernedLaunch.prepare(data.ledger(), frozen, from, source.flightData().orElseThrow().withPlan(plan).startCountdown(now),
                    to, UUID.randomUUID(), new EscrowGuard(guard.logical(), null, Math.max(1, guard.sequence()), guard.rootDigest()));
        }

        private void checkpoint() {
            if (sourceProof == null) {
                var capture = new EscrowFlightSource(source); var runtime = EscrowRuntime.of(level.getServer());
                sourceProof = new EscrowGameTestProof<>(level.getServer()::getTickCount,
                        (tick, completed) -> runtime.barrier().begin(runtime.store(level), capture.endpoint,
                                capture.frontier, capture.validator(), tick, completed),
                        EscrowBarrier.Result::confirmed, EscrowBarrier.Result::reason, result -> {
                            helper.assertTrue(result.confirmed(), "Readiness source checkpoint failed: " + result.reason());
                            result.proof().release(); stored = true;
                        });
            }
            sourceProof.tick();
        }

        private void installCheckedRoots(RocketTransferSavedData journal) {
            var server = level.getServer();
            helper.assertTrue(data.bind(server).writerIdle() && journal.acknowledgedCurrent(), "Startup fixture raced a checked writer");
            // Seed an explicit startup fixture, not a live F1 admission. Keep the real coordinator/writer
            // so other shared-world fixtures retain their lifecycle; publish F2 only in its exact callback.
            step = 5;
            helper.assertTrue(data.bind(server).flightWrite(source.assemblyTransactionId().orElseThrow(), prepared::preflight,
                    () -> true, committed -> {
                        helper.assertTrue(committed, "Startup fixture's ledger write was not acknowledged");
                        arm = data.ledger().armedFlights().stream().filter(value -> value.transfer().equals(transfer)).findFirst().orElseThrow();
                        record = prepared.record(arm);
                        journal.put(record); RocketJournalGameTestSupport.acknowledgeFixture(helper);
                        var journalFile = server.getWorldPath(LevelResource.ROOT).resolve("data").resolve(ManagedSavedDataType.ROCKET_TRANSFERS.fileName());
                        var saved = CheckedSavedDataPolicy.atomic(ManagedSavedDataType.ROCKET_TRANSFERS).read(journalFile).orElseThrow();
                        journal.stop();
                        var restored = RocketTransferSavedData.load(saved);
                        helper.assertTrue(restored.operational() && saved.equals(restored.save(new CompoundTag())),
                                "Startup fixture changed the complete serialized journal");
                        record = restored.find(transfer).orElseThrow();
                        server.overworld().getDataStorage().set(ManagedSavedDataType.ROCKET_TRANSFERS.dataName(), restored);
                        installedAt = server.getTickCount();
                        observedWait = !ready(position) || !ready(destination) || end == End.UNAVAILABLE;
                        step = 3;
                    }), "Startup fixture's ledger write was refused");
        }

        private void finish(RocketTransferSavedData journal) {
            journal.remove(transfer); RocketJournalGameTestSupport.acknowledgeFixture(helper);
            if (level.getEntity(record.sourceEntityId()) instanceof RocketEntity rocket) { rocket.discard(); }
            level.getChunkSource().removeRegionTicket(TicketType.START, new ChunkPos(position), 2, Unit.INSTANCE);
            step = 4; helper.succeed();
        }
    }
}
