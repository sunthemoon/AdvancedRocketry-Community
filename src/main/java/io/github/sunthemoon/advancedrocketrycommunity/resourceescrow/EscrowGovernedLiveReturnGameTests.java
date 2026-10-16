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

    private static final class Scenario {
        private final GameTestHelper helper;
        private final ServerLevel level;
        private final GameTestTickPacer pacer;
        private final BlockPos position;
        private final BlockPos destination;
        private final UUID transfer = UUID.randomUUID();
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

        Scenario(GameTestHelper helper, boolean shared) {
            this.helper = helper; level = helper.getLevel(); pacer = new GameTestTickPacer(level.getServer());
            position = new BlockPos(shared ? 594_136 : 594_008, 100, 594_008);
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
                    && wrapper.equals(rocket.escrowSection()) && passenger.getVehicle() == rocket
                    && passenger.connection.connection.isConnected() && !RocketEscrowGate.idle(rocket),
                    "Return changed fuel, debits, assignments, passenger or ARMED lock");
            if (!requested) {
                record = journal.find(transfer).orElseThrow();
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
                helper.assertTrue(flight.state() == RocketFlightState.COUNTDOWN || EscrowGovernedReturnAction.returned(record,
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
            // Cached success is not authority after departure or a changed checked-journal lease.
            passenger.stopRiding(); helper.assertTrue(!returns.checkpoint(server, record), "Departure retained the terminal checkpoint");
            journal.setDirty(); helper.assertTrue(!returns.checkpoint(server, record), "Unacknowledged journal retained F6 authority");
            journal.remove(transfer); RocketJournalGameTestSupport.acknowledgeFixture(helper);
            server.getPlayerList().remove(passenger); rocket.discard();
            for (var chunk : chunks()) { level.getChunkSource().removeRegionTicket(TicketType.START, chunk, 2, Unit.INSTANCE); }
            done = true; helper.succeed();
        }

        private void prepare() {
            var server = level.getServer(); data = ResourceEscrowSavedData.forTests(server); data.bind(server);
            passenger = new ServerPlayer(server, level, new com.mojang.authlib.GameProfile(UUID.randomUUID(), "ReturnPassenger")) {
                @Override public boolean isCreative() { return true; }
                @Override public boolean isSpectator() { return false; }
            };
            var connection = new net.minecraft.network.Connection(net.minecraft.network.protocol.PacketFlow.SERVERBOUND);
            new io.netty.channel.embedded.EmbeddedChannel(connection); server.getPlayerList().placeNewPlayer(connection, passenger);
            passenger.teleportTo(level, position.getX() + 0.5, position.getY(), position.getZ() + 0.5, 0, 0);
            rocket = RocketSettlementIdleGameTests.createRocket(level, position, UUID.randomUUID());
            var base = rocket.flightData().orElseThrow(); long now = level.getGameTime();
            var fuel = base.fuel().withFluidBank(false, ModIdentity.id("rocket_fuel"), null, 1000);
            rocket.updateFlightData(base.withFuel(fuel, now).withPassengers(base.passengers().assign(passenger.getUUID()).orElseThrow()));
            var tag = rocket.saveWithoutId(new CompoundTag()); wrapper = new RocketEscrowSection(data.ledger().id(), 7, List.of());
            tag.getCompound("RocketEntityData").put("resource_escrow", wrapper.encode()); rocket.load(tag);
            helper.assertTrue(level.addFreshEntity(rocket) && passenger.startRiding(rocket, true), "Return source or passenger was not published");
            initial = rocket.flightData().orElseThrow(); var from = rocket.snapshot().orElseThrow();
            var to = from.relocated(UUID.randomUUID(), from.sourceDimension(), new RocketPosition(destination.getX(), destination.getY(), destination.getZ()), now);
            var plan = new RocketFlightPlan(3, transfer, RocketFlightPlanner.EARTH.bodyId(), RocketFlightPlanner.MOON.bodyId(),
                    from.sourceDimension(), from.sourceDimension(), 500, now).withPropulsionDebit(new RocketPropulsionDebit(500, 0,
                    fuel.propulsion().orElseThrow().revision(), fuel.resourceSignature(), RocketNativeResources.hash("Live return fixture")));
            helper.assertTrue(EscrowGovernedPreparation.begin(rocket, initial.withPlan(plan).startCountdown(now), to,
                    candidate -> true, candidate -> accepted = true, () -> discarded = true), "Live F1 return fixture was refused");
        }
    }
}
