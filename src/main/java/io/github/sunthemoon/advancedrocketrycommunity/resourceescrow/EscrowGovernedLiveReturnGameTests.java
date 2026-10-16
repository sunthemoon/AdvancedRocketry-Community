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

/** Installed request/flight dispatch with native return proofs and a connected passenger. No F7/F8 fixture. */
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
        private boolean requested;
        private boolean done;
        private long checkpointAt = -1;
        private boolean faultInstalled;
        private boolean observedAscent;
        private boolean observedTransit;
        private int vetoes;
        private java.util.function.Consumer<net.minecraftforge.event.entity.EntityJoinLevelEvent> veto;
        private final java.util.Map<BlockPos, net.minecraft.world.level.block.state.BlockState> terrain = new java.util.LinkedHashMap<>();

        Scenario(GameTestHelper helper, boolean shared) {
            this(helper, shared, Fault.CANCEL);
        }
        Scenario(GameTestHelper helper, boolean shared, Fault fault) {
            this.helper = helper; level = helper.getLevel(); pacer = new GameTestTickPacer(level.getServer());
            this.fault = fault;
            position = new BlockPos((shared ? 594_136 : 594_008) + 512 * fault.ordinal(), 100, 594_008);
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
            if (!accepted) { return; }
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
                    requested = true; return;
                }
                helper.assertTrue(flight.state() == RocketFlightState.COUNTDOWN && journal.acknowledgedCurrent(),
                        "Cancellation did not start from a live checked countdown");
                var before = EscrowNativeEnvelope.rocket(rocket);
                RocketRuntime.requestFlightIntent(passenger, rocket.getId(), RocketFlightAction.CANCEL,
                        flight.plan().orElseThrow().destinationTarget(), UUID.randomUUID());
                helper.assertTrue(returns.handles(record, rocket) && !returns.checkpoint(server, record)
                        && before.equals(EscrowNativeEnvelope.rocket(rocket)), "Cancellation bypassed its native proof wait");
                requested = true; return;
            }
            helper.assertTrue(journal.find(transfer).filter(record::equals).isPresent()
                    && data.ledger().armedFlights().contains(record.nativeTransfer().orElseThrow().armed())
                    && level.getEntity(record.nativeTransfer().orElseThrow().armed().destination()) == null,
                    "Return fabricated terminal authority, removed its journal or published a destination");
            if (!returns.checkpoint(server, record)) {
                helper.assertTrue(flight.state() == (fault == Fault.CANCEL ? RocketFlightState.COUNTDOWN : RocketFlightState.TRANSIT)
                        || EscrowGovernedReturnAction.returned(record,
                        EscrowNativeEnvelope.rocket(rocket)), "A pending cancellation advanced the source");
                return;
            }
            helper.assertTrue(EscrowGovernedReturnAction.returned(record, EscrowNativeEnvelope.rocket(rocket))
                    && EscrowGovernedTerminalPassengers.resolved(server, record, EscrowFlightTables.Outcome.CANCELLED, rocket)
                    && rocket.getX() == position.getX() + 0.5 && rocket.getY() == position.getY()
                    && rocket.getZ() == position.getZ() + 0.5 && flight.plan().isEmpty() && flight.activeTransferId().isEmpty(),
                    "CANCELLED F6 checkpoint lacks its exact returned source or passenger");
            if (checkpointAt < 0) { checkpointAt = level.getGameTime(); return; }
            if (level.getGameTime() - checkpointAt < 5) { return; }
            if (fault == Fault.SPAWN) { helper.assertTrue(vetoes == 1, "Failed publication was retried instead of returned"); }
            // Cached success is not authority after departure or a changed checked-journal lease.
            passenger.stopRiding(); helper.assertTrue(!returns.checkpoint(server, record), "Departure retained the terminal checkpoint");
            journal.setDirty(); helper.assertTrue(!returns.checkpoint(server, record), "Unacknowledged journal retained F6 authority");
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
            var to = from.relocated(UUID.randomUUID(), from.sourceDimension(), new RocketPosition(destination.getX(), destination.getY(), destination.getZ()), now);
            helper.assertTrue(new io.github.sunthemoon.advancedrocketrycommunity.rocket.transfer.RocketLandingPadSelector()
                    .availableLoaded(level, to), "Return fixture's landing area is not clear before fault injection");
            var plan = new RocketFlightPlan(3, transfer, RocketFlightPlanner.EARTH.bodyId(), RocketFlightPlanner.MOON.bodyId(),
                    from.sourceDimension(), from.sourceDimension(), 500, now).withPropulsionDebit(new RocketPropulsionDebit(500, 0,
                    fuel.propulsion().orElseThrow().revision(), fuel.resourceSignature(), RocketNativeResources.hash("Live return fixture")));
            helper.assertTrue(EscrowGovernedPreparation.begin(rocket, initial.withPlan(plan).startCountdown(now), to,
                    candidate -> true, candidate -> accepted = true, () -> discarded = true), "Live F1 return fixture was refused");
        }
    }
}
