package io.github.sunthemoon.advancedrocketrycommunity.rocket.server;

import io.github.sunthemoon.advancedrocketrycommunity.ModIdentity;
import io.github.sunthemoon.advancedrocketrycommunity.resourceescrow.*;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.entity.RocketEntity;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.TicketType;
import net.minecraft.util.Unit;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/** G2: one blocked absence column cannot be omitted, or stop work in another column. */
@GameTestHolder(ModIdentity.MOD_ID)
@PrefixGameTestTemplate(false)
public final class RocketPinnedCensusGameTests {
    private RocketPinnedCensusGameTests() { }

    @GameTest(template = "rocket_test", batch = "ordinary_retirement_pinned_census", timeoutTicks = 400)
    public static void aPinnedDiscardedColumnExpiresTheWholeCensusAndTwoSharedReturnsWait(GameTestHelper helper) {
        var scenario = new Scenario(helper);
        helper.onEachTick(scenario::tick);
    }

    private static final class Scenario {
        final GameTestHelper helper;
        final BlockPos source = new BlockPos(620_008, 100, 620_008);
        final BlockPos blocked = source.offset(32, 0, 0), independent = source.offset(64, 0, 0);
        final List<ChunkPos> tickets;
        final Return primary, first, second, control;
        EscrowTestEndpoints.Fixture bank;
        EscrowBarrier.Result governed;
        EscrowBarrier.Proof partial;
        long captureTick = -1, deadlineTick = -1, retryAt = -1;
        boolean released, done;

        Scenario(GameTestHelper helper) {
            this.helper = helper;
            tickets = List.of(new ChunkPos(source), new ChunkPos(blocked), new ChunkPos(independent));
            for (var slot : tickets) {
                helper.getLevel().getChunkSource().addRegionTicket(TicketType.START, slot, 2, Unit.INSTANCE);
                helper.getLevel().getChunk(slot.x, slot.z);
            }
            primary = new Return(this, source, blocked, true);
            first = new Return(this, blocked.above(24), blocked.above(24).east(2), false);
            second = new Return(this, blocked.above(48), blocked.above(48).east(2), false);
            control = new Return(this, independent, independent.east(2), false);
        }

        void tick() {
            if (done) { return; }
            try {
                primary.tick();
                if (captureTick >= 0) { first.tick(); second.tick(); control.tick(); }
                if (!released && deadlineTick >= 0 && control.finished) {
                    helper.assertTrue(governed != null && governed.confirmed() && governed.proof().valid(),
                            "Governed absence-column protection did not remain current");
                    for (var waiting : List.of(first, second)) {
                        helper.assertTrue(waiting.returned && waiting.recovery().retirements().attempts(waiting.record.logicalRocketId()) == 0,
                                "A co-located ordinary logical rocket captured the pinned column");
                        helper.assertTrue(waiting.journal.clean() && waiting.accepted.get() == 0,
                                "A deferred co-located return retained its reservation or completed");
                    }
                    helper.assertTrue(!first.record.logicalRocketId().equals(second.record.logicalRocketId())
                            && first.source.chunkPosition().equals(second.source.chunkPosition()), "Two distinct shared-slot returns required");
                    governed.proof().release(); released = true;
                }
                if (primary.finished && first.finished && second.finished && control.finished) {
                    helper.assertTrue(released && partial != null && !partial.valid() && deadlineTick >= 0,
                            "No complete partial-census deadline/retry observation");
                    close(); helper.succeed();
                }
            } catch (RuntimeException | Error failure) { close(); throw failure; }
        }

        void close() {
            if (done) { return; } done = true;
            for (var test : List.of(primary, first, second, control)) { test.close(); }
            if (governed != null && governed.proof() != null) { governed.proof().release(); }
            if (bank != null) { bank.bank().discard(); helper.getLevel().setBlockAndUpdate(blocked.above(8), Blocks.AIR.defaultBlockState()); }
            for (var slot : tickets) { helper.getLevel().getChunkSource().removeRegionTicket(TicketType.START, slot, 2, Unit.INSTANCE); }
        }
    }

    private static final class Return extends RocketSourceRetirementGameTests.Case {
        final Scenario scenario;
        final BlockPos from, to;
        final boolean census;
        RocketEntity destination;
        boolean returned, finished;

        Return(Scenario scenario, BlockPos from, BlockPos to, boolean census) {
            super(scenario.helper); this.scenario = scenario; this.from = from; this.to = to; this.census = census;
        }
        boolean ready() {
            origin = from; target = to;
            return level().isPositionEntityTicking(origin) && level().isPositionEntityTicking(target);
        }
        void act() {
            if (census) {
                source = flight.spawnInFlight(helper, level()); destination = flight.spawnDestination(helper, level());
                prepare(false); completion = false;
                scenario.bank = EscrowTestEndpoints.create(level(), scenario.blocked.above(8), UUID.randomUUID());
                var op = scenario.bank.operation(1); scenario.bank.apply(op);
            } else { source = flight.spawnCountdown(helper); prepare(true); }
        }
        boolean before() {
            long now = server().getTickCount(); var runtime = EscrowRuntime.of(server());
            if (census && scenario.captureTick < 0) {
                var endpoint = scenario.bank.operation(1).destination(); var frontier = scenario.bank.bankState();
                if (runtime.barrier().begin(runtime.store(level()), endpoint, frontier,
                        EscrowTestEndpoints.validator(endpoint, frontier), now, result -> scenario.governed = result)) {
                    scenario.captureTick = now;
                }
                return false;
            }
            if (!returned) {
                if (census) {
                    if (!runtime.barrier().availableOrdinary(level().dimension().location().toString(), now)) { return false; }
                    recovery().recoverById(server(), journal, record.transferId());
                    if (!recovery().retirements().pending(record.logicalRocketId())) { return false; }
                    helper.assertTrue(destination.isRemoved(), "Recovery did not discard the destination copy");
                } else {
                    helper.assertTrue(service.failBackToSource(server(), journal, record, source,
                            RocketTransferReturnReason.COUNTDOWN_CANCELLED, completion()), "Cancellation refused");
                }
                returned = true; return false;
            }
            if (!census) { return scenario.released || this == scenario.control; }
            var retirement = recovery().retirements();
            Map<UUID, Object> entries = RocketSourceRetirementFixtures.field(retirement, "pending");
            Object entry = entries.get(record.logicalRocketId());
            if (scenario.deadlineTick < 0) {
                helper.assertTrue(journal.find(record.transferId()).orElse(null) == record, "A partial census removed T");
                Map<?, EscrowBarrier.Proof> proofs = RocketSourceRetirementFixtures.field(entry, "proofs");
                if (!proofs.isEmpty()) { scenario.partial = proofs.values().iterator().next(); }
                if ("DEADLINE".equals(retirement.reason(record.logicalRocketId()))) {
                    helper.assertTrue(scenario.partial != null && !scenario.partial.valid() && proofs.isEmpty()
                            && journal.clean() && retirement.attempts(record.logicalRocketId()) == 1,
                            "Deadline did not release the partial proof and reservation");
                    scenario.deadlineTick = now;
                    scenario.retryAt = RocketSourceRetirementFixtures.field(entry, "notBefore");
                    helper.assertTrue(scenario.retryAt == now + 200, "Deadline changed the existing retry delay");
                }
                return false;
            }
            if (now < scenario.retryAt) {
                helper.assertTrue(retirement.attempts(record.logicalRocketId()) == 1 && journal.clean(), "Retried before the deadline backoff");
                return false;
            }
            return true;
        }

        /** Same disk/conservation boundary as Case.await, without completing the composite test early. */
        void await() {
            if (journal.find(record.transferId()).isPresent()) { return; }
            helper.assertTrue(RocketSourceRetirementFixtures.stationaryImage(
                    RocketSourceRetirementFixtures.diskImage(level(), flight.originSlot(), source.getUUID()), record),
                    "T left RAM before a fresh stationary survivor image");
            helper.assertTrue(RocketSourceRetirementFixtures.diskCopies(level(), flight.originSlot(), record.logicalRocketId()) == 1,
                    "Survivor image is not unique");
            if (census) {
                helper.assertTrue(RocketSourceRetirementFixtures.diskCopies(level(), flight.destinationSlot(), record.logicalRocketId()) == 0,
                        "The discarded-copy column was omitted from the fresh census");
            }
            if (!journal.clean() || completion && accepted.get() == 0) { return; }
            helper.assertTrue(!recovery().retirements().pending(record.logicalRocketId())
                    && !RocketSourceRetirementFixtures.live(service).contains(record.transferId())
                    && !RocketSourceRetirementFixtures.settled(service).contains(record.transferId()), "Removal was not acknowledged");
            helper.assertTrue(!completion || accepted.get() == 1 && discarded.get() == 0, "Completion was not exactly once");
            helper.assertTrue(source.isAlive() && RocketTransferEntities.loadedMatches(server(), record, false).size() == 1
                    && source.flightData().orElseThrow().fuel().amount() == RocketSourceRetirementFixtures.FUEL,
                    "Rocket/fuel conservation failed");
            if (this == scenario.control) { helper.assertTrue(!scenario.released, "Different-slot control did not progress during protection"); }
            finished = true; close();
        }
    }
}
