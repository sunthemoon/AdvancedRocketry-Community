package io.github.sunthemoon.advancedrocketrycommunity.resourceescrow;

import io.github.sunthemoon.advancedrocketrycommunity.ModIdentity;
import io.github.sunthemoon.advancedrocketrycommunity.gametest.GameTestTickPacer;
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
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/** Live F1/F2 to F3, not a complete-flight or terminal handoff fixture. */
@GameTestHolder(ModIdentity.MOD_ID)
@PrefixGameTestTemplate(false)
public final class EscrowGovernedSourceFlightGameTests {
    private enum Fault { NONE, MOTION, DUPLICATE }
    private EscrowGovernedSourceFlightGameTests() { }

    @GameTest(template = "empty", batch = "escrow_governed_source_flight", timeoutTicks = 600)
    public static void liveSourceProgressesToTransitAfterSaveAndJoinRevalidation(GameTestHelper helper) {
        new Scenario(helper, Fault.NONE).start();
    }

    @GameTest(template = "empty", batch = "escrow_governed_source_flight", timeoutTicks = 600)
    public static void changedNativeMotionCannotHideBehindEntityTickNormalization(GameTestHelper helper) {
        new Scenario(helper, Fault.MOTION).start();
    }

    @GameTest(template = "empty", batch = "escrow_governed_source_flight", timeoutTicks = 600)
    public static void duplicateJoinHoldsBothCandidatesWithoutLegacyElection(GameTestHelper helper) {
        new Scenario(helper, Fault.DUPLICATE).start();
    }

    private static final class Scenario {
        private final GameTestHelper helper;
        private final ServerLevel level;
        private final GameTestTickPacer pacer;
        private final Fault fault;
        private final BlockPos position;
        private final BlockPos destination;
        private final UUID transfer = UUID.randomUUID();
        private RocketEntity rocket;
        private RocketEntity duplicate;
        private ResourceEscrowSavedData data;
        private RocketFlightData initial;
        private RocketEscrowSection wrapper;
        private boolean accepted;
        private boolean observedAscent;
        private long changedAt = -1;
        private double previousY;
        private int step;

        Scenario(GameTestHelper helper, Fault fault) {
            this.helper = helper; this.fault = fault; level = helper.getLevel();
            pacer = new GameTestTickPacer(level.getServer());
            position = new BlockPos(590_008 + 128 * fault.ordinal(), 100, 590_008); destination = position.offset(32, 0, 0);
        }
        void start() {
            for (var pos : List.of(position, destination)) {
                var chunk = new ChunkPos(pos); level.getChunkSource().addRegionTicket(TicketType.START, chunk, 2, Unit.INSTANCE);
                level.getChunk(chunk.x, chunk.z);
            }
            helper.onEachTick(this::tick);
        }
        private void tick() {
            if (step == 2) { return; }
            pacer.pace();
            if (List.of(position, destination).stream().anyMatch(pos -> !level.areEntitiesLoaded(new ChunkPos(pos).toLong())
                    || !level.isPositionEntityTicking(pos))) { return; }
            var journal = RocketTransferSavedData.get(level.getServer());
            if (step == 0) {
                if (!journal.retryChecked(level.getServer())) { return; }
                prepare(); step = 1; return;
            }
            if (!accepted) { return; }
            var flight = rocket.flightData().orElseThrow();
            helper.assertTrue(initial.fuel().equals(flight.fuel()) && initial.passengers().equals(flight.passengers())
                    && wrapper.equals(rocket.escrowSection()) && !RocketEscrowGate.idle(rocket), "Source progression changed resources, manifest or hold");
            helper.assertTrue(rocket.getX() == position.getX() + 0.5 && rocket.getZ() == position.getZ() + 0.5
                    && rocket.getY() >= previousY && rocket.getY() <= position.getY() + RocketFlightLimits.FLIGHT_ALTITUDE_BLOCKS,
                    "Source escaped its bounded ascent column");
            previousY = rocket.getY();
            if (flight.state() == RocketFlightState.ASCENT) {
                observedAscent = true;
                if (rocket.getY() > position.getY() && changedAt < 0) {
                    changedAt = level.getGameTime();
                    frozenHeight = rocket.getY();
                    if (fault == Fault.MOTION) { rocket.setDeltaMovement(new Vec3(0.125, 0, 0)); }
                    else if (fault == Fault.DUPLICATE) {
                        duplicate = io.github.sunthemoon.advancedrocketrycommunity.registry.ModEntities.ROCKET.get().create(level);
                        helper.assertTrue(duplicate != null, "Duplicate fixture could not be created");
                        duplicate.load(rocket.saveWithoutId(new CompoundTag())); duplicate.setUUID(UUID.randomUUID());
                        helper.assertTrue(level.addFreshEntity(duplicate), "Duplicate fixture was not published");
                    } else {
                        verifyMotionPermits();
                        var saved = rocket.saveWithoutId(new CompoundTag());
                        helper.assertTrue(saved.getCompound("RocketEntityData").getCompound("resource_escrow").equals(wrapper.encode()),
                                "Save changed the credited wrapper");
                        io.github.sunthemoon.advancedrocketrycommunity.rocket.server.RocketRuntime.onRocketJoined(rocket);
                    }
                    return;
                }
            }
            if (fault != Fault.NONE) {
                if (changedAt < 0 || level.getGameTime() - changedAt < 30) { return; }
                helper.assertTrue(flight.state() == RocketFlightState.ASCENT && rocket.getDeltaMovement().equals(Vec3.ZERO),
                        "Fixture did not exercise entity-tick motion normalization");
                helper.assertTrue(rocket.getY() == frozenHeight, "Changed native motion retained its cached interpolation permission");
                if (fault == Fault.DUPLICATE) {
                    helper.assertTrue(duplicate.isAlive() && duplicate.flightData().orElseThrow().fuel().equals(initial.fuel())
                            && duplicate.escrowSection().equals(wrapper), "Governed duplicate was elected, rewritten or discarded");
                }
            } else {
                if (flight.state() != RocketFlightState.TRANSIT) { return; }
                helper.assertTrue(observedAscent && changedAt >= 0 && rocket.getY() == position.getY() + RocketFlightLimits.FLIGHT_ALTITUDE_BLOCKS
                        && flight.activeTransferId().filter(transfer::equals).isPresent(), "Live source skipped a phase or lost T");
            }
            var record = journal.find(transfer).orElseThrow();
            helper.assertTrue(record.phase() == RocketTransferPhase.PREPARED && journal.acknowledgedCurrent()
                    && data.ledger().armedFlights().contains(record.nativeTransfer().orElseThrow().armed()), "Source minted destination or terminal authority");
            journal.remove(transfer); RocketJournalGameTestSupport.acknowledgeFixture(helper);
            rocket.discard();
            if (duplicate != null) { duplicate.discard(); }
            for (var pos : List.of(position, destination)) {
                level.getChunkSource().removeRegionTicket(TicketType.START, new ChunkPos(pos), 2, Unit.INSTANCE);
            }
            step = 2; helper.succeed();
        }
        private double frozenHeight;

        private void verifyMotionPermits() {
            var service = EscrowRuntime.of(level.getServer()).sourceFlights();
            var boundary = new java.util.concurrent.atomic.AtomicBoolean(true);
            var refused = service.movement(rocket, boundary::get);
            var before = EscrowNativeEnvelope.rocket(rocket);
            boundary.set(false); boolean rejected = false;
            try { rocket.moveGovernedSource(refused); } catch (IllegalArgumentException expected) { rejected = true; }
            helper.assertTrue(rejected && before.equals(EscrowNativeEnvelope.rocket(rocket)), "Invalid final motion boundary changed source");
            boundary.set(true); rejected = false;
            try { rocket.moveGovernedSource(refused); } catch (IllegalArgumentException expected) { rejected = true; }
            helper.assertTrue(rejected, "Refused motion permit was reusable");
            var once = service.movement(rocket, boundary::get); rocket.moveGovernedSource(once); rejected = false;
            try { rocket.moveGovernedSource(once); } catch (IllegalArgumentException expected) { rejected = true; }
            helper.assertTrue(rejected, "Consumed motion permit was reusable");
        }

        private void prepare() {
            data = ResourceEscrowSavedData.forTests(level.getServer()); data.bind(level.getServer());
            rocket = RocketSettlementIdleGameTests.createRocket(level, position, UUID.randomUUID());
            var base = rocket.flightData().orElseThrow(); long now = level.getGameTime();
            var fuel = base.fuel().withFluidBank(false, ModIdentity.id("rocket_fuel"), null, 1000);
            rocket.updateFlightData(base.withFuel(fuel, now).withPassengers(base.passengers().assign(UUID.randomUUID()).orElseThrow()));
            var tag = rocket.saveWithoutId(new CompoundTag());
            wrapper = new RocketEscrowSection(data.ledger().id(), 7, List.of());
            tag.getCompound("RocketEntityData").put("resource_escrow", wrapper.encode());
            rocket.load(tag); helper.assertTrue(level.addFreshEntity(rocket), "Source was not published");
            initial = rocket.flightData().orElseThrow(); previousY = rocket.getY();
            var from = rocket.snapshot().orElseThrow();
            var to = from.relocated(UUID.randomUUID(), from.sourceDimension(), new RocketPosition(destination.getX(), destination.getY(), destination.getZ()), now);
            var plan = new RocketFlightPlan(3, transfer, RocketFlightPlanner.EARTH.bodyId(), RocketFlightPlanner.MOON.bodyId(),
                    from.sourceDimension(), from.sourceDimension(), 500, now).withPropulsionDebit(new RocketPropulsionDebit(500, 0,
                    fuel.propulsion().orElseThrow().revision(), fuel.resourceSignature(), RocketNativeResources.hash("F3 source fixture")));
            helper.assertTrue(EscrowGovernedPreparation.begin(rocket, initial.withPlan(plan).startCountdown(now), to, record -> true,
                    record -> accepted = true, () -> helper.fail("Source preparation was discarded")), "F1 preparation was refused");
        }
    }
}
