package io.github.sunthemoon.advancedrocketrycommunity.rocket.server;

import static io.github.sunthemoon.advancedrocketrycommunity.rocket.server.BayDeploymentGameTestSupport.*;

import io.github.sunthemoon.advancedrocketrycommunity.ModIdentity;
import io.github.sunthemoon.advancedrocketrycommunity.resourceescrow.BayReceipt;
import io.github.sunthemoon.advancedrocketrycommunity.resourceescrow.BayReceiptTestHooks;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.bay.deployment.BayClaim;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.bay.deployment.BayMember;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketFlightPlanner;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketTransferPhase;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.persistence.RocketTransferSavedData;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.persistence.SatelliteMissionSavedData;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/**
 * W363 (Root decision R451, B17.3): two REGISTERED restart groups in receipt order. The first one's destination Level
 * does not exist, so its R2 step waits with END_UNAVAILABLE for the whole session. The second, healthy group must still
 * resume, consume and retire with READY_SOURCE, while the waiting group, its record and its receipts stay unchanged.
 * Before the fix the waiting group took R2's one slot on every tick and the healthy group never left RESUME.
 */
@GameTestHolder(ModIdentity.MOD_ID)
@PrefixGameTestTemplate(false)
public final class BayResumeFairnessGameTests {
    private static final ResourceLocation ABSENT = ModIdentity.id("w363_absent");
    private BayResumeFairnessGameTests() { }

    @GameTest(template = TEMPLATE, batch = "bay_resume_fairness", timeoutTicks = 1_200)
    public static void aWaitingRestartGroupDoesNotBlockTheRestartGroupBehindIt(GameTestHelper helper) {
        new Case(helper).start();
    }

    /** One rocket's flight, its packages and the receipts a crashed session left. */
    private static final class Rocket {
        final UUID owner = UUID.randomUUID();
        final List<ItemStack> packages = List.of(solarPackage(owner), solarPackage(owner));
        Flight flight;
        List<BayReceipt> receipts;
        BayClaim claim;
        String checksum;
        UUID logical() { return flight.record().logicalRocketId(); }
    }

    private static final class Case {
        final GameTestHelper helper;
        final Rocket waiting = new Rocket(), healthy = new Rocket();
        int stage, waits;
        boolean closed, sawUnavailable;

        Case(GameTestHelper helper) { this.helper = helper; }
        void start() { helper.onEachTick(this::tick); }

        void tick() {
            if (closed) { return; }
            try {
                helper.assertTrue(++waits < 1_150, "The healthy restart group made no bounded progress at stage " + stage + ": "
                        + describe(waiting) + " / " + describe(healthy));
                switch (stage) {
                    case 0 -> begin();
                    case 1 -> awaitReceipts();
                    default -> observe();
                }
            } catch (RuntimeException | Error failure) { close(); throw failure; }
        }

        void begin() {
            var level = helper.getLevel();
            BlockPos first = helper.absolutePos(new BlockPos(3, 2, 3)), second = helper.absolutePos(new BlockPos(3, 2, 12));
            BlockPos target = helper.absolutePos(new BlockPos(14, 2, 12));
            if (!level.isPositionEntityTicking(first) || !level.isPositionEntityTicking(second) || !level.isPositionEntityTicking(target)) { return; }
            helper.assertTrue(RocketTransferEntities.level(level.getServer(), ABSENT) == null, "The absent Level exists");
            // The waiting group's destination end is in a Level that does not exist: END_UNAVAILABLE, with no end.
            prepare(waiting, first, ABSENT, helper.absolutePos(new BlockPos(14, 2, 3)));
            prepare(healthy, second, level.dimension().location(), target);
            stage = 1;
        }

        void prepare(Rocket rocket, BlockPos origin, ResourceLocation dimension, BlockPos target) {
            var server = helper.getLevel().getServer();
            rocket.flight = transit(helper, origin, dimension, position(target), rocket.owner, rocket.packages,
                    RocketEscrowSectionPatch.NEUTRAL, RocketFlightPlanner.EARTH.bodyId(), null);
            RocketTransferSavedData.get(server).put(rocket.flight.record());
            rocket.checksum = rocket.flight.record().checksum();
            var intents = new ArrayList<BayReceipt>();
            long sequence = BayReceiptTestHooks.nextSequence(server);
            for (int i = 0; i < rocket.packages.size(); i++) {
                intents.add(BayRegisteredGameTests.sequenced(intent(server, rocket.flight.record(), rocket.packages.get(i), i), sequence + i));
            }
            var after = BayRegisteredGameTests.consumed(rocket.flight.record(), intents);
            var list = new ArrayList<BayReceipt>();
            for (BayReceipt intent : intents) {
                list.add(BayRegisteredGameTests.registered(intent, after.source().contentHash(), after.destination().contentHash()));
            }
            rocket.receipts = List.copyOf(list);
            rocket.receipts.forEach(receipt -> helper.assertTrue(BayReceiptTestHooks.put(server, receipt, outcome -> { }).queued(),
                    "Receipt refused"));
        }

        void awaitReceipts() {
            var server = helper.getLevel().getServer();
            var session = bays(helper).sessionForTests();
            var all = new ArrayList<>(waiting.receipts);
            all.addAll(healthy.receipts);
            if (!storeIdle(server) || !all.stream().allMatch(receipt -> session.acknowledged(receipt.op()))) { return; }
            // Receipt order is queue order: the waiting group is ahead of the healthy one.
            var begun = bays(helper).restartForTests(all);
            helper.assertTrue(begun.size() == 2 && begun.get(0).logical().equals(waiting.logical())
                    && begun.get(1).logical().equals(healthy.logical()), "The waiting group is not queued ahead of the healthy one");
            waiting.claim = begun.get(0); healthy.claim = begun.get(1);
            helper.assertTrue(waiting.claim.stage() == BayClaim.Stage.RESUME && healthy.claim.stage() == BayClaim.Stage.RESUME,
                    "Both restart groups must start at RESUME");
            stage = 2;
        }

        void observe() {
            var server = helper.getLevel().getServer();
            var journal = RocketTransferSavedData.get(server);
            var session = bays(helper).sessionForTests();
            // The waiting group never goes on, is never held for repair, and nothing of it changes.
            helper.assertTrue(claim(helper, waiting.logical()).orElse(null) == waiting.claim
                    && waiting.claim.stage() == BayClaim.Stage.RESUME && waiting.claim.hold() == BayClaim.Hold.OWNED_WAIT,
                    "The waiting group left its wait: " + describe(waiting));
            var record = journal.find(waiting.flight.record().transferId()).orElseThrow();
            helper.assertTrue(record.phase() == RocketTransferPhase.PREPARED && record.checksum().equals(waiting.checksum)
                    && waiting.receipts.stream().allMatch(receipt -> session.acknowledged(receipt.op())
                            && session.current(receipt.op()).filter(receipt::equals).isPresent()),
                    "The waiting group's record or receipts changed");
            sawUnavailable |= waiting.claim.reason().equals("END_UNAVAILABLE");
            if (claim(helper, healthy.logical()).isPresent()) { return; }
            helper.assertTrue(healthy.claim.stage() == BayClaim.Stage.ENDED
                    && healthy.claim.handoff().orElseThrow() == BayClaim.Handoff.READY_SOURCE
                    && healthy.claim.members().stream().allMatch(member -> member.state() == BayMember.State.RETIRED),
                    "The healthy group did not retire with READY_SOURCE: " + describe(healthy));
            helper.assertTrue(healthy.packages.stream().allMatch(pkg -> session.find(satellite(pkg)).isEmpty()),
                    "The healthy group's receipts were not retired");
            var ops = consumedOps(journal.find(healthy.flight.record().transferId()).orElseThrow().sourceSnapshot());
            helper.assertTrue(ops.size() == 2 && ops.stream().allMatch(java.util.Objects::nonNull), "The healthy group was not consumed");
            helper.assertTrue(sawUnavailable && waiting.claim.reason().equals("END_UNAVAILABLE"),
                    "The first group was not waiting on its absent end: " + describe(waiting));
            close();
            helper.succeed();
        }

        String describe(Rocket rocket) {
            return rocket.claim == null ? "no claim" : rocket.claim.stage() + " " + rocket.claim.hold() + " " + rocket.claim.reason();
        }

        void close() {
            if (closed) { return; }
            closed = true;
            var server = helper.getLevel().getServer();
            var level = helper.getLevel();
            var journal = RocketTransferSavedData.get(server);
            var session = bays(helper).sessionForTests();
            var registry = SatelliteMissionSavedData.get(server);
            for (Rocket rocket : List.of(waiting, healthy)) {
                if (rocket.flight == null) { continue; }
                bays(helper).forgetForTests(rocket.logical());
                var current = journal.find(rocket.flight.record().transferId()).orElse(null);
                if (current != null) {
                    current.destinationEntityId().map(level::getEntity).ifPresent(net.minecraft.world.entity.Entity::discard);
                    journal.remove(current.transferId());
                }
                RocketTransferEntities.releaseFlightTickets(server, rocket.flight.record());
                var source = level.getEntity(rocket.flight.record().sourceEntityId());
                if (source != null) { source.discard(); }
                rocket.flight.source().discard();
                for (var pkg : rocket.packages) {
                    session.find(satellite(pkg)).ifPresent(receipt -> BayReceiptTestHooks.remove(server, receipt.op(), outcome -> { }));
                    registry.satellite(satellite(pkg)).ifPresent(state -> registry.decommission(state.satelliteId(), rocket.owner, true, true));
                }
            }
        }
    }
}
