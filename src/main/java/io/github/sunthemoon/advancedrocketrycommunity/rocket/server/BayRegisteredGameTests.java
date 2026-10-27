package io.github.sunthemoon.advancedrocketrycommunity.rocket.server;

import static io.github.sunthemoon.advancedrocketrycommunity.rocket.server.BayDeploymentGameTestSupport.*;

import io.github.sunthemoon.advancedrocketrycommunity.ModIdentity;
import io.github.sunthemoon.advancedrocketrycommunity.registry.ModEntities;
import io.github.sunthemoon.advancedrocketrycommunity.resourceescrow.BayReceipt;
import io.github.sunthemoon.advancedrocketrycommunity.resourceescrow.BayReceiptTestHooks;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.bay.deployment.BayClaim;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.bay.deployment.BayMember;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.cargo.RocketCargoWorkBudget;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.entity.RocketEntity;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketFlightState;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketTransferRecord;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.persistence.RocketTransferConsumption;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.persistence.RocketTransferSavedData;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.model.RocketPosition;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.mission.SatelliteOperationCode;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.persistence.SatelliteMissionSavedData;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/**
 * W170 A1 for the registered half on the real installed services: decommission after registration with X live and X
 * absent; the restart resume (R1 to R3) with the exact source present, absent (the bay-resume constructor with T's source
 * UUID at altitude), and the mixed images {@code (S',T)} and {@code (S,T')}; an INTENT whose X is equal promoting at a
 * restart; and a second recognized copy holding the claim as DUPLICATE_PENDING with no record change. Each ends with
 * READY_SOURCE(T'), the receipts retired and every bay consumed by its own operation.
 */
@GameTestHolder(ModIdentity.MOD_ID)
@PrefixGameTestTemplate(false)
public final class BayRegisteredGameTests {
    static final String REGISTERED = "bay_registered";
    private BayRegisteredGameTests() { }

    private enum Kind { DECOMMISSION, PRESENT, ABSENT, MIXED_SOURCE_CONSUMED, MIXED_JOURNAL_CONSUMED, PROMOTE, DUPLICATE, PROTECTED }

    @GameTest(template = TEMPLATE, batch = REGISTERED, timeoutTicks = 1_200)
    public static void decommissionAfterRegistrationLeavesOneSatelliteLiveOneAbsentAndBothBaysConsumed(GameTestHelper helper) {
        new Case(helper, Kind.DECOMMISSION).start();
    }

    @GameTest(template = TEMPLATE, batch = REGISTERED, timeoutTicks = 1_200)
    public static void aRestartResumesARegisteredGroupWithItsSourcePresent(GameTestHelper helper) {
        new Case(helper, Kind.PRESENT).start();
    }

    @GameTest(template = TEMPLATE, batch = REGISTERED, timeoutTicks = 1_200)
    public static void aRestartRebuildsAnAbsentSourceAtAltitudeWithItsOwnUuid(GameTestHelper helper) {
        new Case(helper, Kind.ABSENT).start();
    }

    @GameTest(template = TEMPLATE, batch = REGISTERED, timeoutTicks = 1_200)
    public static void aRestartWithAConsumedSourceAndAnUnconsumedJournalPublishesOnlyTPrime(GameTestHelper helper) {
        new Case(helper, Kind.MIXED_SOURCE_CONSUMED).start();
    }

    @GameTest(template = TEMPLATE, batch = REGISTERED, timeoutTicks = 1_200)
    public static void aRestartWithAConsumedJournalAndAnUnconsumedSourcePublishesOnlySPrime(GameTestHelper helper) {
        new Case(helper, Kind.MIXED_JOURNAL_CONSUMED).start();
    }

    @GameTest(template = TEMPLATE, batch = REGISTERED, timeoutTicks = 1_200)
    public static void aRestartIntentWhoseSatelliteIsEqualPromotesAndConsumes(GameTestHelper helper) {
        new Case(helper, Kind.PROMOTE).start();
    }

    @GameTest(template = TEMPLATE, batch = REGISTERED, timeoutTicks = 1_200)
    public static void aSecondRecognizedCopyHoldsTheRestartAsDuplicatePendingWithNoRecordChange(GameTestHelper helper) {
        new Case(helper, Kind.DUPLICATE).start();
    }

    /** B6's protected-copy refusal: a second recognized copy with a bound escrow wrapper holds the claim for repair. */
    @GameTest(template = TEMPLATE, batch = REGISTERED, timeoutTicks = 1_200)
    public static void aProtectedSecondCopyHoldsTheRestartForRepairWithNoRecordChange(GameTestHelper helper) {
        new Case(helper, Kind.PROTECTED).start();
    }

    private static final class Case {
        final GameTestHelper helper;
        final Kind kind;
        final UUID owner = UUID.randomUUID();
        final List<ItemStack> packages;
        Flight flight;
        RocketEntity copy;
        List<BayReceipt> receipts = List.of();
        BayClaim claim;
        String checksum;
        int stage, waits, held;
        boolean sawIntent, decommissioned, sawConsumedSource, closed;

        Case(GameTestHelper helper, Kind kind) {
            this.helper = helper; this.kind = kind;
            packages = List.of(solarPackage(owner), solarPackage(owner));
        }
        void start() { helper.onEachTick(this::tick); }

        void tick() {
            if (closed) { return; }
            try {
                helper.assertTrue(++waits < 1_150, "Fixture exceeded its bounded wait at stage " + stage + " " + describe());
                switch (stage) {
                    case 0 -> begin();
                    case 1 -> awaitReceipts();
                    default -> observe();
                }
            } catch (RuntimeException | Error failure) { close(); throw failure; }
        }

        void begin() {
            var server = helper.getLevel().getServer();
            BlockPos origin = helper.absolutePos(new BlockPos(3, 2, 3)), target = helper.absolutePos(new BlockPos(14, 2, 3));
            if (!helper.getLevel().isPositionEntityTicking(origin) || !helper.getLevel().isPositionEntityTicking(target)) { return; }
            if (kind != Kind.PRESENT && kind != Kind.ABSENT) { packages.forEach(pkg -> registerSolar(server, pkg, owner)); }
            flight = transit(helper, origin, target, owner, packages, RocketEscrowSectionPatch.NEUTRAL);
            var journal = RocketTransferSavedData.get(server);
            journal.put(flight.record());
            checksum = flight.record().checksum();
            if (kind == Kind.DECOMMISSION) {
                bays(helper).ascentComplete(server, journal, flight.record(), flight.source());
                claim = claim(helper, logical()).orElseThrow();
                stage = 2;
                return;
            }
            // The receipts a crashed session left: put in the same tick as T, so T is held from now on.
            var intents = new ArrayList<BayReceipt>();
            long sequence = BayReceiptTestHooks.nextSequence(server);
            for (int i = 0; i < packages.size(); i++) { intents.add(sequenced(intent(server, flight.record(), packages.get(i), i), sequence + i)); }
            var after = consumed(flight.record(), intents);
            var list = new ArrayList<BayReceipt>();
            for (BayReceipt intent : intents) {
                list.add(kind == Kind.PROMOTE ? intent : registered(intent, after.source().contentHash(), after.destination().contentHash()));
            }
            receipts = List.copyOf(list);
            receipts.forEach(receipt -> helper.assertTrue(BayReceiptTestHooks.put(server, receipt, outcome -> { }).queued(), "Receipt refused"));
            if (kind == Kind.MIXED_SOURCE_CONSUMED) {
                flight.source().applyBayConsumption(new BaySourceConsumption(flight.source().getUUID(), logical(), owner,
                        flight.source().snapshot().orElseThrow(), after.source()));
            } else if (kind == Kind.MIXED_JOURNAL_CONSUMED) {
                var consumed = flight.record().withConsumedSnapshots(after.source(), after.destination());
                helper.assertTrue(RocketTransferConsumption.apply(journal, flight.record(), consumed, flight.record().sourceSnapshot().contentHash(),
                        flight.record().destinationSnapshot().contentHash(), after.source().contentHash(), after.destination().contentHash())
                        == RocketTransferConsumption.Result.CONSUMED, "Fixture T' refused");
                journal.flush(server);
            } else if (kind == Kind.ABSENT) {
                flight.source().discard();
            } else if (kind == Kind.DUPLICATE || kind == Kind.PROTECTED) {
                copy = ModEntities.ROCKET.get().create(helper.getLevel());
                copy.initializeTransferred(flight.record().sourceSnapshot(), logical(), owner, flight.record().sourceFlightData());
                if (kind == Kind.PROTECTED) { copy = RocketEscrowSectionPatch.BOUND.apply(helper.getLevel(), copy); }
                helper.assertTrue(helper.getLevel().addFreshEntity(copy), "Copy not published");
            }
            stage = 1;
        }

        void awaitReceipts() {
            var server = helper.getLevel().getServer();
            var session = bays(helper).sessionForTests();
            helper.assertTrue(io.github.sunthemoon.advancedrocketrycommunity.resourceescrow.BayReceipts.holdsRocket(server, logical()),
                    "The receipts must hold L before the restart rows");
            if (!storeIdle(server) || !receipts.stream().allMatch(receipt -> session.acknowledged(receipt.op()))) { return; }
            if (kind == Kind.ABSENT && helper.getLevel().getEntity(flight.record().sourceEntityId()) != null) { return; }
            var begun = bays(helper).restartForTests(receipts);
            helper.assertTrue(begun.size() == 1, "One restart claim for L");
            claim = begun.get(0);
            helper.assertTrue(claim.stage() == (kind == Kind.PROMOTE ? BayClaim.Stage.OUTCOMES : BayClaim.Stage.RESUME),
                    "Wrong restart stage " + claim.stage());
            stage = 2;
        }

        void observe() {
            var server = helper.getLevel().getServer();
            var session = bays(helper).sessionForTests();
            var registry = SatelliteMissionSavedData.get(server);
            if (claim(helper, logical()).isPresent()) {
                helper.assertTrue(record().phase() == io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketTransferPhase.PREPARED,
                        "T advanced while held");
                if (claim.stage() == BayClaim.Stage.PROVE || claim.stage() == BayClaim.Stage.RETIRE) { observeConsumedSource(); }
                var first = session.find(satellite(packages.get(0))).orElse(null);
                if (kind == Kind.DECOMMISSION && first != null) {
                    if (first.phase() == BayReceipt.Phase.INTENT) {
                        sawIntent = true;
                        helper.assertTrue(registry.decommission(first.satellite(), owner, true, true).code() == SatelliteOperationCode.DEPLOYMENT_PENDING,
                                "Decommission must be fenced from INTENT");
                    } else if (!decommissioned && first.phase() == BayReceipt.Phase.REGISTERED && session.acknowledged(first.op())) {
                        helper.assertTrue(registry.decommission(first.satellite(), owner, true, true).code() == SatelliteOperationCode.SUCCESS,
                                "An acknowledged REGISTERED receipt must lift the decommission fence");
                        decommissioned = true;
                    }
                }
                if (kind == Kind.PROTECTED) {
                    if (claim.hold() != BayClaim.Hold.REPAIR) { return; }
                    helper.assertTrue(claim.reason().equals("PROTECTED_DUPLICATE") && claim.stage() == BayClaim.Stage.RESUME,
                            "A protected copy must hold the claim for repair: " + describe());
                    helper.assertTrue(record().checksum().equals(checksum) && flight.source().snapshot().orElseThrow().contentHash()
                            .equals(flight.record().sourceSnapshot().contentHash()) && receipts.stream().allMatch(receipt ->
                            session.acknowledged(receipt.op()) && session.current(receipt.op()).orElseThrow().equals(receipt)),
                            "The repair hold changed a record or the source");
                    if (++held >= 20) { close(); helper.succeed(); }
                    return;
                }
                if (kind == Kind.DUPLICATE && copy != null) {
                    if (!claim.reason().equals("DUPLICATE_PENDING")) {
                        helper.assertTrue(held == 0, "The duplicate hold ended while the copy was loaded: " + claim.reason());
                        return;
                    }
                    helper.assertTrue(claim.stage() == BayClaim.Stage.RESUME && claim.hold() == BayClaim.Hold.OWNED_WAIT,
                            "DUPLICATE_PENDING is a runtime wait, not a repair");
                    helper.assertTrue(record().checksum().equals(checksum) && receipts.stream().allMatch(receipt ->
                            session.acknowledged(receipt.op()) && session.current(receipt.op()).orElseThrow().equals(receipt)),
                            "DUPLICATE_PENDING changed a record");
                    if (++held >= 40) { copy.discard(); copy = null; } // W27d3 resolves copies; here the copy leaves
                }
                return;
            }
            helper.assertTrue(claim.stage() == BayClaim.Stage.ENDED && claim.handoff().orElseThrow() == BayClaim.Handoff.READY_SOURCE,
                    "The claim did not end with READY_SOURCE: " + claim.stage() + " " + claim.reason());
            var group = claim.group().orElseThrow();
            helper.assertTrue(group.members().size() == 2 && claim.members().stream().allMatch(member -> member.state() == BayMember.State.RETIRED),
                    "Both members must retire");
            helper.assertTrue(packages.stream().allMatch(pkg -> session.find(satellite(pkg)).isEmpty()), "The receipts were not retired");
            var current = record();
            var ops = consumedOps(current.sourceSnapshot());
            helper.assertTrue(ops.size() == 2 && ops.stream().allMatch(Objects::nonNull) && group.operations().containsAll(ops)
                    && current.sourceSnapshot().contentHash().equals(group.srcAfter())
                    && current.destinationSnapshot().contentHash().equals(group.dstAfter()), "T' must carry the group's consumption");
            UUID sourceUuid = claim.source().orElseThrow();
            helper.assertTrue(sourceUuid.equals(flight.record().sourceEntityId()), "The source keeps T's source UUID");
            // After READY_SOURCE the transfer moves on at once (spawn, or a fail-back), so S' was observed while held.
            helper.assertTrue(sawConsumedSource, "S' at altitude in TRANSIT was never observed while the claim held it");
            if (kind == Kind.DECOMMISSION) {
                helper.assertTrue(sawIntent && decommissioned && registry.satellite(satellite(packages.get(0))).isEmpty()
                        && registry.satellite(satellite(packages.get(1))).isPresent(), "X absent and X live were both consumed");
            }
            if (kind == Kind.PROMOTE) {
                helper.assertTrue(packages.stream().allMatch(pkg -> registry.satellite(satellite(pkg)).isPresent()), "Promotion kept X");
            }
            if (kind == Kind.DUPLICATE) { helper.assertTrue(held >= 40, "The duplicate hold was never observed"); }
            close();
            helper.succeed();
        }

        /** D6 and D7: the exact source holds S', in TRANSIT at altitude; an absent source was rebuilt, not resurrected. */
        void observeConsumedSource() {
            var group = claim.group().orElseThrow();
            var source = claim.source().map(helper.getLevel()::getEntity).orElse(null);
            if (!(source instanceof RocketEntity rocket)) { return; }
            var origin = flight.record().sourceSnapshot().sourceOrigin();
            helper.assertTrue(rocket.snapshot().orElseThrow().contentHash().equals(group.srcAfter())
                    && rocket.flightData().orElseThrow().state() == RocketFlightState.TRANSIT
                    && rocket.getY() == origin.y() + io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketFlightLimits.FLIGHT_ALTITUDE_BLOCKS,
                    "The held source is not S' at altitude in TRANSIT");
            helper.assertTrue(kind != Kind.ABSENT || rocket != flight.source(), "The absent source was rebuilt, not resurrected");
            sawConsumedSource = true;
        }

        UUID logical() { return flight.record().logicalRocketId(); }
        RocketTransferRecord record() {
            return RocketTransferSavedData.get(helper.getLevel().getServer()).find(flight.record().transferId()).orElseThrow();
        }
        String describe() { return claim == null ? "no claim" : claim.stage() + " " + claim.hold() + " " + claim.reason(); }

        void close() {
            if (closed) { return; }
            closed = true;
            var server = helper.getLevel().getServer();
            if (flight == null) { return; }
            bays(helper).forgetForTests(logical());
            var journal = RocketTransferSavedData.get(server);
            var current = journal.find(flight.record().transferId()).orElse(null);
            if (current != null) {
                current.destinationEntityId().map(helper.getLevel()::getEntity).ifPresent(net.minecraft.world.entity.Entity::discard);
                journal.remove(current.transferId());
            }
            RocketTransferEntities.releaseFlightTickets(server, flight.record());
            var level = helper.getLevel();
            var source = level.getEntity(flight.record().sourceEntityId());
            if (source != null) { source.discard(); }
            flight.source().discard();
            if (copy != null) { copy.discard(); }
            var session = bays(helper).sessionForTests();
            var registry = SatelliteMissionSavedData.get(server);
            for (var pkg : packages) {
                session.find(satellite(pkg)).ifPresent(receipt -> BayReceiptTestHooks.remove(server, receipt.op(), outcome -> { }));
                registry.satellite(satellite(pkg)).ifPresent(state -> registry.decommission(state.satelliteId(), owner, true, true));
            }
        }
    }

    /** The group's S' and D' for these receipts, computed as D4 computes them (each bay consumed by its receipt's op). */
    static BayConsumedSnapshots.Result consumed(RocketTransferRecord record, List<BayReceipt> receipts) {
        var map = new LinkedHashMap<RocketPosition, BayReceipt>();
        receipts.forEach(receipt -> map.put(receipt.bay(), receipt));
        var job = BayConsumedSnapshots.prepare(record.sourceSnapshot(), record.destinationSnapshot(), map).orElseThrow();
        var budget = new RocketCargoWorkBudget();
        long tick = 0;
        while (!job.step(budget, tick)) { tick++; }
        return job.result().orElseThrow();
    }

    static BayReceipt sequenced(BayReceipt intent, long seq) {
        return new BayReceipt(intent.op(), seq, intent.rocket(), intent.bay(), intent.satellite(), intent.owner(), intent.definition(),
                intent.kind(), intent.orbit(), intent.packageSig(), intent.transfer(), intent.srcBefore(), intent.srcAfter(),
                intent.dstBefore(), intent.dstAfter(), intent.phase(), intent.reason(), intent.preexisting(), intent.released(),
                intent.registryEpoch(), intent.createdTick(), intent.updatedTick(), intent.attempts());
    }

    static BayReceipt registered(BayReceipt intent, String srcAfter, String dstAfter) {
        return new BayReceipt(intent.op(), intent.seq(), intent.rocket(), intent.bay(), intent.satellite(), intent.owner(),
                intent.definition(), intent.kind(), intent.orbit(), intent.packageSig(), intent.transfer(), intent.srcBefore(), srcAfter,
                intent.dstBefore(), dstAfter, BayReceipt.Phase.REGISTERED, "NONE", false, false, 1, intent.createdTick(),
                intent.updatedTick(), 0);
    }
}
