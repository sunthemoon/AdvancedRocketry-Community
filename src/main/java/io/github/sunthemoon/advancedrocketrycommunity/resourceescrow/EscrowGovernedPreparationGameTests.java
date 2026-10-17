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
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/** Real F1 storage and F2 acknowledgement, without a fixture drain between request and callback. */
@GameTestHolder(ModIdentity.MOD_ID)
@PrefixGameTestTemplate(false)
public final class EscrowGovernedPreparationGameTests {
    private EscrowGovernedPreparationGameTests() { }

    @GameTest(template = "empty", batch = "escrow_governed_preparation", timeoutTicks = 600)
    public static void countdownWaitsForOwnStoreArmAndPreparedAcknowledgement(GameTestHelper helper) {
        new Scenario(helper, false).start();
    }

    @GameTest(template = "empty", batch = "escrow_governed_preparation", timeoutTicks = 600)
    public static void changedPreparationBoundaryCannotArmOrReportSuccess(GameTestHelper helper) {
        new Scenario(helper, true).start();
    }

    private static final class Scenario {
        private final GameTestHelper helper;
        private final ServerLevel level;
        private final GameTestTickPacer pacer;
        private final boolean invalidate;
        private final BlockPos position;
        private final BlockPos destination;
        private final UUID transfer = UUID.randomUUID();
        private RocketEntity rocket;
        private ResourceEscrowSavedData data;
        private CompoundTag before;
        private boolean allowed = true;
        private boolean accepted;
        private boolean discarded;
        private int step;

        Scenario(GameTestHelper helper, boolean invalidate) {
            this.helper = helper; this.invalidate = invalidate; level = helper.getLevel();
            pacer = new GameTestTickPacer(level.getServer());
            position = new BlockPos(invalidate ? 580_136 : 580_008, 100, 580_008);
            destination = position.offset(32, 0, 0);
        }
        void start() {
            for (var pos : List.of(position, destination)) {
                var chunk = new ChunkPos(pos);
                level.getChunkSource().addRegionTicket(TicketType.START, chunk, 2, Unit.INSTANCE);
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
                prepare(journal); step = 1; return;
            }
            if (!accepted && !discarded) {
                helper.assertTrue(before.equals(EscrowNativeEnvelope.rocket(rocket)), "Preparation changed E before F2 acknowledgement");
                helper.assertTrue(!RocketEscrowGate.idle(rocket), "Pending preparation lost its source lock");
                return;
            }
            helper.assertTrue(EscrowRuntime.of(level.getServer()).flightCheckpoint().pendingDestinations().stream()
                    .noneMatch(region -> region.minimum().x() == destination.getX() && region.minimum().z() == destination.getZ()),
                    "Completed or discarded preparation retained its pending destination");
            if (invalidate) {
                helper.assertTrue(discarded && !accepted && before.equals(EscrowNativeEnvelope.rocket(rocket)),
                        "Invalidated preparation changed the source or reported success");
                helper.assertTrue(data.ledger().armedFlights().stream().noneMatch(arm -> arm.transfer().equals(transfer))
                        && journal.find(transfer).isEmpty() && RocketEscrowGate.idle(rocket),
                        "Invalidated F1 minted authority or left an unacknowledged preparation lock");
                var checkpoint = EscrowRuntime.of(level.getServer()).flightCheckpoint();
                helper.assertTrue(!checkpoint.retryReady(rocket.assemblyTransactionId().orElseThrow(), level.getServer().getTickCount()),
                        "Failed F1 can retry before its proof backoff");
            } else {
                helper.assertTrue(accepted && !discarded && journal.acknowledgedCurrent(), "Countdown has no exact F2 acknowledgement");
                var record = journal.find(transfer).orElseThrow();
                helper.assertTrue(record.nativeTransfer().isPresent()
                        && data.ledger().armedFlights().contains(record.nativeTransfer().orElseThrow().armed())
                        && record.nativeTransfer().orElseThrow().sourceEnvelope(record).equals(before), "F2 lost the acknowledged F1 envelope");
                helper.assertTrue(rocket.flightData().orElseThrow().state() == RocketFlightState.COUNTDOWN
                        && rocket.flightData().orElseThrow().fuel().totalAmount() == 1000
                        && rocket.flightData().orElseThrow().fuel().committedDebits().isEmpty()
                        && rocket.escrowSection().epoch() == 7 && !RocketEscrowGate.idle(rocket),
                        "F2 changed the source credit or bypassed its ordinary mutation hold");
                journal.remove(transfer); RocketJournalGameTestSupport.acknowledgeFixture(helper);
            }
            // Only this disposable fixture is removed. No F6/F7 or terminal outcome is inferred.
            rocket.discard();
            for (var pos : List.of(position, destination)) {
                level.getChunkSource().removeRegionTicket(TicketType.START, new ChunkPos(pos), 2, Unit.INSTANCE);
            }
            step = 2; helper.succeed();
        }
        private void prepare(RocketTransferSavedData journal) {
            data = ResourceEscrowSavedData.forTests(level.getServer()); data.bind(level.getServer());
            rocket = RocketSettlementIdleGameTests.createRocket(level, position, UUID.randomUUID());
            var initial = rocket.flightData().orElseThrow(); long now = level.getGameTime();
            var fuel = initial.fuel().withFluidBank(false, ModIdentity.id("rocket_fuel"), null, 1000);
            rocket.updateFlightData(initial.withFuel(fuel, now));
            var tag = rocket.saveWithoutId(new CompoundTag());
            tag.getCompound("RocketEntityData").put("resource_escrow", new RocketEscrowSection(data.ledger().id(), 7, List.of()).encode());
            rocket.load(tag); helper.assertTrue(level.addFreshEntity(rocket), "Source was not published");
            before = EscrowNativeEnvelope.rocket(rocket);
            var from = rocket.snapshot().orElseThrow();
            var to = from.relocated(UUID.randomUUID(), from.sourceDimension(),
                    new RocketPosition(destination.getX(), destination.getY(), destination.getZ()), now);
            var plan = new RocketFlightPlan(3, transfer, RocketFlightPlanner.EARTH.bodyId(), RocketFlightPlanner.MOON.bodyId(),
                    from.sourceDimension(), from.sourceDimension(), 500, now).withPropulsionDebit(new RocketPropulsionDebit(500, 0,
                    fuel.propulsion().orElseThrow().revision(), fuel.resourceSignature(), RocketNativeResources.hash("F1/F2 adapter fixture")));
            var countdown = rocket.flightData().orElseThrow().withPlan(plan).startCountdown(now);
            if (invalidate) {
                helper.assertTrue(!EscrowGovernedPreparation.begin(rocket, countdown, to, record -> false,
                        record -> accepted = true, () -> discarded = true), "Held action boundary started preparation");
                helper.assertTrue(!accepted && !discarded && RocketEscrowGate.idle(rocket), "Refused preparation mutated callback or lock state");
                helper.assertTrue(!EscrowGovernedPreparation.pendingDestinations(level.getServer()).contains(
                        io.github.sunthemoon.advancedrocketrycommunity.rocket.transaction.RocketRegion.fromSnapshot(to)),
                        "Refused preparation retained its destination");
            }
            helper.assertTrue(EscrowGovernedPreparation.begin(rocket, countdown, to, record -> allowed,
                    record -> accepted = true, () -> discarded = true), "Live F1 preparation was refused");
            helper.assertTrue(!accepted && !discarded && before.equals(EscrowNativeEnvelope.rocket(rocket))
                    && journal.find(transfer).isEmpty() && RocketEscrowGate.flightFrozen(rocket), "Request did not wait for F1");
            helper.assertTrue(EscrowGovernedPreparation.pendingDestinations(level.getServer()).contains(
                    io.github.sunthemoon.advancedrocketrycommunity.rocket.transaction.RocketRegion.fromSnapshot(to)),
                    "Accepted F1 did not reserve its destination before returning");
            if (invalidate) { allowed = false; }
        }
    }
}
