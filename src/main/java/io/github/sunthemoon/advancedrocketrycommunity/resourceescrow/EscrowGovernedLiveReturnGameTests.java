package io.github.sunthemoon.advancedrocketrycommunity.resourceescrow;

import io.github.sunthemoon.advancedrocketrycommunity.ModIdentity;
import io.github.sunthemoon.advancedrocketrycommunity.gametest.GameTestTickPacer;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.entity.RocketEntity;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.*;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.persistence.RocketJournalGameTestSupport;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.persistence.RocketTransferSavedData;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.model.RocketNativeResources;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.model.RocketPosition;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.server.RocketRuntime;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.TicketType;
import net.minecraft.util.Unit;
import net.minecraft.world.level.ChunkPos;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/** Installed return dispatch through CANCELLED F7/F8 with native proofs and a connected passenger. */
@GameTestHolder(ModIdentity.MOD_ID)
@PrefixGameTestTemplate(false)
public final class EscrowGovernedLiveReturnGameTests {
    private enum Fault { CANCEL, PAD, SPAWN }
    private EscrowGovernedLiveReturnGameTests() { }

    // The checked journal is global; these single-flight acknowledgement fixtures require isolated batches.
    @GameTest(template = "empty", batch = "escrow_live_cancel_separate", timeoutTicks = 600)
    public static void countdownCancellationReachesReturnedF6WithPassenger(GameTestHelper helper) {
        new Scenario(helper, false).start();
    }

    @GameTest(template = "empty", batch = "escrow_cancelled_unmounted_complete", timeoutTicks = 600)
    public static void dismountAfterReturnCompletesCancelledF7AndF8WithoutReseating(GameTestHelper helper) {
        new Scenario(helper, false, Fault.CANCEL, false, false, true).start();
    }

    @GameTest(template = "empty", batch = "escrow_live_cancel_shared", timeoutTicks = 600)
    public static void countdownCancellationProvesSharedSlotWithPassenger(GameTestHelper helper) {
        new Scenario(helper, true).start();
    }

    @GameTest(template = "empty", batch = "escrow_live_blocked_return_separate", timeoutTicks = 600)
    public static void blockedLandingReturnsToF6WithPassenger(GameTestHelper helper) {
        new Scenario(helper, false, Fault.PAD).start();
    }

    @GameTest(template = "empty", batch = "escrow_live_blocked_return_shared", timeoutTicks = 600)
    public static void blockedLandingReturnsThroughSharedSlotWithPassenger(GameTestHelper helper) {
        new Scenario(helper, true, Fault.PAD).start();
    }

    @GameTest(template = "empty", batch = "escrow_live_spawn_return_separate", timeoutTicks = 600)
    public static void rejectedDestinationSpawnReturnsToF6WithPassenger(GameTestHelper helper) {
        new Scenario(helper, false, Fault.SPAWN).start();
    }

    @GameTest(template = "empty", batch = "escrow_live_spawn_return_shared", timeoutTicks = 600)
    public static void rejectedDestinationSpawnReturnsThroughSharedSlotWithPassenger(GameTestHelper helper) {
        new Scenario(helper, true, Fault.SPAWN).start();
    }

    @GameTest(template = "empty", batch = "escrow_cancelled_dismount_cut", timeoutTicks = 600)
    public static void dismountBetweenCancelledCheckpointAndReplacementRefusesWrite(GameTestHelper helper) {
        new Scenario(helper, false, Fault.CANCEL, true).start();
    }

    @GameTest(template = "empty", batch = "escrow_terminal_retry_replacement", timeoutTicks = 1000)
    public static void terminalProofFailuresSurviveReplacementOfNativeCapture(GameTestHelper helper) {
        new Scenario(helper, false, Fault.CANCEL, false, true).start();
    }

    // N1 / R17 item 5: motion applied after the entity tick is still visible to the proof callback in END.
    @GameTest(template = "empty", batch = "escrow_return_proof_motion", timeoutTicks = 600)
    public static void motionDuringReturnProofsIsTransientAndCompletesCancelledF7AndF8(GameTestHelper helper) {
        new Scenario(helper, false, Fault.CANCEL, false, false, false, true).start();
    }

    private static final class Scenario {
        private final GameTestHelper helper;
        private final ServerLevel level;
        private final GameTestTickPacer pacer;
        private final BlockPos position;
        private final BlockPos destination;
        private final Fault fault;
        private final UUID transfer = UUID.randomUUID();
        private final UUID previousDebit = UUID.randomUUID();
        private ResourceEscrowSavedData data;
        private RocketEntity rocket;
        private ServerPlayer passenger;
        private RocketFlightData initial;
        private RocketEscrowSection wrapper;
        private RocketTransferRecord record;
        private boolean accepted;
        private boolean discarded;
        private EscrowGameTestLaunch launch;
        private boolean requested;
        private boolean done;
        private final boolean dismountCut;
        private final boolean departureAfterReturn;
        private boolean departed;
        private EscrowTerminalLeaseCut leaseCut;
        private final EscrowTerminalRetryCut retryCut;
        private boolean sawF6, sawF7;
        private long f7Tick;
        private List<EscrowFlightTables.Armed> otherArms;
        private io.github.sunthemoon.advancedrocketrycommunity.station.pad.LandingPadEntry claimedPad, otherPad;
        private final UUID otherTransfer = UUID.randomUUID();
        private boolean faultInstalled;
        private boolean observedAscent;
        private boolean observedTransit;
        private int vetoes;
        private java.util.function.Consumer<net.minecraftforge.event.entity.EntityJoinLevelEvent> veto;
        private final java.util.Map<BlockPos, net.minecraft.world.level.block.state.BlockState> terrain = new java.util.LinkedHashMap<>();
        private final boolean proofMotion;
        private final int[] impulses = new int[2];
        private final boolean[] proofMotionDone = new boolean[2];

        Scenario(GameTestHelper helper, boolean shared) {
            this(helper, shared, Fault.CANCEL);
        }
        Scenario(GameTestHelper helper, boolean shared, Fault fault) {
            this(helper, shared, fault, false);
        }
        Scenario(GameTestHelper helper, boolean shared, Fault fault, boolean dismountCut) {
            this(helper, shared, fault, dismountCut, false);
        }
        Scenario(GameTestHelper helper, boolean shared, Fault fault, boolean dismountCut, boolean retryCut) {
            this(helper, shared, fault, dismountCut, retryCut, false);
        }
        Scenario(GameTestHelper helper, boolean shared, Fault fault, boolean dismountCut, boolean retryCut, boolean departureAfterReturn) {
            this(helper, shared, fault, dismountCut, retryCut, departureAfterReturn, false);
        }
        Scenario(GameTestHelper helper, boolean shared, Fault fault, boolean dismountCut, boolean retryCut, boolean departureAfterReturn,
                 boolean proofMotion) {
            this.helper = helper; level = helper.getLevel(); pacer = new GameTestTickPacer(level.getServer());
            this.fault = fault; this.dismountCut = dismountCut;
            this.departureAfterReturn = departureAfterReturn; this.proofMotion = proofMotion;
            this.retryCut = retryCut ? new EscrowTerminalRetryCut() : null;
            position = new BlockPos((shared ? 594_136 : 594_008) + 512 * fault.ordinal()
                    + (dismountCut ? 2_048 : 0) + (retryCut ? 4_096 : 0) + (departureAfterReturn ? 8_192 : 0)
                    + (proofMotion ? 16_384 : 0), 100, 594_008);
            destination = position.offset(shared ? 4 : 32, 0, 0);
        }

        void start() {
            for (var chunk : chunks()) {
                level.getChunkSource().addRegionTicket(TicketType.START, chunk, 2, Unit.INSTANCE); level.getChunk(chunk.x, chunk.z);
            }
            helper.onEachTick(this::tick);
        }

        private List<ChunkPos> chunks() { return List.of(new ChunkPos(position), new ChunkPos(destination)).stream().distinct().toList(); }

        private void tick() {
            if (done) { return; }
            pacer.pace();
            if (List.of(position, destination).stream().anyMatch(pos -> !level.areEntitiesLoaded(new ChunkPos(pos).toLong())
                    || !level.isPositionEntityTicking(pos))) { return; }
            var server = level.getServer(); var journal = RocketTransferSavedData.get(server);
            if (rocket == null) { if (journal.retryChecked(server)) { prepare(); } return; }
            helper.assertTrue(!discarded, "Live return launch preparation was discarded");
            launch.tick();
            if (!accepted) { return; }
            if (requested) {
                terminal(journal);
                // After this tick's assertions, so the impulse is seen only by END and zeroed by the next entity tick.
                if (proofMotion && !done) { proofMotion(); }
                return;
            }
            var returns = EscrowRuntime.of(server).returns(); var flight = rocket.flightData().orElseThrow();
            helper.assertTrue(flight.fuel().equals(initial.fuel()) && flight.passengers().equals(initial.passengers())
                    && flight.fuel().committedDebits().equals(List.of(previousDebit))
                    && wrapper.equals(rocket.escrowSection()) && passenger.getVehicle() == rocket
                    && passenger.connection.connection.isConnected() && !RocketEscrowGate.idle(rocket),
                    "Return changed fuel, debits, assignments, passenger or ARMED lock");
            if (!requested) {
                record = journal.find(transfer).orElseThrow();
                if (fault != Fault.CANCEL) {
                    if (!faultInstalled) { installFault(); faultInstalled = true; }
                    observedAscent |= flight.state() == RocketFlightState.ASCENT;
                    observedTransit |= flight.state() == RocketFlightState.TRANSIT;
                    if (!returns.handles(record, rocket)) { return; }
                    helper.assertTrue(observedAscent && observedTransit && !returns.checkpoint(server, record),
                            "Failed landing skipped live source phases or its native return wait");
                    helper.assertTrue(record.phase() == (fault == Fault.SPAWN ? RocketTransferPhase.DESTINATION_SPAWNED : RocketTransferPhase.PREPARED)
                            && (fault != Fault.SPAWN || vetoes == 1), "Return was not caused by the intended live failure");
                    requested = true; leaseCut = new EscrowTerminalLeaseCut(helper, data, record, EscrowFlightTables.Outcome.CANCELLED); return;
                }
                helper.assertTrue(flight.state() == RocketFlightState.COUNTDOWN && journal.acknowledgedCurrent(),
                        "Cancellation did not start from a live checked countdown");
                var before = EscrowNativeEnvelope.rocket(rocket);
                RocketRuntime.requestFlightIntent(passenger, rocket.getId(), RocketFlightAction.CANCEL,
                        flight.plan().orElseThrow().destinationTarget(), UUID.randomUUID());
                helper.assertTrue(returns.handles(record, rocket) && !returns.checkpoint(server, record)
                        && before.equals(EscrowNativeEnvelope.rocket(rocket)), "Cancellation bypassed its native proof wait");
                requested = true;
                if (retryCut == null) { leaseCut = new EscrowTerminalLeaseCut(helper, data, record, EscrowFlightTables.Outcome.CANCELLED); }
                return;
            }
        }

        private void terminal(RocketTransferSavedData journal) {
            var server = level.getServer(); var runtime = EscrowRuntime.of(server);
            var flight = rocket.flightData().orElseThrow();
            if (departureAfterReturn && !departed && EscrowGovernedReturnAction.returned(record, EscrowNativeEnvelope.rocket(rocket))) {
                passenger.stopRiding(); departed = true;
                helper.assertTrue(passenger.getVehicle() == null, "Returned passenger could not dismount");
            }
            helper.assertTrue(flight.fuel().committedDebits().equals(List.of(previousDebit))
                    && flight.passengers().equals(initial.passengers()) && (departed ? passenger.getVehicle() == null : passenger.getVehicle() == rocket)
                    && passenger.level() == level
                    && passenger.connection.connection.isConnected()
                    && level.getEntity(record.nativeTransfer().orElseThrow().armed().destination()) == null,
                    "Return lost its connected passenger, repeated a debit or published a destination");
            if (retryCut != null && retryCut.tick(helper, server, record, rocket)) {
                helper.assertTrue(data.ledger().armedFlights().contains(record.nativeTransfer().orElseThrow().armed())
                        && !RocketEscrowGate.resourcesIdle(rocket) && journal.find(transfer).filter(record::equals).isPresent(),
                        "F6 retry inferred F7 or released its resource/journal hold");
                finish(journal); return;
            }
            if (!sawF7) {
                helper.assertTrue(flight.fuel().equals(initial.fuel()) && wrapper.equals(rocket.escrowSection())
                        && !RocketEscrowGate.resourcesIdle(rocket) && journal.find(transfer).filter(record::equals).isPresent(),
                        "Return released resources or changed source credit before F7");
                helper.assertTrue(flight.state() == (fault == Fault.CANCEL ? RocketFlightState.COUNTDOWN : RocketFlightState.TRANSIT)
                        || EscrowGovernedReturnAction.returned(record,
                        EscrowNativeEnvelope.rocket(rocket)), "A pending cancellation advanced the source");
            }
            var slots = EscrowGovernedTerminalDisk.slots(record, EscrowFlightTables.Outcome.CANCELLED);
            helper.assertTrue(slots.size() != 1 || slots.get(0).identity().equals(rocket.getUUID())
                    && slots.get(0).beforeEpoch() == wrapper.epoch(), "Shared CANCELLED frontier lost the source epoch");
            if (runtime.terminal().checkpoint(server, record)) {
                sawF6 = true;
                helper.assertTrue(!runtime.returns().checkpoint(server, record)
                        && EscrowGovernedReturnAction.returned(record, EscrowNativeEnvelope.rocket(rocket))
                        && EscrowGovernedTerminalPassengers.resolved(server, record, EscrowFlightTables.Outcome.CANCELLED, rocket)
                        && rocket.getX() == position.getX() + 0.5 && rocket.getY() == position.getY()
                        && rocket.getZ() == position.getZ() + 0.5 && flight.plan().isEmpty() && flight.activeTransferId().isEmpty(),
                        "CANCELLED F6 lacks its exact survivor or overlaps the return proof set");
            }
            if (leaseCut != null && leaseCut.ready()) {
                helper.assertTrue(sawF6, "F7 was captured without the joint CANCELLED checkpoint");
                var writeFailure = dismountCut ? EscrowTerminalRetryCut.observeWriteFailure(server, record) : null;
                try { leaseCut.exercise(helper, dismountCut ? EscrowTerminalLeaseCut.Fault.DISMOUNT : EscrowTerminalLeaseCut.Fault.NONE, rocket, passenger); }
                finally {
                    try { if (writeFailure != null) { writeFailure.close(); } }
                    finally { leaseCut.close(); leaseCut = null; }
                }
                if (dismountCut) {
                    EscrowTerminalRetryCut.assertWriteFailure(helper, server, record, writeFailure);
                    helper.assertTrue(!runtime.terminal().checkpoint(server, record) && !runtime.returns().checkpoint(server, record),
                            "Departure retained a terminal checkpoint");
                    journal.setDirty();
                    helper.assertTrue(!runtime.terminal().checkpoint(server, record) && !RocketEscrowGate.resourcesIdle(rocket),
                            "Unacknowledged journal retained terminal authority");
                    finish(journal); return;
                }
                sawF7 = true; f7Tick = server.getTickCount();
                helper.assertTrue(RocketEscrowGate.resourcesIdle(rocket) && !RocketEscrowGate.idle(rocket)
                        && RocketRuntime.resourceMutationAllowed(rocket) && wrapper.equals(rocket.escrowSection()),
                        "F7 did not release only the unchanged source's resource lock");
                helper.assertTrue(io.github.sunthemoon.advancedrocketrycommunity.rocket.server.RocketTransferFenceGameTests.flightTickets(server, transfer) == slots.size(),
                        "CANCELLED tickets were not retained through F7");
                var pads = io.github.sunthemoon.advancedrocketrycommunity.station.pad.LandingPadIndex.get(server);
                helper.assertTrue(transfer.equals(pads.find(transfer, claimedPad.reference()).orElseThrow().claim()),
                        "CANCELLED pad claim was released before checked journal absence");
                verifyStoredSurvivor(slots);
                rocket.updateFlightData(flight.withFuel(flight.fuel().withFluidBank(false, ModIdentity.id("rocket_fuel"), null, 1_100), level.getGameTime()));
                helper.assertTrue(rocket.escrowSection().epoch() == wrapper.epoch() + 1
                        && rocket.flightData().orElseThrow().fuel().committedDebits().equals(List.of(previousDebit)),
                        "Released CANCELLED refuel reset its epoch or debit history");
            }
            var arm = data.ledger().armedFlights().stream().filter(value -> value.transfer().equals(transfer)).findFirst().orElse(null);
            if (arm != null) {
                helper.assertTrue(arm.outcome() == (sawF7 ? EscrowFlightTables.Outcome.CANCELLED : EscrowFlightTables.Outcome.ARMED)
                        && !RocketEscrowGate.idle(rocket), "Return fabricated completion or released terminal capacity early");
                return;
            }
            helper.assertTrue(sawF6 && sawF7 && journal.actionAuthorityAvailable() && journal.find(transfer).isEmpty()
                    && journal.acknowledgedGuard(record.logicalRocketId()).orElseThrow().transfer() == null
                    && data.ledger().armedFlights().equals(otherArms), "F8 did not prune only its exact CANCELLED entry after checked absence");
            helper.assertTrue(!proofMotion || proofMotionDone[0] && proofMotionDone[1],
                    "Motion was not applied during both return proof windows: absence=" + impulses[0] + ", F6=" + impulses[1]);
            helper.assertTrue(RocketEscrowGate.idle(rocket) && RocketRuntime.resourceMutationAllowed(rocket)
                    && rocket.flightData().orElseThrow().fuel().totalAmount() == 1_100 && rocket.isAlive()
                    && rocket.getPassengers().equals(departureAfterReturn ? List.of() : List.of(passenger))
                    && (!departureAfterReturn || departed && passenger.getVehicle() == null), "F8 lost the available returned rocket or refuel");
            helper.assertTrue(server.getTickCount() - f7Tick < RocketFlightLimits.FLIGHT_TICKET_TIMEOUT_TICKS
                    && io.github.sunthemoon.advancedrocketrycommunity.rocket.server.RocketTransferFenceGameTests.flightTickets(server, transfer) == 0,
                    "CANCELLED F8 relied on ticket expiry instead of explicit release");
            var pads = io.github.sunthemoon.advancedrocketrycommunity.station.pad.LandingPadIndex.get(server);
            helper.assertTrue(pads.find(transfer, claimedPad.reference()).orElseThrow().claim() == null
                    && otherTransfer.equals(pads.find(transfer, otherPad.reference()).orElseThrow().claim()), "F8 released the wrong pad claim");
            if (fault == Fault.SPAWN) { helper.assertTrue(vetoes == 1, "Failed publication was retried instead of returned"); }
            // Review N3: F8 drops the retired bindings, and moving the idle survivor afterwards holds nothing.
            helper.assertTrue(!runtime.sourceFlights().bound(record.logicalRocketId()) && !runtime.arrivals().bound(transfer),
                    "F8 kept the retired flight's F3 or arrival binding");
            rocket.setPos(rocket.getX() + 1, rocket.getY(), rocket.getZ());
            runtime.sourceFlights().observe(rocket); runtime.arrivals().observe(rocket); runtime.flightPhases().observe(rocket);
            helper.assertTrue(!runtime.executorHolds().held(record), "A retired flight's placement observation recorded a hold");
            finish(journal);
        }

        /**
         * Sets motion on every tick of the first absence and the first F6 proof window, from onEachTick after the
         * entity tick. The callback that ends the window therefore observes it. It must answer "not now": no sticky
         * flag, no proof kept from that slot, and no executor hold. The rocket itself never moves.
         */
        private void proofMotion() {
            var work = returnWork();
            if (work == null || work.terminal) { return; }
            int window = work.returned ? 1 : 0;
            if (proofMotionDone[window]) { return; }
            if (work.pending) {
                if (impulses[window] == 0) { windowStart = rocket.position(); }
                rocket.setDeltaMovement(new net.minecraft.world.phys.Vec3(0.125, 0, 0)); impulses[window]++; return;
            }
            if (impulses[window] == 0) { return; }
            proofMotionDone[window] = true;
            helper.assertTrue(work.source.unchanged.get() && work.proofs.isEmpty()
                    && !EscrowRuntime.of(level.getServer()).executorHolds().held(record),
                    "Transient motion during a return proof set a sticky flag, kept its proof or held the flight");
            helper.assertTrue(rocket.position().equals(windowStart) && rocket.getDeltaMovement().equals(net.minecraft.world.phys.Vec3.ZERO),
                    "The return proof motion fixture moved the rocket or its tick kept the impulse");
        }
        private net.minecraft.world.phys.Vec3 windowStart;

        private EscrowGovernedReturn.Work returnWork() {
            try {
                var field = EscrowGovernedReturn.class.getDeclaredField("work"); field.setAccessible(true);
                return (EscrowGovernedReturn.Work) ((java.util.Map<?, ?>) field.get(EscrowRuntime.of(level.getServer()).returns())).get(transfer);
            } catch (ReflectiveOperationException failure) { throw new IllegalStateException(failure); }
        }

        private void verifyStoredSurvivor(List<EscrowEndpoint> slots) {
            var directory = net.minecraft.world.level.dimension.DimensionType.getStorageFolder(level.dimension(),
                    level.getServer().getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT)).resolve("entities");
            try {
                for (var slot : slots) {
                    helper.assertTrue(EscrowGovernedTerminalDisk.validator(record, EscrowFlightTables.Outcome.CANCELLED, slot,
                            EscrowNativeEnvelope.rocket(rocket)).test(AnvilRecordReader.read(directory, new ChunkPos(slot.x(), slot.z())).root()),
                            "F7 disk lost the unchanged source or proved destination absence");
                }
            } catch (java.io.IOException failure) { throw new java.io.UncheckedIOException(failure); }
        }

        private void finish(RocketTransferSavedData journal) {
            var server = level.getServer();
            passenger.stopRiding();
            journal.remove(transfer); RocketJournalGameTestSupport.acknowledgeFixture(helper);
            server.getPlayerList().remove(passenger); rocket.discard();
            if (veto != null) { net.minecraftforge.common.MinecraftForge.EVENT_BUS.unregister(veto); }
            terrain.forEach((pos, state) -> level.setBlock(pos, state, 3));
            for (var chunk : chunks()) { level.getChunkSource().removeRegionTicket(TicketType.START, chunk, 2, Unit.INSTANCE); }
            done = true; helper.succeed();
        }

        private void installFault() {
            if (fault == Fault.PAD) {
                helper.assertTrue(level.setBlock(destination, net.minecraft.world.level.block.Blocks.STONE.defaultBlockState(), 3),
                        "Destination obstruction was not installed");
            } else {
                var identity = record.nativeTransfer().orElseThrow().armed().destination();
                veto = event -> {
                    if (event.getEntity().getUUID().equals(identity)) { vetoes++; event.setCanceled(true); }
                };
                net.minecraftforge.common.MinecraftForge.EVENT_BUS.addListener(net.minecraftforge.eventbus.api.EventPriority.LOWEST,
                        false, net.minecraftforge.event.entity.EntityJoinLevelEvent.class, veto);
            }
        }

        private void prepare() {
            var server = level.getServer(); data = ResourceEscrowSavedData.forTests(server); data.bind(server);
            otherArms = List.copyOf(data.ledger().armedFlights());
            // Remote chunks use generated terrain, not the empty GameTest template. Own only these four cells.
            for (var origin : List.of(position, destination)) {
                for (int height = 0; height < 2; height++) {
                    var cell = origin.above(height);
                    helper.assertTrue(level.getBlockEntity(cell) == null, "Return fixture overlaps a block entity");
                    terrain.put(cell, level.getBlockState(cell));
                    level.setBlock(cell, net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(), 3);
                }
            }
            passenger = new ServerPlayer(server, level, new com.mojang.authlib.GameProfile(UUID.randomUUID(), "ReturnPassenger")) {
                @Override public boolean isCreative() { return true; }
                @Override public boolean isSpectator() { return false; }
            };
            var connection = new net.minecraft.network.Connection(net.minecraft.network.protocol.PacketFlow.SERVERBOUND);
            new io.netty.channel.embedded.EmbeddedChannel(connection); server.getPlayerList().placeNewPlayer(connection, passenger);
            passenger.teleportTo(level, position.getX() + 0.5, position.getY(), position.getZ() + 0.5, 0, 0);
            rocket = RocketSettlementIdleGameTests.createRocket(level, position, UUID.randomUUID());
            var base = rocket.flightData().orElseThrow(); long now = level.getGameTime();
            var filled = base.fuel().withFluidBank(false, ModIdentity.id("rocket_fuel"), null, 1000);
            var fuel = RocketFuelState.restorePropulsion(filled.propulsion().orElseThrow(), List.of(previousDebit));
            rocket.updateFlightData(base.withFuel(fuel, now).withPassengers(base.passengers().assign(passenger.getUUID()).orElseThrow()));
            var tag = rocket.saveWithoutId(new CompoundTag()); wrapper = new RocketEscrowSection(data.ledger().id(), 7, List.of());
            tag.getCompound("RocketEntityData").put("resource_escrow", wrapper.encode()); rocket.load(tag);
            helper.assertTrue(level.addFreshEntity(rocket) && passenger.startRiding(rocket, true), "Return source or passenger was not published");
            initial = rocket.flightData().orElseThrow(); var from = rocket.snapshot().orElseThrow();
            // Synthetic dependent claims check exact release, not named-pad destination selection.
            var pads = io.github.sunthemoon.advancedrocketrycommunity.station.pad.LandingPadIndex.get(server);
            var owner = rocket.ownerId().orElseThrow();
            claimedPad = pads.register(transfer, owner, UUID.randomUUID(), "Cancelled", new io.github.sunthemoon.advancedrocketrycommunity.station.model.StationPosition(0, 80, 0));
            otherPad = pads.register(transfer, owner, UUID.randomUUID(), "Unrelated", new io.github.sunthemoon.advancedrocketrycommunity.station.model.StationPosition(1, 80, 0));
            helper.assertTrue(pads.claim(transfer, claimedPad.reference(), claimedPad.physicalId(), transfer)
                    && pads.claim(transfer, otherPad.reference(), otherPad.physicalId(), otherTransfer), "Return pad dependencies were not claimed");
            var to = from.relocated(UUID.randomUUID(), from.sourceDimension(), new RocketPosition(destination.getX(), destination.getY(), destination.getZ()), now);
            helper.assertTrue(new io.github.sunthemoon.advancedrocketrycommunity.rocket.transfer.RocketLandingPadSelector()
                    .availableLoaded(level, to), "Return fixture's landing area is not clear before fault injection");
            var plan = new RocketFlightPlan(3, transfer, RocketFlightPlanner.EARTH.bodyId(), RocketFlightPlanner.MOON.bodyId(),
                    from.sourceDimension(), from.sourceDimension(), 500, now).withPropulsionDebit(new RocketPropulsionDebit(500, 0,
                    fuel.propulsion().orElseThrow().revision(), fuel.resourceSignature(), RocketNativeResources.hash("Live return fixture")));
            launch = new EscrowGameTestLaunch(helper, data.ledger().id(), initial.logicalRocketId(), transfer,
                    (accept, discard) -> EscrowGovernedPreparation.begin(rocket,
                            initial.withPlan(plan).startCountdown(level.getGameTime()), to, candidate -> true, accept, discard),
                    candidate -> accepted = true, () -> discarded = true);
            launch.begin();
        }
    }
}
