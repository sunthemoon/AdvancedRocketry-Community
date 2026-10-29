package io.github.sunthemoon.advancedrocketrycommunity.rocket.server;

import static io.github.sunthemoon.advancedrocketrycommunity.rocket.server.BayDeploymentGameTestSupport.*;

import io.github.sunthemoon.advancedrocketrycommunity.ModIdentity;
import io.github.sunthemoon.advancedrocketrycommunity.gametest.GameTestTickPacer;
import io.github.sunthemoon.advancedrocketrycommunity.registry.ModEntities;
import io.github.sunthemoon.advancedrocketrycommunity.resourceescrow.BayReceipt;
import io.github.sunthemoon.advancedrocketrycommunity.resourceescrow.BayReceiptTestHooks;
import io.github.sunthemoon.advancedrocketrycommunity.resourceescrow.BayReceipts;
import io.github.sunthemoon.advancedrocketrycommunity.resourceescrow.EscrowBarrier;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.bay.deployment.BayClaim;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.entity.RocketEntity;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketTransferRecord;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.persistence.RocketTransferSavedData;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.Predicate;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/** R170: real D6 captures and coordinator dispatch must hand the turn on without releasing the conflicted rocket. */
@GameTestHolder(ModIdentity.MOD_ID)
@PrefixGameTestTemplate(false)
public final class BayHeldProofGameTests {
    private BayHeldProofGameTests() { }

    @GameTest(template = TEMPLATE, batch = "bay_held_proof_capture", timeoutTicks = 1_200)
    public static void outsideCopyDuringPartialCaptureHoldsOnlyItsClaim(GameTestHelper helper) { new Case(helper, false).start(); }

    @GameTest(template = TEMPLATE, batch = "bay_held_proof_retire", timeoutTicks = 1_200)
    public static void escrowConflictBeforeRetirementHoldsOnlyItsClaim(GameTestHelper helper) { new Case(helper, true).start(); }

    private static final class Case {
        final GameTestHelper helper;
        final boolean retire;
        final GameTestTickPacer pacer;
        final Flight[] flights = new Flight[2];
        final BayReceipt[] receipts = new BayReceipt[2];
        final BayClaim[] claims = new BayClaim[2];
        BaySourceProof proof;
        Consumer<UUID> originalOffer;
        Predicate<BayClaim> originalStall;
        Throwable callbackFailure;
        RocketEntity copy;
        RocketTransferRecord heldRecord;
        List<EscrowBarrier.Proof> handles = List.of();
        int stage, ticks, pending, settled;
        boolean triggered, closed;

        Case(GameTestHelper helper, boolean retire) {
            this.helper = helper; this.retire = retire;
            pacer = new GameTestTickPacer(helper.getLevel().getServer());
        }

        void start() { helper.onEachTick(this::tick); }

        void tick() {
            if (closed) { return; }
            try {
                if (callbackFailure instanceof RuntimeException failure) { throw failure; }
                if (callbackFailure instanceof Error failure) { throw failure; }
                helper.assertTrue(++ticks < 1_150, "Two-claim proof fixture exceeded its wait at stage " + stage);
                if (stage == 0) { begin(); }
                else if (stage == 1) { restart(); }
                else if (triggered) { observe(); }
            } catch (RuntimeException | Error failure) { close(); throw failure; }
            finally { if (!closed) { pacer.pace(); } }
        }

        void begin() {
            var server = helper.getLevel().getServer();
            var origin = helper.absolutePos(new BlockPos(3, 2, 3));
            var target = helper.absolutePos(new BlockPos(14, 2, 3));
            if (!helper.getLevel().isPositionEntityTicking(origin) || !helper.getLevel().isPositionEntityTicking(target)) { return; }
            for (int i = 0; i < 2; i++) {
                var owner = UUID.randomUUID();
                var pkg = solarPackage(owner);
                flights[i] = transit(helper, origin.offset(0, 0, i * 3), target.offset(0, 0, i * 3), owner,
                        List.of(pkg), RocketEscrowSectionPatch.NEUTRAL);
                RocketTransferSavedData.get(server).put(flights[i].record());
                var intent = intent(server, flights[i].record(), pkg, 0);
                var after = BayRegisteredGameTests.consumed(flights[i].record(), List.of(intent));
                receipts[i] = BayRegisteredGameTests.registered(intent, after.source().contentHash(), after.destination().contentHash());
                helper.assertTrue(BayReceiptTestHooks.put(server, receipts[i], ignored -> {}).queued(), "Receipt refused");
            }
            stage = 1;
        }

        void restart() {
            var coordinator = bays(helper);
            var server = helper.getLevel().getServer();
            if (!storeIdle(server) || !List.of(receipts).stream().allMatch(r -> coordinator.sessionForTests().acknowledged(r.op()))) { return; }
            pending = BayReceiptTestHooks.authority(server).pendingCount();
            proof = RocketSourceRetirementFixtures.field(coordinator, "proof");
            originalOffer = RocketSourceRetirementFixtures.field(proof, "offer");
            originalStall = RocketSourceRetirementFixtures.field(proof, "stall");
            // Let both claims reach D6 before starting captures, then let the first take the turn first. This avoids
            // a capture completing while the second claim is still waiting for its independent T' acknowledgement.
            setProofField("stall", (Predicate<BayClaim>) claim -> originalStall.test(claim) || !triggered
                    && (claims[1] == null || claims[1].stage() != BayClaim.Stage.PROVE
                        || claim == claims[1] && !proof.turnsForTests().holds(claims[0].logical())));
            // Observe the actual capture completion before its normal enqueue, including RETIRE before D7 dispatch.
            // This replaces no effect/guard and registers no GameTest callback from inside a scheduled callback.
            setOffer(logical -> {
                try {
                    if (!closed && !triggered && claims[0] != null && logical.equals(claims[0].logical())) { conflictAtCaptureBoundary(); }
                } catch (RuntimeException | Error failure) { callbackFailure = failure; }
                originalOffer.accept(logical);
            });
            for (int i = 0; i < 2; i++) { claims[i] = coordinator.restartForTests(List.of(receipts[i])).get(0); }
            stage = 2;
        }

        void conflictAtCaptureBoundary() {
            var claim = claims[0];
            if (retire) {
                if (claim.stage() != BayClaim.Stage.RETIRE) { return; }
                Object proved = map("proofs").get(claim.logical());
                helper.assertTrue(proved != null && !set("retiring").contains(claim.logical()), "D7 already queued or D6 not complete");
                BaySlotProofs.Proof captured = RocketSourceRetirementFixtures.field(proved, "proof");
                handles = captured.slots();
                helper.assertTrue(captured.valid(), "Completed D6 proof invalid before conflict");
            } else {
                var attempt = map("capturing").get(claim.logical());
                if (claim.stage() != BayClaim.Stage.PROVE || attempt == null) { return; }
                List<EscrowBarrier.Proof> captured = RocketSourceRetirementFixtures.field(attempt, "proofs");
                if (captured.isEmpty()) { return; }
                handles = List.copyOf(captured);
                helper.assertTrue(handles.size() < BayAbsenceCensus.slots(flights[0].record()).size(), "Not a partial D6 capture");
            }
            helper.assertTrue(proof.turnsForTests().holds(claim.logical()), "Conflicted claim did not hold D6");
            helper.assertTrue(proof.turnsForTests().line().contains(claims[1].logical()), "Second claim was not waiting for D6");
            triggered = true;
            var server = helper.getLevel().getServer();
            var journal = RocketTransferSavedData.get(server);
            heldRecord = journal.find(claim.transfer()).orElseThrow();
            if (retire) { bays(helper).escrowConflict(server, journal, heldRecord); }
            else {
                copy = ModEntities.ROCKET.get().create(helper.getLevel());
                copy.initializeTransferred(heldRecord.sourceSnapshot(), claim.logical(), heldRecord.ownerId(), heldRecord.sourceFlightData());
                copy.setPos(copy.getX() + 32, copy.getY(), copy.getZ());
                helper.assertTrue(helper.getLevel().addFreshEntity(copy), "Outside copy not published");
            }
            helper.assertTrue(claim.releaseRefusal().orElseThrow().equals(reason()), "Conflict was not latched");
        }

        void observe() {
            var server = helper.getLevel().getServer();
            var coordinator = bays(helper);
            var journal = RocketTransferSavedData.get(server);
            var held = claims[0];
            helper.assertTrue(held.hold() == BayClaim.Hold.REPAIR && held.reason().equals(reason())
                    && held.stage() == (retire ? BayClaim.Stage.RETIRE : BayClaim.Stage.PROVE), "Held claim recovered or lost its latch");
            helper.assertTrue(coordinator.sessionForTests().current(receipts[0].op()).orElseThrow().equals(receipts[0])
                    && BayReceipts.holdsRocket(server, held.logical()), "Held receipt, release flag or lock changed");
            helper.assertTrue(journal.find(held.transfer()).orElseThrow().equals(heldRecord)
                    && !flights[0].source().isRemoved() && (copy == null || !copy.isRemoved()), "Held physical/journal authority changed");
            helper.assertTrue(coordinator.claim(server, journal, heldRecord).kind() == RocketTransferBayRecoveryGate.Kind.HELD
                    && RocketTransferEntities.findDestination(server, heldRecord) == null, "Held claim delegated flight");
            // Allow the same tick's normal enqueue to dispatch; no extra work or scheduling budget is granted.
            if (++settled == 1) { return; }
            helper.assertTrue(!proof.turnsForTests().holds(held.logical()) && !proof.turnsForTests().line().contains(held.logical()),
                    "Physical-held claim retained the D6 turn or queue position");
            for (String name : List.of("capturing", "proofs", "watches", "retryAt")) {
                helper.assertTrue(!map(name).containsKey(held.logical()), "Held claim retained " + name);
            }
            helper.assertTrue(handles.stream().noneMatch(EscrowBarrier.Proof::valid), "Held claim retained live proof handles");
            if (coordinator.claimForTests(claims[1].logical()).isPresent()) { return; }
            helper.assertTrue(claims[1].handoff().orElseThrow() == BayClaim.Handoff.READY_SOURCE, "Second claim did not finish D7");
            helper.assertTrue(coordinator.sessionForTests().current(receipts[1].op()).isEmpty()
                    && consumedOps(flights[1].source().snapshot().orElseThrow()).equals(List.of(receipts[1].op())),
                    "Second claim did not consume its package and retire its receipt");
            helper.assertTrue(BayReceiptTestHooks.authority(server).pendingCount() == pending - 1, "Held charge was freed or second charge retained");
            close(); helper.succeed();
        }

        String reason() { return retire ? "ESCROW_AUTHORITY_CONFLICT" : "UNEXPECTED_COPY"; }
        Map<UUID, ?> map(String name) { return RocketSourceRetirementFixtures.field(proof, name); }
        java.util.Set<UUID> set(String name) { return RocketSourceRetirementFixtures.field(proof, name); }

        void setOffer(Consumer<UUID> offer) {
            setProofField("offer", offer);
        }

        void setProofField(String name, Object value) {
            try { var field = BaySourceProof.class.getDeclaredField(name); field.setAccessible(true); field.set(proof, value); }
            catch (ReflectiveOperationException failure) { throw new IllegalStateException(failure); }
        }

        void close() {
            if (closed) { return; } closed = true;
            if (originalOffer != null) { setOffer(originalOffer); }
            if (originalStall != null) { setProofField("stall", originalStall); }
            var server = helper.getLevel().getServer();
            var journal = RocketTransferSavedData.get(server);
            for (int i = 0; i < flights.length; i++) {
                var flight = flights[i];
                if (flight == null) { continue; }
                bays(helper).forgetForTests(flight.record().logicalRocketId());
                journal.find(flight.record().transferId()).ifPresent(record -> {
                    var destination = RocketTransferEntities.findDestination(server, record);
                    if (destination != null) { destination.discard(); }
                    journal.remove(record.transferId());
                });
                flight.source().discard();
                RocketTransferEntities.releaseFlightTickets(server, flight.record());
                if (receipts[i] != null) { BayReceiptTestHooks.remove(server, receipts[i].op(), ignored -> {}); }
            }
            if (copy != null) { copy.discard(); }
        }
    }
}
