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

/** Live governed flights, terminal joint proofs, checked handoff and ordered cleanup. */
@GameTestHolder(ModIdentity.MOD_ID)
@PrefixGameTestTemplate(false)
public final class EscrowGovernedSourceFlightGameTests {
    private enum Fault { NONE, MOTION, DUPLICATE, CLOCK, LANDING, POSITION, REST_POSITION }
    private enum Departure { NONE, ASCENT, LANDED, LOGOUT, MOUNTED_LOGOUT }
    private EscrowGovernedSourceFlightGameTests() { }

    @GameTest(template = "empty", batch = "escrow_governed_source_progress", timeoutTicks = 600)
    public static void liveSourceProgressesToTransitAfterSaveAndJoinRevalidation(GameTestHelper helper) {
        new Scenario(helper, Fault.NONE).start();
    }

    @GameTest(template = "empty", batch = "escrow_governed_source_motion", timeoutTicks = 600)
    public static void transientNativeMotionDoesNotStopSourceProgression(GameTestHelper helper) {
        new Scenario(helper, Fault.MOTION).start();
    }

    @GameTest(template = "empty", batch = "escrow_governed_source_position", timeoutTicks = 600)
    public static void movingPlanRestoresTransientPositionWithoutAStickyHold(GameTestHelper helper) {
        new Scenario(helper, Fault.POSITION).start();
    }

    @GameTest(template = "empty", batch = "escrow_governed_rest_hold", timeoutTicks = 600)
    public static void persistentRestPlacementHoldsAndLetsFlightTicketsExpire(GameTestHelper helper) {
        new Scenario(helper, Fault.REST_POSITION).start();
    }

    @GameTest(template = "empty", batch = "escrow_governed_source_duplicate", timeoutTicks = 600)
    public static void duplicateJoinHoldsBothCandidatesWithoutLegacyElection(GameTestHelper helper) {
        new Scenario(helper, Fault.DUPLICATE).start();
    }

    @GameTest(template = "empty", batch = "escrow_governed_source_clock", timeoutTicks = 600)
    public static void restoredAscentAheadOfLevelClockResumesWithoutResourceChanges(GameTestHelper helper) {
        new Scenario(helper, Fault.CLOCK).start();
    }

    @GameTest(template = "empty", batch = "escrow_governed_arrival", timeoutTicks = 600)
    public static void governedDescentLandsWithPassengerAndRetainsArm(GameTestHelper helper) {
        new Scenario(helper, Fault.LANDING).start();
    }

    @GameTest(template = "empty", batch = "escrow_ascent_dismount_complete", timeoutTicks = 600)
    public static void ascentDismountCompletesCheckedHandoffAndCleanup(GameTestHelper helper) {
        new Scenario(helper, Departure.ASCENT).start();
    }
    @GameTest(template = "empty", batch = "escrow_landed_dismount_complete", timeoutTicks = 600)
    public static void landedDismountCompletesCheckedHandoffAndCleanup(GameTestHelper helper) {
        new Scenario(helper, Departure.LANDED).start();
    }
    @GameTest(template = "empty", batch = "escrow_landed_relogin_complete", timeoutTicks = 600)
    public static void logoutDuringAscentAndLoginAfterLandingCompletesHandoffAndCleanup(GameTestHelper helper) {
        new Scenario(helper, Departure.LOGOUT).start();
    }
    // Review N4: the passenger is still riding when PlayerList.remove runs, so vanilla dismounts inside the removal.
    @GameTest(template = "empty", batch = "escrow_mounted_logout_complete", timeoutTicks = 600)
    public static void mountedLogoutDuringAscentAndLoginAfterLandingCompletesHandoffAndCleanup(GameTestHelper helper) {
        new Scenario(helper, Departure.MOUNTED_LOGOUT).start();
    }

    // Review N4: the installed dispatcher's warm COMMITTED descent visits, counted on the arrival and the record.
    @GameTest(template = "empty", batch = "escrow_governed_descent_cost", timeoutTicks = 600)
    public static void warmDispatcherDescentVisitsMakeNoCompleteCaptureOrEnvelopeDigest(GameTestHelper helper) {
        new Scenario(helper, Fault.LANDING, true).start();
    }

    // These single-flight fixtures assert the whole journal's acknowledgement at F3.
    // Keep them in distinct batches so another fixture cannot queue an unrelated write.
    @GameTest(template = "empty", batch = "escrow_governed_terminal_separate_slots", timeoutTicks = 600)
    public static void landedDestinationCheckpointProvesSourceAbsentInBothSlots(GameTestHelper helper) {
        new Scenario(helper, false).start();
    }

    @GameTest(template = "empty", batch = "escrow_governed_terminal_shared_slot", timeoutTicks = 600)
    public static void landedDestinationCheckpointProvesSharedSourceAndDestinationSlot(GameTestHelper helper) {
        new Scenario(helper, true).start();
    }

    @GameTest(template = "empty", batch = "escrow_terminal_dismount_cut", timeoutTicks = 600)
    public static void dismountBetweenF7CaptureAndReplacementRefusesHandoff(GameTestHelper helper) {
        new Scenario(helper, EscrowTerminalLeaseCut.Fault.DISMOUNT).start();
    }
    @GameTest(template = "empty", batch = "escrow_terminal_removal_cut", timeoutTicks = 600)
    public static void removalBetweenF7CaptureAndReplacementRefusesHandoff(GameTestHelper helper) {
        new Scenario(helper, EscrowTerminalLeaseCut.Fault.REMOVAL).start();
    }
    @GameTest(template = "empty", batch = "escrow_terminal_logout_cut", timeoutTicks = 600)
    public static void logoutBetweenF7CaptureAndReplacementRefusesHandoff(GameTestHelper helper) {
        new Scenario(helper, EscrowTerminalLeaseCut.Fault.LOGOUT).start();
    }
    @GameTest(template = "empty", batch = "escrow_terminal_motion_cut", timeoutTicks = 600)
    public static void acceptedUnobservedMotionIntervalRetainsProvedF7DiskAuthority(GameTestHelper helper) {
        new Scenario(helper, EscrowTerminalLeaseCut.Fault.MOTION).start();
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
        private net.minecraft.server.level.ServerPlayer passenger;
        private boolean dismounted;
        private long changedDestinationAt = -1;
        private int step;
        private double arrivalY = Double.POSITIVE_INFINITY;
        private boolean observedDescent;
        private boolean terminalCheckpoint;
        private RocketTransferRecord terminalRecord;
        private RocketEntity terminalArrival;
        private boolean sawF6, sawF7, refueled, cutDone;
        private EscrowTerminalLeaseCut.Fault terminalFault = EscrowTerminalLeaseCut.Fault.NONE;
        private EscrowTerminalLeaseCut leaseCut;
        private long f7Tick;
        private io.github.sunthemoon.advancedrocketrycommunity.station.pad.LandingPadEntry claimedPad, otherPad;
        private final UUID otherTransfer = UUID.randomUUID();
        private Departure departure = Departure.NONE;
        private boolean departed, reconnected;
        private long offlineLandedAt = -1;
        private final java.util.Map<BlockPos, net.minecraft.world.level.block.state.BlockState> terrain = new java.util.LinkedHashMap<>();
        private boolean descentCost;
        private DescentSample descentAfter;
        private int descentWarm, descentMeasured;
        private record DescentSample(long tick, long captures, long digests, long observation, double y) { }

        Scenario(GameTestHelper helper, Fault fault) { this(helper, fault, false); }
        Scenario(GameTestHelper helper, Fault fault, boolean descentCost) {
            this.helper = helper; this.fault = fault; this.descentCost = descentCost; level = helper.getLevel();
            pacer = new GameTestTickPacer(level.getServer());
            position = descentCost ? new BlockPos(604_008, 100, 604_008) : new BlockPos(590_008 + 128 * fault.ordinal(), 100, 590_008);
            destination = position.offset(fault == Fault.CLOCK ? 4 : 32, 0, 0);
        }
        Scenario(GameTestHelper helper, boolean shared) {
            this.helper = helper; fault = Fault.LANDING; level = helper.getLevel();
            pacer = new GameTestTickPacer(level.getServer()); terminalCheckpoint = true;
            position = new BlockPos(shared ? 592_136 : 592_008, 100, 592_008);
            destination = position.offset(shared ? 4 : 32, 0, 0);
        }
        Scenario(GameTestHelper helper, EscrowTerminalLeaseCut.Fault terminalFault) {
            this.helper = helper; fault = Fault.LANDING; this.terminalFault = terminalFault;
            level = helper.getLevel(); pacer = new GameTestTickPacer(level.getServer()); terminalCheckpoint = true;
            position = new BlockPos(596_008 + 128 * terminalFault.ordinal(), 100, 596_008);
            destination = position.offset(32, 0, 0);
        }
        Scenario(GameTestHelper helper, Departure departure) {
            this.helper = helper; this.departure = departure; fault = Fault.LANDING;
            level = helper.getLevel(); pacer = new GameTestTickPacer(level.getServer()); terminalCheckpoint = true;
            position = new BlockPos(602_008 + 128 * departure.ordinal(), 100, 602_008);
            destination = position.offset(32, 0, 0);
        }
        private boolean logout() { return departure == Departure.LOGOUT || departure == Departure.MOUNTED_LOGOUT; }
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
            if (step == 5) { terminal(journal); return; }
            if (step == 4) { phases(journal); return; }
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
            if (fault == Fault.REST_POSITION) { restingHold(journal); return; }
            helper.assertTrue(rocket.getX() == position.getX() + 0.5 && rocket.getZ() == position.getZ() + 0.5
                    && rocket.getY() >= previousY && rocket.getY() <= position.getY() + RocketFlightLimits.FLIGHT_ALTITUDE_BLOCKS,
                    "Source escaped its bounded ascent column");
            previousY = rocket.getY();
            if (flight.state() == RocketFlightState.ASCENT) {
                observedAscent = true;
                if (rocket.getY() > position.getY() && changedAt < 0) {
                    changedAt = level.getGameTime();
                    frozenHeight = rocket.getY();
                    if (departure == Departure.ASCENT || logout()) {
                        if (departure == Departure.MOUNTED_LOGOUT) {
                            helper.assertTrue(passenger.getVehicle() == rocket, "Mounted logout lacks its riding passenger");
                        } else {
                            passenger.stopRiding();
                            helper.assertTrue(passenger.getVehicle() == null, "Ascent departure was prevented");
                        }
                        departed = true;
                        if (logout()) {
                            level.getServer().getPlayerList().remove(passenger);
                            helper.assertTrue(level.getServer().getPlayerList().getPlayer(passenger.getUUID()) == null,
                                    "Logout did not remove the assigned player");
                            helper.assertTrue(passenger.getVehicle() == null && rocket.isAlive() && rocket.isAddedToWorld()
                                    && rocket.getPassengers().isEmpty(), "Logout left its player seated or unloaded the rocket with it");
                        }
                    }
                    else if (fault == Fault.MOTION) { rocket.setDeltaMovement(new Vec3(0.125, 0, 0)); }
                    else if (fault == Fault.POSITION) { rocket.setPos(rocket.getX() + 0.125, rocket.getY() + 3, rocket.getZ() + 0.125); }
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
            if (fault == Fault.DUPLICATE) {
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
                    && data.ledger().armedFlights().contains(record.nativeTransfer().orElseThrow().armed()),
                    "Source minted destination or terminal authority: phase=" + record.phase()
                            + ", acknowledged=" + journal.acknowledgedCurrent() + ", armed="
                            + data.ledger().armedFlights().contains(record.nativeTransfer().orElseThrow().armed()));
            if (fault != Fault.DUPLICATE) { step = 3; return; }
            finish(journal, null);
        }

        private void restingHold(RocketTransferSavedData journal) {
            var server = level.getServer(); var record = journal.find(transfer).orElseThrow();
            if (changedAt < 0) {
                if (!journal.acknowledgedCurrent() || io.github.sunthemoon.advancedrocketrycommunity.rocket.server
                        .RocketTransferFenceGameTests.flightTickets(server, transfer) == 0) { return; }
                helper.assertTrue(EscrowRuntime.of(server).sourceFlights().tick(server, journal, record, rocket, () -> true)
                        == EscrowGovernedSourceFlight.Result.COUNTDOWN, "Rest fixture did not bind a stationary countdown");
                changedAt = server.getTickCount(); rocket.setPos(rocket.getX() + 0.125, rocket.getY(), rocket.getZ()); return;
            }
            helper.assertTrue(rocket.flightData().orElseThrow().state() == RocketFlightState.COUNTDOWN
                    && rocket.getX() == position.getX() + 0.625 && rocket.getY() == position.getY(),
                    "A persistent rest displacement resumed or was silently repaired");
            if (server.getTickCount() - changedAt <= RocketFlightLimits.FLIGHT_TICKET_TIMEOUT_TICKS + 3) { return; }
            helper.assertTrue(EscrowRuntime.of(server).executorHolds().held(record)
                    && io.github.sunthemoon.advancedrocketrycommunity.rocket.server.RocketTransferFenceGameTests.flightTickets(server, transfer) == 0,
                    "An ACTIVE executor hold kept refreshing its flight tickets");
            helper.assertTrue(data.ledger().armedFlights().contains(record.nativeTransfer().orElseThrow().armed())
                    && journal.acknowledgedCurrent(), "Placement hold changed the checked flight authority");
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
            if (fault == Fault.NONE || fault == Fault.LANDING) { step = 4; return; }
            finish(journal, arrival);
        }

        private void phases(RocketTransferSavedData journal) {
            var record = journal.find(transfer).orElseThrow();
            if (!journal.acknowledgedCurrent() || record.phase() != RocketTransferPhase.COMMITTED) { return; }
            var arrival = (RocketEntity) level.getEntity(record.destinationEntityId().orElseThrow());
            helper.assertTrue(arrival != null && rocket.isRemoved() && rocket.getPassengers().isEmpty()
                    && level.getEntity(rocket.getUUID()) == null, "F5 did not remove exactly its emptied source");
            // Sampled before this fixture's own complete captures below, which the window must not count.
            var before = descentCost ? sample(record, arrival) : null;
            helper.assertTrue(initial.fuel().equals(rocket.flightData().orElseThrow().fuel()) && wrapper.equals(rocket.escrowSection())
                    && EscrowGovernedPhaseAction.destinationMatches(record, EscrowNativeEnvelope.rocket(arrival))
                    && arrival.escrowSection().epoch() == 0 && !RocketEscrowGate.idle(arrival)
                    && data.ledger().armedFlights().contains(record.nativeTransfer().orElseThrow().armed()),
                    "F5 changed credit, repeated a debit or released ARMED before F6/F7");
            if (arrival.flightData().orElseThrow().state() == RocketFlightState.DESCENT
                    && arrival.getY() < destination.getY() + RocketFlightLimits.FLIGHT_ALTITUDE_BLOCKS) { observedDescent = true; }
            if (logout() && !reconnected) {
                helper.assertTrue(departed && level.getServer().getPlayerList().getPlayer(passenger.getUUID()) == null
                        && arrival.getPassengers().isEmpty(), "Offline assignment was reconstructed or seated");
                if (arrival.flightData().orElseThrow().state() != RocketFlightState.LANDED) { return; }
                if (offlineLandedAt < 0) { offlineLandedAt = level.getServer().getTickCount(); }
                if (level.getServer().getTickCount() - offlineLandedAt < 20) { return; }
                passenger = connect(passenger.getUUID()); reconnected = true;
                helper.assertTrue(passenger.getVehicle() == null, "Login fixture seated its passenger instead of F5"); return;
            }
            if (departure == Departure.LANDED && !departed
                    && arrival.flightData().orElseThrow().state() == RocketFlightState.LANDED) {
                helper.assertTrue(passenger.getVehicle() == arrival, "Landed departure lacks its checked arriving passenger");
                passenger.stopRiding(); departed = true;
                helper.assertTrue(passenger.getVehicle() == null, "Landed dismount was prevented"); return;
            }
            if (changedDestinationAt >= 0) {
                if (level.getGameTime() - changedDestinationAt < 30) { return; }
                helper.assertTrue(arrival.getDeltaMovement().equals(Vec3.ZERO) && passenger.getVehicle() == arrival,
                        "Transient destination motion prevented the checked F5 remount");
                finish(journal, arrival); return;
            }
            if (passenger.getVehicle() != arrival) { return; }
            if (!dismounted) {
                helper.assertTrue(arrival.getPassengers().equals(List.of(passenger)), "F5 lost its online assignment");
                passenger.stopRiding();
                helper.assertTrue(passenger.getVehicle() == null && !passenger.startRiding(arrival, true),
                        "Physical dismount was cancelled or an external mount bypassed ARMED");
                dismounted = true; return;
            }
            helper.assertTrue(arrival.getPassengers().equals(List.of(passenger)), "Checked F5 reconnect duplicated its passenger");
            if (fault == Fault.LANDING) {
                helper.assertTrue(arrival.getY() <= arrivalY && arrival.getY() >= destination.getY(), "Descent escaped its monotonic column");
                arrivalY = arrival.getY();
                if (arrival.flightData().orElseThrow().state() == RocketFlightState.DESCENT) {
                    if (arrival.getY() < destination.getY() + RocketFlightLimits.FLIGHT_ALTITUDE_BLOCKS) { observedDescent = true; }
                    if (descentCost) { descentCost(before, record, arrival); }
                    return;
                }
                helper.assertTrue(!descentCost || descentMeasured == 20,
                        "The descent cost window covered " + descentMeasured + " of 20 warm dispatcher visits");
                helper.assertTrue(observedDescent && arrival.flightData().orElseThrow().state() == RocketFlightState.LANDED
                        && arrival.getY() == destination.getY() && arrival.flightData().orElseThrow().plan().isEmpty()
                        && arrival.flightData().orElseThrow().activeTransferId().isEmpty()
                        && arrival.flightData().orElseThrow().fuel().equals(record.destinationFlightData().fuel())
                        && arrival.flightData().orElseThrow().passengers().equals(record.destinationFlightData().passengers()),
                        "Governed landing changed the frozen debit or assignments");
                if (terminalCheckpoint) {
                    terminalRecord = record; terminalArrival = arrival;
                    leaseCut = new EscrowTerminalLeaseCut(level.getServer(), data, record);
                    step = 5; return;
                }
                finish(journal, arrival); return;
            }
            passenger.stopRiding(); arrival.setDeltaMovement(new Vec3(0.125, 0, 0)); changedDestinationAt = level.getGameTime();
        }

        private DescentSample sample(RocketTransferRecord record, RocketEntity arrival) {
            return new DescentSample(level.getServer().getTickCount(), arrival.governedCaptures(),
                    record.nativeTransfer().orElseThrow().envelopeDigests(), arrival.governedObservation(), arrival.getY());
        }

        /**
         * Review N4. Between the previous onEachTick and this one, END ran the installed dispatcher's COMMITTED
         * descent visit and the next entity tick ran the flight observers. Once warm, such a visit makes no complete
         * capture of the arrival and computes no native envelope digest for the record. Only visits that moved the
         * arrival down without reaching the landing height count. A chunk save or load changes governedObservation and
         * revalidates once, so it restarts the warm window instead of being counted.
         */
        private void descentCost(DescentSample before, RocketTransferRecord record, RocketEntity arrival) {
            boolean visited = descentAfter != null && before.tick() == descentAfter.tick() + 1
                    && before.observation() == descentAfter.observation()
                    && before.y() < descentAfter.y() && before.y() > destination.getY();
            if (!visited) { descentWarm = 0; }
            else if (++descentWarm > 3 && descentMeasured < 20) {
                helper.assertTrue(before.captures() == descentAfter.captures() && before.digests() == descentAfter.digests(),
                        "A warm dispatcher descent visit made " + (before.captures() - descentAfter.captures())
                                + " complete captures and " + (before.digests() - descentAfter.digests()) + " envelope digests");
                descentMeasured++;
            }
            descentAfter = sample(record, arrival);
        }

        private void terminal(RocketTransferSavedData journal) {
            var record = terminalRecord; var arrival = terminalArrival; var server = level.getServer();
            var slots = EscrowGovernedTerminalDisk.slots(record, EscrowFlightTables.Outcome.HANDED_OFF);
            helper.assertTrue(slots.size() != 1 || slots.get(0).identity().equals(arrival.getUUID())
                    && slots.get(0).beforeEpoch() == 0, "Shared terminal frontier belongs to the disappeared source");
            if (EscrowRuntime.of(server).terminal().checkpoint(server, record)) {
                sawF6 = true;
                helper.assertTrue(!RocketEscrowGate.resourcesIdle(arrival)
                        && EscrowGovernedTerminalPassengers.resolved(server, record, EscrowFlightTables.Outcome.HANDED_OFF, arrival),
                        "Joint F6 checkpoint lost its passenger prerequisite or released resources");
            }
            if (leaseCut != null && leaseCut.ready()) {
                try { leaseCut.exercise(helper, terminalFault, arrival, passenger); }
                finally { leaseCut.close(); leaseCut = null; }
                cutDone = true;
                if (terminalFault != EscrowTerminalLeaseCut.Fault.MOTION && terminalFault != EscrowTerminalLeaseCut.Fault.NONE) {
                    finish(journal, arrival); return;
                }
            }
            var arm = data.ledger().armedFlights().stream().filter(value -> value.transfer().equals(transfer)).findFirst().orElse(null);
            if (arm != null && arm.outcome() == EscrowFlightTables.Outcome.ARMED) {
                helper.assertTrue(!RocketEscrowGate.resourcesIdle(arrival) && passenger.getVehicle() == arrival,
                        "F6 released resources or lost its connected passenger");
                return;
            }
            if (arm != null) {
                helper.assertTrue(arm.outcome() == EscrowFlightTables.Outcome.HANDED_OFF && sawF6, "F7 did not follow its joint checkpoint");
                if (!sawF7) { sawF7 = true; f7Tick = server.getTickCount(); }
                helper.assertTrue(!RocketEscrowGate.idle(arrival), "Terminal entry released launch/destruction before F8");
                if (journal.actionAuthorityAvailable() && journal.find(transfer).isPresent() && !refueled) {
                    helper.assertTrue(RocketEscrowGate.resourcesIdle(arrival)
                            && io.github.sunthemoon.advancedrocketrycommunity.rocket.server.RocketRuntime.resourceMutationAllowed(arrival),
                            "F7 acknowledgement did not release its exact successor's resources");
                    helper.assertTrue(io.github.sunthemoon.advancedrocketrycommunity.rocket.server.RocketTransferFenceGameTests.flightTickets(server, transfer) == slots.size(),
                            "Flight tickets were not retained until checked journal absence");
                    var pads = io.github.sunthemoon.advancedrocketrycommunity.station.pad.LandingPadIndex.get(server);
                    helper.assertTrue(transfer.equals(pads.find(transfer, claimedPad.reference()).orElseThrow().claim()),
                            "Pad claim was released before checked journal absence");
                    verifyStoredSurvivor(record, arrival);
                    // W85 G9: the terminal successor passes resource validation, but its record-3 T is refused
                    // before any release; nothing changes and no pending disassembly starts.
                    var landedSession = io.github.sunthemoon.advancedrocketrycommunity.rocket.server
                            .RocketLandedDisassemblyGameTestSupport.create(helper);
                    var owner = new io.github.sunthemoon.advancedrocketrycommunity.rocket.server
                            .RocketLandedDisassemblyGameTestSupport.Requester((ServerLevel) arrival.level(), arrival.ownerId().orElseThrow());
                    owner.setPos(arrival.position());
                    var governedRecord = journal.find(transfer).orElseThrow();
                    landedSession.requestDisassembly(owner, arrival);
                    helper.assertTrue(owner.token != null && !landedSession.confirmDisassembly(owner, owner.token)
                            && "validation.advancedrocketrycommunity.rocket.region_busy".equals(owner.lastActionBar())
                            && journal.find(transfer).orElse(null) == governedRecord && !landedSession.pending(arrival)
                            && arrival.isAlive() && journal.actionAuthorityAvailable(), "A record-3 arrival reached a landed release");
                    var flight = arrival.flightData().orElseThrow();
                    arrival.updateFlightData(flight.withFuel(flight.fuel().withFluidBank(false, ModIdentity.id("rocket_fuel"), null, 600), level.getGameTime()));
                    helper.assertTrue(arrival.escrowSection().epoch() == 1 && arrival.flightData().orElseThrow().fuel().committedDebits().equals(List.of(transfer)),
                            "Released refuel lost the independent epoch or repeated a debit");
                    refueled = true;
                }
                return;
            }
            helper.assertTrue(sawF6 && sawF7 && refueled && cutDone
                    && journal.actionAuthorityAvailable() && journal.find(transfer).isEmpty()
                    && journal.acknowledgedGuard(record.logicalRocketId()).orElseThrow().transfer() == null,
                    "F8 did not acknowledge journal removal before exact pruning");
            helper.assertTrue(RocketEscrowGate.idle(arrival)
                    && io.github.sunthemoon.advancedrocketrycommunity.rocket.server.RocketRuntime.resourceMutationAllowed(arrival)
                    && arrival.flightData().orElseThrow().fuel().totalAmount() == 600
                    && arrival.getPassengers().equals(List.of(passenger)) && passenger.connection.connection.isConnected()
                    && level.getEntity(rocket.getUUID()) == null, "F8 failed to release its one surviving rocket intact");
            helper.assertTrue(server.getTickCount() - f7Tick < RocketFlightLimits.FLIGHT_TICKET_TIMEOUT_TICKS
                    && io.github.sunthemoon.advancedrocketrycommunity.rocket.server.RocketTransferFenceGameTests.flightTickets(server, transfer) == 0,
                    "F8 relied on ticket expiry instead of explicit release");
            var pads = io.github.sunthemoon.advancedrocketrycommunity.station.pad.LandingPadIndex.get(server);
            helper.assertTrue(pads.find(transfer, claimedPad.reference()).orElseThrow().claim() == null
                    && otherTransfer.equals(pads.find(transfer, otherPad.reference()).orElseThrow().claim()),
                    "F8 did not release only its dependent pad claim");
            retiredPlacement(record, arrival);
            finish(journal, arrival);
        }

        /** Review N3: F8 drops the retired bindings, and moving the idle landed rocket afterwards holds nothing. */
        private void retiredPlacement(RocketTransferRecord record, RocketEntity arrival) {
            var runtime = EscrowRuntime.of(level.getServer());
            helper.assertTrue(!runtime.sourceFlights().bound(record.logicalRocketId()) && !runtime.arrivals().bound(record.transferId()),
                    "F8 kept the retired flight's F3 or arrival binding");
            arrival.setPos(arrival.getX() + 1, arrival.getY(), arrival.getZ());
            runtime.sourceFlights().observe(arrival); runtime.arrivals().observe(arrival); runtime.flightPhases().observe(arrival);
            helper.assertTrue(!runtime.executorHolds().held(record), "A retired flight's placement observation recorded a hold");
        }

        private void verifyStoredSurvivor(RocketTransferRecord record, RocketEntity arrival) {
            var envelope = EscrowNativeEnvelope.rocket(arrival);
            // R13 permits the unsaved motion interval; the F6 disk still contains the proved stationary survivor.
            if (terminalFault == EscrowTerminalLeaseCut.Fault.MOTION) {
                envelope.put("motion", record.nativeTransfer().orElseThrow().destinationEnvelope(record).get("motion").copy());
            }
            var directory = net.minecraft.world.level.dimension.DimensionType.getStorageFolder(level.dimension(),
                    level.getServer().getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT)).resolve("entities");
            try {
                for (var slot : EscrowGovernedTerminalDisk.slots(record, EscrowFlightTables.Outcome.HANDED_OFF)) {
                    helper.assertTrue(EscrowGovernedTerminalDisk.validator(record, EscrowFlightTables.Outcome.HANDED_OFF, slot, envelope)
                            .test(AnvilRecordReader.read(directory, new ChunkPos(slot.x(), slot.z())).root()), "F7 disk lost its proved survivor/source absence");
                }
            } catch (java.io.IOException failure) { throw new java.io.UncheckedIOException(failure); }
        }

        private void finish(RocketTransferSavedData journal, RocketEntity arrival) {
            if (departure != Departure.NONE) {
                helper.assertTrue(departed && sawF6 && sawF7 && journal.find(transfer).isEmpty()
                        && (!logout() || reconnected), "Departure fixture did not finish checked F7/F8");
            }
            if (claimedPad != null) {
                var pads = io.github.sunthemoon.advancedrocketrycommunity.station.pad.LandingPadIndex.get(level.getServer());
                pads.remove(transfer, claimedPad.reference(), claimedPad.physicalId());
                pads.remove(transfer, otherPad.reference(), otherPad.physicalId());
            }
            journal.remove(transfer); RocketJournalGameTestSupport.acknowledgeFixture(helper);
            if (passenger != null) {
                passenger.stopRiding(); level.getServer().getPlayerList().remove(passenger);
            }
            rocket.discard();
            if (arrival != null) { arrival.discard(); }
            if (duplicate != null) { duplicate.discard(); }
            terrain.forEach((pos, state) -> level.setBlock(pos, state, 3));
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

        private net.minecraft.server.level.ServerPlayer connect(UUID id) {
            var player = new net.minecraft.server.level.ServerPlayer(level.getServer(), level,
                    new com.mojang.authlib.GameProfile(id, "F5Passenger")) {
                @Override public boolean isCreative() { return true; }
                @Override public boolean isSpectator() { return false; }
            };
            var connection = new net.minecraft.network.Connection(net.minecraft.network.protocol.PacketFlow.SERVERBOUND);
            new io.netty.channel.embedded.EmbeddedChannel(connection);
            level.getServer().getPlayerList().placeNewPlayer(connection, player);
            player.teleportTo(level, position.getX() + 0.5, position.getY(), position.getZ() + 0.5, 0, 0);
            return player;
        }

        private void prepare() {
            data = ResourceEscrowSavedData.forTests(level.getServer()); data.bind(level.getServer());
            if (departure != Departure.NONE || descentCost) {
                // Remote generated chunks are not covered by the empty GameTest template.
                // Own and restore only the two-block source and destination footprints.
                for (var origin : List.of(position, destination)) {
                    for (int height = 0; height < 2; height++) {
                        var cell = origin.above(height);
                        helper.assertTrue(level.getBlockEntity(cell) == null, "Departure fixture overlaps a block entity");
                        terrain.put(cell, level.getBlockState(cell));
                        level.setBlock(cell, net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(), 3);
                    }
                }
            }
            if (fault == Fault.NONE || fault == Fault.LANDING) {
                passenger = connect(UUID.randomUUID());
            }
            rocket = RocketSettlementIdleGameTests.createRocket(level, position, UUID.randomUUID());
            var base = rocket.flightData().orElseThrow(); long now = level.getGameTime();
            var fuel = base.fuel().withFluidBank(false, ModIdentity.id("rocket_fuel"), null, 1000);
            rocket.updateFlightData(base.withFuel(fuel, now).withPassengers(base.passengers().assign(
                    passenger == null ? UUID.randomUUID() : passenger.getUUID()).orElseThrow()));
            var tag = rocket.saveWithoutId(new CompoundTag());
            wrapper = new RocketEscrowSection(data.ledger().id(), 7, List.of());
            tag.getCompound("RocketEntityData").put("resource_escrow", wrapper.encode());
            rocket.load(tag); helper.assertTrue(level.addFreshEntity(rocket), "Source was not published");
            if (passenger != null) { helper.assertTrue(passenger.startRiding(rocket, true), "Passenger could not board before F1"); }
            checkpoint = rocket.saveWithoutId(new CompoundTag());
            initial = rocket.flightData().orElseThrow(); previousY = rocket.getY();
            if (terminalCheckpoint) {
                // A dependent index claim exercises F8's release-by-T after the plan is gone.
                // This is not a named-pad destination-selection fixture.
                var pads = io.github.sunthemoon.advancedrocketrycommunity.station.pad.LandingPadIndex.get(level.getServer());
                var owner = rocket.ownerId().orElseThrow();
                claimedPad = pads.register(transfer, owner, UUID.randomUUID(), "Completed", new io.github.sunthemoon.advancedrocketrycommunity.station.model.StationPosition(0, 80, 0));
                otherPad = pads.register(transfer, owner, UUID.randomUUID(), "Unrelated", new io.github.sunthemoon.advancedrocketrycommunity.station.model.StationPosition(1, 80, 0));
                helper.assertTrue(pads.claim(transfer, claimedPad.reference(), claimedPad.physicalId(), transfer)
                        && pads.claim(transfer, otherPad.reference(), otherPad.physicalId(), otherTransfer), "Pad dependency fixture could not claim entries");
            }
            var from = rocket.snapshot().orElseThrow();
            var unloaded = from.relocated(UUID.randomUUID(), from.sourceDimension(), new RocketPosition(1_100_008, 100, 1_100_008), now);
            var unloadedChunk = new ChunkPos(unloaded.sourceOrigin().x() >> 4, unloaded.sourceOrigin().z() >> 4);
            helper.assertTrue(level.getChunkSource().getChunkNow(unloadedChunk.x, unloadedChunk.z) == null
                    && !new io.github.sunthemoon.advancedrocketrycommunity.rocket.transfer.RocketLandingPadSelector().availableLoaded(level, unloaded)
                    && level.getChunkSource().getChunkNow(unloadedChunk.x, unloadedChunk.z) == null,
                    "Governed pad check loaded an absent terrain chunk");
            var to = from.relocated(UUID.randomUUID(), from.sourceDimension(), new RocketPosition(destination.getX(), destination.getY(), destination.getZ()), now);
            if (departure != Departure.NONE || descentCost) {
                helper.assertTrue(new io.github.sunthemoon.advancedrocketrycommunity.rocket.transfer.RocketLandingPadSelector()
                        .availableLoaded(level, to), "Departure fixture's landing footprint is not clear");
            }
            var plan = new RocketFlightPlan(3, transfer, RocketFlightPlanner.EARTH.bodyId(), RocketFlightPlanner.MOON.bodyId(),
                    from.sourceDimension(), from.sourceDimension(), 500, now).withPropulsionDebit(new RocketPropulsionDebit(500, 0,
                    fuel.propulsion().orElseThrow().revision(), fuel.resourceSignature(), RocketNativeResources.hash("F3 source fixture")));
            helper.assertTrue(EscrowGovernedPreparation.begin(rocket, initial.withPlan(plan).startCountdown(now), to, record -> true,
                    record -> accepted = true, () -> discarded = true), "F1 preparation was refused");
        }
    }
}
