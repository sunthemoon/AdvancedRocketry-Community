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

/** Live F1-F4, not a complete-flight or terminal handoff fixture. */
@GameTestHolder(ModIdentity.MOD_ID)
@PrefixGameTestTemplate(false)
public final class EscrowGovernedSourceFlightGameTests {
    private enum Fault { NONE, MOTION, DUPLICATE, CLOCK }
    private EscrowGovernedSourceFlightGameTests() { }

    @GameTest(template = "empty", batch = "escrow_governed_source_progress", timeoutTicks = 600)
    public static void liveSourceProgressesToTransitAfterSaveAndJoinRevalidation(GameTestHelper helper) {
        new Scenario(helper, Fault.NONE).start();
    }

    @GameTest(template = "empty", batch = "escrow_governed_source_motion", timeoutTicks = 600)
    public static void changedNativeMotionCannotHideBehindEntityTickNormalization(GameTestHelper helper) {
        new Scenario(helper, Fault.MOTION).start();
    }

    @GameTest(template = "empty", batch = "escrow_governed_source_duplicate", timeoutTicks = 600)
    public static void duplicateJoinHoldsBothCandidatesWithoutLegacyElection(GameTestHelper helper) {
        new Scenario(helper, Fault.DUPLICATE).start();
    }

    @GameTest(template = "empty", batch = "escrow_governed_source_clock", timeoutTicks = 600)
    public static void restoredAscentAheadOfLevelClockResumesWithoutResourceChanges(GameTestHelper helper) {
        new Scenario(helper, Fault.CLOCK).start();
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
        private boolean discarded;
        private boolean observedAscent;
        private long changedAt = -1;
        private double previousY;
        private CompoundTag checkpoint;
        private boolean restoredCheckpoint;
        private boolean repeatedPublication;
        private int step;

        Scenario(GameTestHelper helper, Fault fault) {
            this.helper = helper; this.fault = fault; level = helper.getLevel();
            pacer = new GameTestTickPacer(level.getServer());
            position = new BlockPos(590_008 + 128 * fault.ordinal(), 100, 590_008);
            destination = position.offset(fault == Fault.CLOCK ? 4 : 32, 0, 0);
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
            if (step == 3) { publication(journal); return; }
            if (step == 0) {
                if (!journal.retryChecked(level.getServer())) { return; }
                prepare(); step = 1; return;
            }
            helper.assertTrue(!discarded, "Source preparation was discarded");
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
                    } else if (fault == Fault.CLOCK) {
                        var saved = rocket.saveWithoutId(new CompoundTag());
                        var future = RocketFlightData.restore(flight.schemaVersion(), flight.logicalRocketId(), flight.state(), flight.fuel(),
                                flight.plan().orElseThrow(), flight.passengers(), flight.currentBody(), flight.currentDimension(),
                                flight.currentOrigin(), flight.stateStartedGameTime() + 10_000, null, flight.currentTarget().orElseThrow());
                        saved.getCompound("RocketEntityData").put("flight_data", io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.persistence.RocketFlightNbtCodec.encode(future));
                        // Reload another entity instance without clearing the service. Its saved phase
                        // is newer than the Level clock; the old live-instance cache must not survive.
                        rocket.discard();
                        rocket = io.github.sunthemoon.advancedrocketrycommunity.registry.ModEntities.ROCKET.get().create(level);
                        helper.assertTrue(rocket != null, "Reloaded source fixture could not be created");
                        rocket.load(saved);
                        helper.assertTrue(level.addFreshEntity(rocket), "Reloaded source was not published");
                        helper.assertTrue(rocket.flightData().orElseThrow().stateStartedGameTime() > level.getGameTime(),
                                "Restored clock fixture did not precede its source timestamp");
                        io.github.sunthemoon.advancedrocketrycommunity.rocket.server.RocketRuntime.onRocketJoined(rocket);
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
            if (fault == Fault.MOTION || fault == Fault.DUPLICATE) {
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
            if (fault == Fault.NONE || fault == Fault.CLOCK) { step = 3; return; }
            finish(journal, null);
        }

        private void publication(RocketTransferSavedData journal) {
            var record = journal.find(transfer).orElseThrow();
            if (record.phase() != RocketTransferPhase.DESTINATION_SPAWNED || !journal.acknowledgedCurrent()) { return; }
            var destinationId = record.nativeTransfer().orElseThrow().armed().destination();
            if (fault == Fault.CLOCK && !restoredCheckpoint) {
                helper.assertTrue(level.getEntity(destinationId) == null, "F4 published without awaiting fresh slot proofs");
                // Mixed save order: F4 survives but the source has only its exact F1 checkpoint.
                // Replace the entity instance, never mutate a live protected source through /data.
                rocket.discard(); rocket = io.github.sunthemoon.advancedrocketrycommunity.registry.ModEntities.ROCKET.get().create(level);
                helper.assertTrue(rocket != null, "F1 checkpoint source could not be created");
                rocket.load(checkpoint.copy()); helper.assertTrue(level.addFreshEntity(rocket), "F1 checkpoint source was not added");
                restoredCheckpoint = true; return;
            }
            if (!(level.getEntity(destinationId) instanceof RocketEntity arrival)) { return; }
            helper.assertTrue(record.nativeTransfer().orElseThrow().destinationEnvelope(record).equals(EscrowNativeEnvelope.rocket(arrival)),
                    "Published destination differs from its complete fixed envelope");
            helper.assertTrue(!arrival.getUUID().equals(rocket.getUUID()) && arrival.escrowSection().ledger().equals(wrapper.ledger())
                    && arrival.escrowSection().epoch() == 0 && arrival.escrowSection().markers().isEmpty()
                    && arrival.flightData().orElseThrow().fuel().totalAmount() == 500
                    && arrival.flightData().orElseThrow().fuel().committedDebits().equals(List.of(transfer)),
                    "Publication lost the independent epoch or exact one-time debit");
            helper.assertTrue(rocket.isAlive() && initial.fuel().equals(rocket.flightData().orElseThrow().fuel())
                    && initial.passengers().equals(rocket.flightData().orElseThrow().passengers()) && wrapper.equals(rocket.escrowSection())
                    && data.ledger().armedFlights().contains(record.nativeTransfer().orElseThrow().armed())
                    && !RocketEscrowGate.idle(rocket) && !RocketEscrowGate.idle(arrival),
                    "F4 changed source credit or released either resource lock");
            helper.assertTrue(arrival.getPassengers().isEmpty(), "F4 transferred passengers before F5 acknowledgement");
            if (fault == Fault.CLOCK) {
                helper.assertTrue(restoredCheckpoint && EscrowNativeEnvelope.rocket(rocket).equals(record.nativeTransfer().orElseThrow().sourceEnvelope(record)),
                        "F4 recovery replaced or progressed the F1-checkpointed source");
            }
            if (fault == Fault.NONE && !repeatedPublication) {
                arrival.discard(); repeatedPublication = true; return;
            }
            finish(journal, arrival);
        }

        private void finish(RocketTransferSavedData journal, RocketEntity arrival) {
            journal.remove(transfer); RocketJournalGameTestSupport.acknowledgeFixture(helper);
            rocket.discard();
            if (arrival != null) { arrival.discard(); }
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
            checkpoint = rocket.saveWithoutId(new CompoundTag());
            initial = rocket.flightData().orElseThrow(); previousY = rocket.getY();
            var from = rocket.snapshot().orElseThrow();
            var unloaded = from.relocated(UUID.randomUUID(), from.sourceDimension(), new RocketPosition(1_100_008, 100, 1_100_008), now);
            var unloadedChunk = new ChunkPos(unloaded.sourceOrigin().x() >> 4, unloaded.sourceOrigin().z() >> 4);
            helper.assertTrue(level.getChunkSource().getChunkNow(unloadedChunk.x, unloadedChunk.z) == null
                    && !new io.github.sunthemoon.advancedrocketrycommunity.rocket.transfer.RocketLandingPadSelector().availableLoaded(level, unloaded)
                    && level.getChunkSource().getChunkNow(unloadedChunk.x, unloadedChunk.z) == null,
                    "Governed pad check loaded an absent terrain chunk");
            var to = from.relocated(UUID.randomUUID(), from.sourceDimension(), new RocketPosition(destination.getX(), destination.getY(), destination.getZ()), now);
            var plan = new RocketFlightPlan(3, transfer, RocketFlightPlanner.EARTH.bodyId(), RocketFlightPlanner.MOON.bodyId(),
                    from.sourceDimension(), from.sourceDimension(), 500, now).withPropulsionDebit(new RocketPropulsionDebit(500, 0,
                    fuel.propulsion().orElseThrow().revision(), fuel.resourceSignature(), RocketNativeResources.hash("F3 source fixture")));
            helper.assertTrue(EscrowGovernedPreparation.begin(rocket, initial.withPlan(plan).startCountdown(now), to, record -> true,
                    record -> accepted = true, () -> discarded = true), "F1 preparation was refused");
        }
    }
}
