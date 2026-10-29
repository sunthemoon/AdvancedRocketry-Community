package io.github.sunthemoon.advancedrocketrycommunity.rocket.server;

import static io.github.sunthemoon.advancedrocketrycommunity.rocket.server.BayDeploymentGameTestSupport.*;

import io.github.sunthemoon.advancedrocketrycommunity.ModIdentity;
import io.github.sunthemoon.advancedrocketrycommunity.gametest.GameTestTickPacer;
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
 * restart; and W185's recognized copies consumed and removed at both ends. Each ends with
 * READY_SOURCE(T'), the receipts retired and every bay consumed by its own operation. A protected copy, and an unexpected
 * image of L in the source margin or the destination column (W170-R21), hold the claim for repair instead, with T, the
 * source, the receipts and the copy unchanged. A destination end that is still loading holds R2 at END_LOADING until it
 * loads, with or without an image of L there (W170-R41).
 */
@GameTestHolder(ModIdentity.MOD_ID)
@PrefixGameTestTemplate(false)
public final class BayRegisteredGameTests {
    static final String REGISTERED = "bay_registered";
    /**
     * W170-R41's far-destination restarts, paced, one per batch: R2 runs one group per tick, so a second restart claim in
     * the batch could take the slot until the first one's end had loaded, and that claim would never meet a loading end.
     * Alone, its first checked R2 step comes in the coordinator tick of R1's first tickets, while the end is still loading.
     */
    static final String LOADING = "bay_registered_loading", LOADING_COPY = "bay_registered_loading_copy";
    static final String RECOGNIZED_LOADING = "bay_copies_loading";
    private BayRegisteredGameTests() { }

    private enum Kind {
        DECOMMISSION, PRESENT, ABSENT, MIXED_SOURCE_CONSUMED, MIXED_JOURNAL_CONSUMED, PROMOTE, DUPLICATE, PROTECTED,
        UNEXPECTED_MARGIN, UNEXPECTED_COLUMN, DESTINATION_LOADING, UNEXPECTED_LOADING,
        BOTH_COPIES, ABSENT_WITH_COPIES, RECOGNIZED_COLD_COPY, OUTSIDE_JOIN;
        boolean held() { return this == PROTECTED || this == UNEXPECTED_MARGIN || this == UNEXPECTED_COLUMN || this == UNEXPECTED_LOADING || this == OUTSIDE_JOIN; }
        /** W170-R41: the destination is a far overworld column that only R1's tickets load; no other GameTest reads it. */
        boolean loading() { return this == DESTINATION_LOADING || this == UNEXPECTED_LOADING || this == RECOGNIZED_COLD_COPY; }
        boolean copies() { return this == DUPLICATE || this == BOTH_COPIES || this == ABSENT_WITH_COPIES || this == RECOGNIZED_COLD_COPY; }
        BlockPos far() { return this == DESTINATION_LOADING ? new BlockPos(742_008, 100, 742_008) : new BlockPos(746_008, 100, 746_008); }
    }

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
    public static void aSecondRecognizedCopyIsConsumedAndRemovedBeforeReceiptsRetire(GameTestHelper helper) {
        new Case(helper, Kind.DUPLICATE).start();
    }

    @GameTest(template = TEMPLATE, batch = REGISTERED, timeoutTicks = 1_200)
    public static void copiesAtBothEndsAreConsumedWithTheirOwnHashBeforeRemoval(GameTestHelper helper) {
        new Case(helper, Kind.BOTH_COPIES).start();
    }

    @GameTest(template = TEMPLATE, batch = REGISTERED, timeoutTicks = 1_200)
    public static void copiesNeverReplaceTheMissingExactSourceAndOfflineSeatsSurvive(GameTestHelper helper) {
        new Case(helper, Kind.ABSENT_WITH_COPIES).start();
    }

    @GameTest(template = TEMPLATE, batch = RECOGNIZED_LOADING, timeoutTicks = 1_200)
    public static void aRecognizedColdDestinationCopyIsConsumedOnlyAfterItsEndLoads(GameTestHelper helper) {
        new Case(helper, Kind.RECOGNIZED_COLD_COPY).start();
    }

    @GameTest(template = TEMPLATE, batch = "bay_copies_outside", timeoutTicks = 1_200)
    public static void aKnownCopyJoiningOutsideBothRecordedEndsQuarantinesWithoutRetiring(GameTestHelper helper) {
        new Case(helper, Kind.OUTSIDE_JOIN).start();
    }

    /** B6's protected-copy refusal: a second recognized copy with a bound escrow wrapper holds the claim for repair. */
    @GameTest(template = TEMPLATE, batch = REGISTERED, timeoutTicks = 1_200)
    public static void aProtectedSecondCopyHoldsTheRestartForRepairWithNoRecordChange(GameTestHelper helper) {
        new Case(helper, Kind.PROTECTED).start();
    }

    /** W170-R21: a loaded image of L in the source end's lookup margin whose hash is neither group hash holds for repair. */
    @GameTest(template = TEMPLATE, batch = REGISTERED, timeoutTicks = 1_200)
    public static void anUnexpectedImageInTheSourceMarginHoldsTheRestartForRepairWithNoRecordChange(GameTestHelper helper) {
        new Case(helper, Kind.UNEXPECTED_MARGIN).start();
    }

    /** W170-R21: the same in the destination's column, where D6's source-slot proof would never look. */
    @GameTest(template = TEMPLATE, batch = REGISTERED, timeoutTicks = 1_200)
    public static void anUnexpectedImageInTheDestinationColumnHoldsTheRestartForRepairWithNoRecordChange(GameTestHelper helper) {
        new Case(helper, Kind.UNEXPECTED_COLUMN).start();
    }

    /** W170-R41: R2 waits (END_LOADING) while the destination end is still loading, then the group goes on to READY_SOURCE. */
    @GameTest(template = TEMPLATE, batch = LOADING, timeoutTicks = 1_200)
    public static void aRestartWaitsForADestinationEndStillLoadingAndThenProceeds(GameTestHelper helper) {
        new Case(helper, Kind.DESTINATION_LOADING).start();
    }

    /**
     * W170-R41, from the re-review's HIDDEN-section probe: an image of L added to the destination column before it loads is
     * not yet in the entity index. R2 waits for the end, and once it loads the census holds the restart for repair.
     */
    @GameTest(template = TEMPLATE, batch = LOADING_COPY, timeoutTicks = 1_200)
    public static void anImageOfLAtADestinationEndStillLoadingHoldsTheRestartOnceTheEndLoads(GameTestHelper helper) {
        new Case(helper, Kind.UNEXPECTED_LOADING).start();
    }

    private static final class Case {
        final GameTestHelper helper;
        final Kind kind;
        final UUID owner = UUID.randomUUID();
        final List<ItemStack> packages;
        Flight flight;
        RocketEntity copy;
        RocketEntity destinationCopy;
        final UUID offlinePassenger = UUID.randomUUID();
        List<BayReceipt> receipts = List.of();
        BayClaim claim;
        String checksum;
        int stage, waits, held;
        boolean sawIntent, decommissioned, sawConsumedSource, closed, sawLoading, endLoaded;
        /** W170-R41's loading kinds wait on chunk and entity-section loads, which run in wall-clock time. */
        final GameTestTickPacer pacer;

        Case(GameTestHelper helper, Kind kind) {
            this.helper = helper; this.kind = kind;
            packages = List.of(solarPackage(owner), solarPackage(owner));
            pacer = kind.loading() ? new GameTestTickPacer(helper.getLevel().getServer()) : null;
        }
        void start() { helper.onEachTick(this::tick); }

        void tick() {
            if (closed) { return; }
            if (pacer != null) { pacer.pace(); }
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
            BlockPos origin = helper.absolutePos(new BlockPos(3, 2, 3));
            BlockPos target = kind.loading() ? kind.far() : helper.absolutePos(new BlockPos(14, 2, 3));
            if (!helper.getLevel().isPositionEntityTicking(origin)
                    || (!kind.loading() && !helper.getLevel().isPositionEntityTicking(target))) { return; }
            if (kind.loading()) {
                helper.assertTrue(!helper.getLevel().areEntitiesLoaded(new net.minecraft.world.level.ChunkPos(target).toLong()),
                        "The far destination column was already loaded");
            }
            if (kind != Kind.PRESENT && kind != Kind.ABSENT) { packages.forEach(pkg -> registerSolar(server, pkg, owner)); }
            flight = transit(helper, origin, helper.getLevel().dimension().location(), position(target), owner, packages,
                    RocketEscrowSectionPatch.NEUTRAL, io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketFlightPlanner.EARTH.bodyId(),
                    kind == Kind.ABSENT_WITH_COPIES ? offlinePassenger : null);
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
            } else if (kind.copies() || kind == Kind.PROTECTED) {
                copy = ModEntities.ROCKET.get().create(helper.getLevel());
                copy.initializeTransferred(flight.record().sourceSnapshot(), logical(), owner, flight.record().sourceFlightData());
                if (kind == Kind.PROTECTED) { copy = RocketEscrowSectionPatch.BOUND.apply(helper.getLevel(), copy); }
                helper.assertTrue(helper.getLevel().addFreshEntity(copy), "Copy not published");
                if (kind != Kind.DUPLICATE && kind != Kind.PROTECTED) {
                    destinationCopy = ModEntities.ROCKET.get().create(helper.getLevel());
                    destinationCopy.initializeTransferred(flight.record().destinationSnapshot(), logical(), owner, flight.record().destinationFlightData());
                    helper.assertTrue(helper.getLevel().addFreshEntity(destinationCopy), "Destination copy not published");
                    if (kind == Kind.RECOGNIZED_COLD_COPY) {
                        helper.assertTrue(helper.getLevel().getEntity(destinationCopy.getUUID()) == null, "Cold copy was already indexed");
                    }
                }
                if (kind == Kind.ABSENT_WITH_COPIES) { flight.source().discard(); }
            } else if (kind == Kind.UNEXPECTED_MARGIN || kind == Kind.UNEXPECTED_COLUMN || kind == Kind.UNEXPECTED_LOADING) {
                // The end's own snapshot ID with only the first bay consumed: a hash that is neither group hash.
                var partial = consumed(flight.record(), List.of(intents.get(0)));
                boolean margin = kind == Kind.UNEXPECTED_MARGIN;
                var snapshot = margin ? partial.source() : partial.destination();
                copy = ModEntities.ROCKET.get().create(helper.getLevel());
                copy.initializeTransferred(snapshot, logical(), owner, flight.record().sourceFlightData());
                var region = io.github.sunthemoon.advancedrocketrycommunity.rocket.transaction.RocketRegion.fromSnapshot(snapshot);
                var end = snapshot.sourceOrigin();
                copy.setPos(margin ? region.minimum().x() - 0.5D : end.x() + 0.5D, end.y(), end.z() + 0.5D);
                helper.assertTrue(helper.getLevel().addFreshEntity(copy), "Copy not published");
                if (kind == Kind.UNEXPECTED_LOADING) { // the HIDDEN-section probe: added, but not in the entity index yet
                    helper.assertTrue(helper.getLevel().getEntity(copy.getUUID()) == null, "The copy at the unloaded end is already accessible");
                }
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
            if (kind == Kind.OUTSIDE_JOIN) {
                copy = ModEntities.ROCKET.get().create(helper.getLevel());
                copy.initializeTransferred(flight.record().sourceSnapshot(), logical(), owner, flight.record().sourceFlightData());
                copy.setPos(copy.getX() + 32, copy.getY(), copy.getZ());
                helper.assertTrue(helper.getLevel().addFreshEntity(copy), "Outside copy not published");
            }
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
                if (kind.loading() && !endLoaded) { awaitEnd(); }
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
                if (kind.held()) {
                    if (claim.hold() != BayClaim.Hold.REPAIR) { return; }
                    String reason = kind == Kind.PROTECTED ? "PROTECTED_DUPLICATE" : "UNEXPECTED_COPY";
                    helper.assertTrue(claim.reason().equals(reason) && claim.stage() == BayClaim.Stage.RESUME,
                            "The copy must hold the claim for repair with " + reason + ": " + describe());
                    helper.assertTrue(record().checksum().equals(checksum) && flight.source().snapshot().orElseThrow().contentHash()
                            .equals(flight.record().sourceSnapshot().contentHash()) && receipts.stream().allMatch(receipt ->
                            session.acknowledged(receipt.op()) && session.current(receipt.op()).orElseThrow().equals(receipt)),
                            "The repair hold changed a record or the source");
                    helper.assertTrue(copy.isAlive() && helper.getLevel().getEntity(copy.getUUID()) == copy,
                            "The repair hold must retain the copy, not discard it");
                    helper.assertTrue(!kind.loading() || sawLoading, "The hold came without R2's END_LOADING wait");
                    if (++held >= 20) { close(); helper.succeed(); }
                    return;
                }
                if (kind.copies() && claim.stage() == BayClaim.Stage.RETIRE) {
                    assertCopiesConsumed();
                    // current() is the pending RAM root: removeGroup excludes these entries before the IO acknowledgement.
                    // The acknowledged root must still retain the exact receipts until that completion ends RETIRE.
                    helper.assertTrue(BayReceiptTestHooks.store(server).orElseThrow().durable().receipts().containsAll(receipts),
                            "Receipts disappeared before D7 acknowledgement");
                }
                return;
            }
            helper.assertTrue(!kind.held(), "The claim ended while a copy that must hold it for repair was loaded: " + describe());
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
            if (kind.copies()) { assertCopiesConsumed(); }
            if (kind == Kind.ABSENT_WITH_COPIES) {
                helper.assertTrue(current.sourceFlightData().passengers().assignment(offlinePassenger).isPresent()
                        && current.destinationFlightData().passengers().assignment(offlinePassenger).isPresent(), "Offline seat was cleared");
            }
            if (kind.loading()) { helper.assertTrue(sawLoading && endLoaded, "R2's END_LOADING wait was never observed"); }
            close();
            helper.succeed();
        }

        /**
         * W170-R41, each tick until the destination end is loaded: R2 has not gone on (the claim waits in RESUME), and the
         * source, T and the receipts are unchanged. R1's tickets load the end; the wait must be seen at least once. R2 sets
         * END_LOADING in the step that adds R1's first tickets and runs at most once per tick, so the wait is visible to
         * this check in the tick the end is first seen loaded at the latest; a paced tick can load the far column whole.
         */
        void awaitEnd() {
            var server = helper.getLevel().getServer();
            if (claim.stage() == BayClaim.Stage.RESUME && claim.reason().equals("END_LOADING")) { sawLoading = true; }
            if (RocketTransferEntities.endAlreadyLoaded(server, flight.record().destinationSnapshot())) { endLoaded = true; return; }
            helper.assertTrue(claim.stage() == BayClaim.Stage.RESUME && claim.hold() == BayClaim.Hold.OWNED_WAIT,
                    "R2 went on before the destination end loaded: " + describe());
            var session = bays(helper).sessionForTests();
            helper.assertTrue(record().checksum().equals(checksum) && flight.source().snapshot().orElseThrow().contentHash()
                    .equals(flight.record().sourceSnapshot().contentHash()) && receipts.stream().allMatch(receipt ->
                    session.current(receipt.op()).filter(receipt::equals).isPresent()), "A record changed before the end loaded");
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
            helper.assertTrue((kind != Kind.ABSENT && kind != Kind.ABSENT_WITH_COPIES) || rocket != flight.source(),
                    "The absent source was rebuilt, not resurrected");
            sawConsumedSource = true;
        }

        void assertCopiesConsumed() {
            var group = claim.group().orElseThrow();
            helper.assertTrue(copy.isRemoved() && copy.snapshot().orElseThrow().contentHash().equals(group.srcAfter())
                    && consumedOps(copy.snapshot().orElseThrow()).containsAll(group.operations()), "Source copy was not consumed before removal");
            if (destinationCopy != null) {
                helper.assertTrue(destinationCopy.isRemoved() && destinationCopy.snapshot().orElseThrow().contentHash().equals(group.dstAfter())
                        && consumedOps(destinationCopy.snapshot().orElseThrow()).containsAll(group.operations()),
                        "Destination copy was not consumed with its own hash before removal");
            }
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
            if (destinationCopy != null) { destinationCopy.discard(); }
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
