package io.github.sunthemoon.advancedrocketrycommunity.resourceescrow;

import io.github.sunthemoon.advancedrocketrycommunity.ModIdentity;
import io.github.sunthemoon.advancedrocketrycommunity.gametest.GameTestTickPacer;
import io.github.sunthemoon.advancedrocketrycommunity.persistence.migration.ManagedSavedDataType;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.entity.RocketEntity;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.*;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.persistence.RocketJournalGameTestSupport;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.persistence.RocketTransferSavedData;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.model.RocketNativeResources;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.model.RocketPosition;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
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

/** Entity-adapter test with a detached acknowledged authority fixture, not F1/F6 or live-dispatch evidence. */
@GameTestHolder(ModIdentity.MOD_ID)
@PrefixGameTestTemplate(false)
public final class EscrowGovernedSourceRecoveryGameTests {
    private EscrowGovernedSourceRecoveryGameTests() { }

    @GameTest(template = "empty", batch = "escrow_governed_source_adapter", timeoutTicks = 400)
    public static void exactSourceResumeAndReturnKeepNativeResourcesAndHold(GameTestHelper helper) {
        new Scenario(helper).start();
    }

    private static final class Scenario {
        private final GameTestHelper helper;
        private final ServerLevel level;
        private final GameTestTickPacer pacer;
        private final BlockPos position = new BlockPos(570_008, 100, 570_008);
        private final BlockPos destination = position.offset(64, 0, 0);
        private final RocketTransferSavedData journal = new RocketTransferSavedData();
        private RocketEntity rocket;
        private EscrowGovernedDispatch.Authority authority;
        private EscrowLedger root;
        private int step;

        Scenario(GameTestHelper helper) {
            this.helper = helper; level = helper.getLevel(); pacer = new GameTestTickPacer(level.getServer());
        }
        void start() {
            for (var pos : List.of(position, destination)) {
                var chunk = new ChunkPos(pos);
                level.getChunkSource().addRegionTicket(TicketType.START, chunk, 2, Unit.INSTANCE);
                level.getChunk(chunk.x, chunk.z);
            }
            helper.onEachTick(this::tick);
        }
        private boolean loaded() {
            return List.of(position, destination).stream().allMatch(pos -> level.areEntitiesLoaded(new ChunkPos(pos).toLong())
                    && level.isPositionEntityTicking(pos));
        }
        private void tick() {
            if (step == 3) { return; }
            pacer.pace(); if (!loaded()) { return; }
            long now = level.getGameTime();
            if (step == 0) { prepare(now); step = 1; return; }
            var flight = rocket.flightData().orElseThrow();
            if (step == 1 && now - flight.stateStartedGameTime() >= RocketFlightLimits.COUNTDOWN_TICKS) {
                rocket.advanceGovernedSource(EscrowGovernedSourceAction.issue(authority,
                        EscrowGovernedSourceAction.Action.COMPLETE_COUNTDOWN, EscrowNativeEnvelope.rocket(rocket), now, this::loaded));
                step = 2; return;
            }
            if (step != 2 || now - flight.stateStartedGameTime() < 40) { return; }
            // A plan-authorized midpoint, set by the fixture rather than an unimplemented motion dispatcher.
            rocket.setPos(position.getX() + 0.5, position.getY() + 24, position.getZ() + 0.5);
            var before = EscrowNativeEnvelope.rocket(rocket);
            var boundary = new AtomicBoolean(true);
            var stale = EscrowGovernedSourceRecovery.issue(authority, EscrowGovernedSourceRecovery.Action.RETURN_EXACT_SOURCE,
                    this::observe, now, boundary::get);
            boundary.set(false);
            boolean refused = false;
            try { rocket.recoverGovernedSource(stale); }
            catch (IllegalArgumentException expected) { refused = true; }
            helper.assertTrue(refused && before.equals(EscrowNativeEnvelope.rocket(rocket)), "Invalid final boundary mutated the native entity");
            var resume = EscrowGovernedSourceRecovery.issue(authority, EscrowGovernedSourceRecovery.Action.RESUME_EXACT_SOURCE,
                    this::observe, now, this::loaded);
            rocket.recoverGovernedSource(resume);
            helper.assertTrue(before.equals(EscrowNativeEnvelope.rocket(rocket)), "Resume replaced a native source field");
            var wrapper = rocket.escrowSection(); var snapshot = rocket.snapshot().orElseThrow();
            var fuel = rocket.flightData().orElseThrow().fuel(); var passengers = rocket.flightData().orElseThrow().passengers();
            rocket.recoverGovernedSource(EscrowGovernedSourceRecovery.issue(authority,
                    EscrowGovernedSourceRecovery.Action.RETURN_EXACT_SOURCE, this::observe, now, this::loaded));
            helper.assertTrue(rocket.flightData().orElseThrow().state() == RocketFlightState.FUELED
                    && rocket.getX() == position.getX() + 0.5 && rocket.getY() == position.getY()
                    && rocket.getZ() == position.getZ() + 0.5, "Return did not restore only the source flight/position");
            helper.assertTrue(fuel.equals(rocket.flightData().orElseThrow().fuel()) && wrapper.equals(rocket.escrowSection())
                    && snapshot.equals(rocket.snapshot().orElseThrow())
                    && passengers.equals(rocket.flightData().orElseThrow().passengers()), "Return changed credited source data");
            helper.assertTrue(!RocketEscrowGate.idle(rocket) && root.armedFlights().get(0).outcome() == EscrowFlightTables.Outcome.ARMED
                    && journal.acknowledgedCurrent() && journal.entries().get(0).phase() == RocketTransferPhase.PREPARED,
                    "Return fabricated a terminal acknowledgement or ordinary idle exemption");
            // Dispose only this detached adapter fixture; no gameplay journal/ledger authority is removed.
            rocket.discard(); journal.stop();
            for (var pos : List.of(position, destination)) {
                level.getChunkSource().removeRegionTicket(TicketType.START, new ChunkPos(pos), 2, Unit.INSTANCE);
            }
            step = 3; helper.succeed();
        }
        private EscrowGovernedSourceRecovery.Observation observe() {
            return new EscrowGovernedSourceRecovery.Observation(loaded(), List.of(EscrowNativeEnvelope.rocket(rocket)), List.of());
        }
        private void prepare(long now) {
            root = EscrowLedger.empty(UUID.randomUUID());
            rocket = RocketSettlementIdleGameTests.createRocket(level, position, UUID.randomUUID());
            var initial = rocket.flightData().orElseThrow();
            var fuel = initial.fuel().withFluidBank(false, ModIdentity.id("rocket_fuel"), null, 1000);
            rocket.updateFlightData(initial.withFuel(fuel, now).withPassengers(initial.passengers().assign(UUID.randomUUID()).orElseThrow()));
            var parent = rocket.saveWithoutId(new CompoundTag());
            parent.getCompound("RocketEntityData").put("resource_escrow", new RocketEscrowSection(root.id(), 7, List.of()).encode());
            rocket.load(parent); helper.assertTrue(level.addFreshEntity(rocket), "Native source fixture was not added");
            var file = level.getServer().getWorldPath(LevelResource.ROOT).resolve("governed-source-adapter").resolve(rocket.getUUID().toString())
                    .resolve(ManagedSavedDataType.ROCKET_TRANSFERS.fileName());
            try { Files.createDirectories(file.getParent()); }
            catch (IOException failure) { throw new UncheckedIOException(failure); }
            RocketJournalGameTestSupport.confirmDetached(helper, journal, file);
            var transfer = UUID.randomUUID(); var from = rocket.snapshot().orElseThrow();
            var to = from.relocated(UUID.randomUUID(), from.sourceDimension(),
                    new RocketPosition(destination.getX(), destination.getY(), destination.getZ()), now);
            var plan = new RocketFlightPlan(3, transfer, RocketFlightPlanner.EARTH.bodyId(), RocketFlightPlanner.MOON.bodyId(),
                    from.sourceDimension(), from.sourceDimension(), 500, now).withPropulsionDebit(new RocketPropulsionDebit(500, 0,
                    fuel.propulsion().orElseThrow().revision(), fuel.resourceSignature(), RocketNativeResources.hash("source adapter fixture")));
            var guard = journal.acknowledgedGuard(rocket.assemblyTransactionId().orElseThrow()).orElseThrow();
            var prepared = EscrowGovernedLaunch.prepare(root, EscrowNativeEnvelope.rocket(rocket), from,
                    rocket.flightData().orElseThrow().withPlan(plan).startCountdown(now), to, UUID.randomUUID(),
                    new EscrowGuard(guard.logical(), null, Math.max(1, guard.sequence()), guard.rootDigest()));
            root = prepared.preflight(root, 1).generation(1);
            var record = prepared.record(root.armedFlights().get(0)); journal.put(record);
            RocketJournalGameTestSupport.confirmDetached(helper, journal, file);
            authority = EscrowGovernedDispatch.acquire(() -> root, () -> true, journal, record);
            helper.assertTrue(authority != null, "Detached fixture has no checked record authority");
            rocket.advanceGovernedSource(EscrowGovernedSourceAction.issue(authority,
                    EscrowGovernedSourceAction.Action.START_COUNTDOWN, EscrowNativeEnvelope.rocket(rocket), now, this::loaded));
        }
    }
}
