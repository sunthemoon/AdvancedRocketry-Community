package io.github.sunthemoon.advancedrocketrycommunity.rocket.server;

import static io.github.sunthemoon.advancedrocketrycommunity.rocket.server.BayDeploymentGameTestSupport.*;

import io.github.sunthemoon.advancedrocketrycommunity.ModIdentity;
import io.github.sunthemoon.advancedrocketrycommunity.gametest.GameTestTickPacer;
import io.github.sunthemoon.advancedrocketrycommunity.persistence.migration.ManagedSavedDataType;
import io.github.sunthemoon.advancedrocketrycommunity.registry.ModEntities;
import io.github.sunthemoon.advancedrocketrycommunity.resourceescrow.BayReceipt;
import io.github.sunthemoon.advancedrocketrycommunity.resourceescrow.BayReceiptTestHooks;
import io.github.sunthemoon.advancedrocketrycommunity.resourceescrow.BayReceipts;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.bay.SatelliteBayBlockEntity;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.bay.SatelliteBayPackage;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.bay.SatelliteBayRoot;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.entity.RocketEntity;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketFlightLimits;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketFlightState;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketTransferPhase;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketTransferRecord;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketTransferRecoveryAction;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketTransferRecoveryReport;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.persistence.RocketJournalGameTestSupport;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.persistence.RocketTransferConsumption;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.persistence.RocketTransferSavedData;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.model.RocketStructureSnapshot;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.model.SatelliteState;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.persistence.SatelliteMissionSavedData;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/**
 * W342 (W27A B6.5, W338 finding 3): the crash state after D7 in which a source-Level save persisted SOURCE_REMOVED's
 * discard of S before the destination Level saved the new rocket. The journal holds a committed, consumed T' (every
 * registering bay consumed), the registry holds both registered satellites, no receipt exists, and neither entity of L
 * exists. Ordinary recovery on the installed manager must rebuild the destination from T' (REBUILD_DESTINATION) and
 * resurrect no package. Controls: the same state with the consumed source present keeps that source (KEEP_SOURCE,
 * W27A B8), and an unconsumed committed record of a group that never registered rebuilds its destination with the
 * packages still aboard (W27A section 7). Each state is built directly, in one tick, with no production seam.
 */
@GameTestHolder(ModIdentity.MOD_ID)
@PrefixGameTestTemplate(false)
public final class BayRebuildDestinationGameTests {
    /** Its own batch: no other test shares the journal or the installed recovery while these run. */
    static final String BATCH = "bay_rebuild_destination";
    /** Waiting ticks: the GameTest area to load, and the KEEP_SOURCE control's survivor proof (paced to 20 TPS). */
    private static final int MAX_WAITS = 600;

    private BayRebuildDestinationGameTests() { }

    private enum Kind {
        REBUILD, KEEP_SOURCE, UNCONSUMED;
        boolean consumed() { return this != UNCONSUMED; }
    }

    @GameTest(template = TEMPLATE, batch = BATCH, timeoutTicks = 1_200)
    public static void aConsumedCommittedGroupWithNeitherEntityRebuildsTheDestinationFromTPrime(GameTestHelper helper) {
        new Case(helper, Kind.REBUILD).start();
    }

    @GameTest(template = TEMPLATE, batch = BATCH, timeoutTicks = 1_200)
    public static void theSameConsumedStateWithItsSourcePresentKeepsTheConsumedSource(GameTestHelper helper) {
        new Case(helper, Kind.KEEP_SOURCE).start();
    }

    @GameTest(template = TEMPLATE, batch = BATCH, timeoutTicks = 1_200)
    public static void anUnconsumedCommittedRecordRebuildsItsDestinationWithThePackagesAboard(GameTestHelper helper) {
        new Case(helper, Kind.UNCONSUMED).start();
    }

    private static final class Case {
        final GameTestHelper helper;
        final Kind kind;
        final UUID owner = UUID.randomUUID();
        final List<ItemStack> packages;
        final GameTestTickPacer pacer;
        Flight flight;
        /** The record the journal holds once the state is built. */
        RocketTransferRecord committed;
        /** Each bay's consumed operation, in block order (consumed kinds only). */
        List<UUID> ops = List.of();
        Map<UUID, Optional<SatelliteState>> registryBefore = Map.of();
        Set<UUID> ownedBefore = Set.of();
        int stage, waits;
        boolean closed;

        Case(GameTestHelper helper, Kind kind) {
            this.helper = helper; this.kind = kind;
            packages = List.of(solarPackage(owner), solarPackage(owner));
            pacer = new GameTestTickPacer(helper.getLevel().getServer());
        }

        void start() { helper.onEachTick(this::tick); }

        void tick() {
            if (closed) { return; }
            try {
                helper.assertTrue(++waits < MAX_WAITS, "Fixture exceeded its bounded wait at stage " + stage);
                switch (stage) {
                    case 0 -> begin();
                    case 1 -> awaitSourceRecovery();
                    default -> { }
                }
            } catch (RuntimeException | Error failure) { close(); throw failure; }
        }

        /** Builds the state in this tick and runs ordinary recovery before the installed per-tick recovery can see it. */
        void begin() {
            var level = helper.getLevel();
            var server = level.getServer();
            BlockPos origin = helper.absolutePos(new BlockPos(3, 2, 3));
            BlockPos target = helper.absolutePos(new BlockPos(14, 2, 3));
            if (!level.isPositionEntityTicking(origin) || !level.isPositionEntityTicking(target)) { pacer.pace(); return; }
            if (kind.consumed()) { packages.forEach(pkg -> registerSolar(server, pkg, owner)); }
            flight = transit(helper, origin, target, owner, packages, RocketEscrowSectionPatch.NEUTRAL);
            var before = flight.record();
            var journal = RocketTransferSavedData.get(server);
            journal.put(before);
            RocketTransferRecord authority = before;
            if (kind.consumed()) {
                // The group's INTENT rows exist only in RAM: D7 retired them, so only their ops survive, in the bays.
                var intents = new ArrayList<BayReceipt>();
                for (int i = 0; i < packages.size(); i++) { intents.add(intent(server, before, packages.get(i), i)); }
                ops = intents.stream().map(BayReceipt::op).toList();
                var after = BayRegisteredGameTests.consumed(before, intents);
                var consumed = before.withConsumedSnapshots(after.source(), after.destination());
                helper.assertTrue(RocketTransferConsumption.apply(journal, before, consumed, before.sourceSnapshot().contentHash(),
                        before.destinationSnapshot().contentHash(), after.source().contentHash(), after.destination().contentHash())
                        == RocketTransferConsumption.Result.CONSUMED, "Fixture T' refused");
                authority = consumed;
                if (kind == Kind.KEEP_SOURCE) {
                    flight.source().applyBayConsumption(new BaySourceConsumption(flight.source().getUUID(), before.logicalRocketId(),
                            owner, flight.source().snapshot().orElseThrow(), after.source()));
                }
            }
            committed = authority.destinationSpawned(UUID.randomUUID()).advance(RocketTransferPhase.PASSENGERS_TRANSFERRED)
                    .advance(RocketTransferPhase.SOURCE_REMOVED).advance(RocketTransferPhase.COMMITTED);
            journal.put(committed);
            if (kind != Kind.KEEP_SOURCE) { flight.source().discard(); }
            RocketJournalGameTestSupport.acknowledgeFixture(helper);
            assertBuiltState();
            var report = RocketJournalGameTestSupport.recoverFixture(helper,
                    () -> RocketRuntime.recoverTransfer(server, committed.transferId()));
            if (kind == Kind.KEEP_SOURCE) {
                // W98: a kept source is reported once its survivor proof has retired T; until then each call waits.
                if (report.status() == RocketTransferRecoveryReport.Status.RECOVERED) { assertKept(report); } else { stage = 1; }
                return;
            }
            assertRebuilt(report);
            finish();
        }

        void assertBuiltState() {
            var current = journal().find(committed.transferId()).orElseThrow();
            helper.assertTrue(current == committed && current.phase() == RocketTransferPhase.COMMITTED, "The journal does not hold committed T'");
            helper.assertTrue(baysConsumed(current.sourceSnapshot()) && baysConsumed(current.destinationSnapshot()),
                    "The journal's record does not carry the expected bay state");
            assertNoReceipts();
            var rockets = rocketsOf(logical());
            if (kind == Kind.KEEP_SOURCE) {
                helper.assertTrue(rockets.size() == 1 && rockets.get(0) == flight.source()
                        && rockets.get(0).snapshot().orElseThrow().contentHash().equals(committed.sourceSnapshot().contentHash()),
                        "The control's source is not S' alone");
            } else {
                helper.assertTrue(rockets.isEmpty(), "An entity of L exists before recovery");
            }
            var registry = SatelliteMissionSavedData.get(helper.getLevel().getServer());
            var states = new LinkedHashMap<UUID, Optional<SatelliteState>>();
            packages.forEach(pkg -> states.put(satellite(pkg), registry.satellite(satellite(pkg))));
            helper.assertTrue(states.values().stream().allMatch(state -> state.isPresent() == kind.consumed()),
                    "The registry does not hold exactly the registered group");
            registryBefore = Collections.unmodifiableMap(states);
            ownedBefore = owned();
        }

        /** R1 and R3: the rebuilt rocket is T's (or T''s) destination snapshot, and nothing else changed. */
        void assertRebuilt(RocketTransferRecoveryReport report) {
            assertReport(report, RocketTransferRecoveryAction.REBUILD_DESTINATION, 0, 0);
            var current = journal().find(committed.transferId()).orElseThrow();
            helper.assertTrue(current.checksum().equals(committed.checksum()) && current.phase() == RocketTransferPhase.COMMITTED
                    && current.destinationEntityId().equals(committed.destinationEntityId()), "Recovery changed the journal's record");
            var rockets = rocketsOf(logical());
            helper.assertTrue(rockets.size() == 1, "Recovery left " + rockets.size() + " rockets of L, not one");
            var rebuilt = rockets.get(0);
            var expected = committed.destinationSnapshot();
            var origin = expected.sourceOrigin();
            helper.assertTrue(rebuilt != flight.source() && rebuilt.getUUID().equals(committed.destinationEntityId().orElseThrow()),
                    "The rebuilt rocket does not carry the record's destination UUID");
            helper.assertTrue(rebuilt.getX() == origin.x() + 0.5D && rebuilt.getY() == origin.y() + RocketFlightLimits.FLIGHT_ALTITUDE_BLOCKS
                    && rebuilt.getZ() == origin.z() + 0.5D, "The rebuilt rocket is not at its destination origin's altitude");
            var snapshot = rebuilt.snapshot().orElseThrow();
            helper.assertTrue(snapshot.snapshotId().equals(expected.snapshotId()) && snapshot.contentHash().equals(expected.contentHash()),
                    "The rebuilt rocket does not come from the record's destination snapshot");
            helper.assertTrue(baysConsumed(snapshot), "The rebuilt rocket's bays are not in the record's state");
            assertNoLoosePackage(snapshot);
            assertRegistry(true);
            assertNoReceipts();
            assertReload(rebuilt);
        }

        /** R2: the survivor proof retires T; the result is then reported once. */
        void awaitSourceRecovery() {
            if (journal().find(committed.transferId()).isPresent()) { pacer.pace(); return; }
            var server = helper.getLevel().getServer();
            assertKept(RocketJournalGameTestSupport.recoverFixture(helper, () -> RocketRuntime.recoverTransfer(server, committed.transferId())));
        }

        void assertKept(RocketTransferRecoveryReport report) {
            assertReport(report, RocketTransferRecoveryAction.KEEP_SOURCE, 1, 0);
            var rockets = rocketsOf(logical());
            helper.assertTrue(rockets.size() == 1 && rockets.get(0) == flight.source(), "KEEP_SOURCE did not keep exactly the source");
            var source = flight.source();
            var origin = committed.sourceSnapshot().sourceOrigin();
            var data = source.flightData().orElseThrow();
            helper.assertTrue(source.getUUID().equals(committed.sourceEntityId()), "The source lost T's source UUID");
            helper.assertTrue(source.getX() == origin.x() + 0.5D && source.getY() == origin.y() && source.getZ() == origin.z() + 0.5D
                    && (data.state() == RocketFlightState.FUELED || data.state() == RocketFlightState.ASSEMBLED)
                    && data.plan().isEmpty() && data.activeTransferId().isEmpty(), "The source is not stationary at its origin");
            var snapshot = source.snapshot().orElseThrow();
            helper.assertTrue(snapshot.contentHash().equals(committed.sourceSnapshot().contentHash()) && baysConsumed(snapshot),
                    "The kept source is not S'");
            assertNoLoosePackage(snapshot);
            assertRegistry(false);
            assertNoReceipts();
            assertReload(source);
            finish();
        }

        void assertReport(RocketTransferRecoveryReport report, RocketTransferRecoveryAction action, int sources, int destinations) {
            helper.assertTrue(report.status() == RocketTransferRecoveryReport.Status.RECOVERED
                    && report.action().filter(action::equals).isPresent()
                    && report.sourceMatches() == sources && report.destinationMatches() == destinations,
                    "Recovery reported " + report.status() + " " + report.action().orElse(null) + " " + report.sourceMatches() + "/"
                            + report.destinationMatches() + ", not RECOVERED " + action + " " + sources + "/" + destinations);
        }

        /** Consumed kinds: every bay holds its group op and no package. Unconsumed: every bay still holds its own package. */
        boolean baysConsumed(RocketStructureSnapshot snapshot) {
            var roots = bayPayloads(snapshot);
            if (roots.size() != packages.size()) { return false; }
            for (int i = 0; i < roots.size(); i++) {
                var contents = SatelliteBayRoot.decode(roots.get(i)).contents().orElse(null);
                if (contents == null || !contents.owner().equals(owner)) { return false; }
                if (kind.consumed() ? contents.pkg() != null || !contents.consumed().filter(ops.get(i)::equals).isPresent()
                        : contents.consumed().isPresent() || contents.pkg() == null
                                || !SatelliteBayPackage.decode(contents.pkg()).identity().map(identity -> identity.satelliteId())
                                        .filter(satellite(packages.get(i))::equals).isPresent()) {
                    return false;
                }
            }
            return true;
        }

        /** No package of the group is a loose item near either end or in a world bay of the structure. */
        void assertNoLoosePackage(RocketStructureSnapshot snapshot) {
            var level = helper.getLevel();
            var source = committed.sourceSnapshot().sourceOrigin();
            var destination = committed.destinationSnapshot().sourceOrigin();
            var area = new AABB(Math.min(source.x(), destination.x()), Math.min(source.y(), destination.y()),
                    Math.min(source.z(), destination.z()), Math.max(source.x(), destination.x()) + 1,
                    Math.max(source.y(), destination.y()) + RocketFlightLimits.FLIGHT_ALTITUDE_BLOCKS + 1,
                    Math.max(source.z(), destination.z()) + 1).inflate(16);
            for (ItemEntity item : level.getEntitiesOfClass(ItemEntity.class, area)) {
                helper.assertTrue(packages.stream().noneMatch(pkg -> ItemStack.isSameItemSameTags(item.getItem(), pkg)),
                        "A package of the group is back as an item");
            }
            helper.forEveryBlockInStructure(pos -> {
                if (level.getBlockEntity(pos) instanceof SatelliteBayBlockEntity bay) {
                    var contents = SatelliteBayRoot.decode(bay.captureRoot()).contents().orElse(null);
                    helper.assertTrue(contents == null || contents.pkg() == null, "A world bay holds a package");
                }
            });
            helper.assertTrue(!kind.consumed() || bayPayloads(snapshot).stream().allMatch(root ->
                    SatelliteBayRoot.decode(root).contents().map(contents -> contents.pkg() == null).orElse(false)),
                    "A consumed bay holds a package again");
        }

        /** Same tick (R1): each entry is equal. Over the proof's ticks (R2): the same satellites, none added or removed. */
        void assertRegistry(boolean sameTick) {
            var registry = SatelliteMissionSavedData.get(helper.getLevel().getServer());
            for (var entry : registryBefore.entrySet()) {
                var now = registry.satellite(entry.getKey());
                boolean unchanged = sameTick ? now.equals(entry.getValue()) : now.isPresent() == entry.getValue().isPresent()
                        && now.map(state -> state.ownerId().equals(owner) && state.definitionId().equals(entry.getValue().orElseThrow().definitionId())
                                && state.launchedAtLogicalTime() == entry.getValue().orElseThrow().launchedAtLogicalTime()).orElse(true);
                helper.assertTrue(unchanged, "Recovery changed the registry entry of " + entry.getKey());
            }
            helper.assertTrue(owned().equals(ownedBefore), "Recovery added or removed an owned satellite");
        }

        Set<UUID> owned() {
            return SatelliteMissionSavedData.get(helper.getLevel().getServer()).satellites().stream()
                    .filter(state -> state.ownerId().equals(owner)).map(SatelliteState::satelliteId).collect(Collectors.toUnmodifiableSet());
        }

        void assertNoReceipts() {
            var server = helper.getLevel().getServer();
            var session = bays(helper).sessionForTests();
            helper.assertTrue(packages.stream().allMatch(pkg -> session.find(satellite(pkg)).isEmpty()), "A receipt names a satellite of the group");
            helper.assertTrue(!BayReceipts.holdsRocket(server, logical()), "The bay receipts hold L");
            var store = BayReceiptTestHooks.store(server).orElse(null);
            helper.assertTrue(store == null || (store.receipts().stream().noneMatch(receipt -> receipt.rocket().equals(logical()))
                    && store.durable().receipts().stream().noneMatch(receipt -> receipt.rocket().equals(logical()))),
                    "A stored receipt names L");
        }

        /**
         * The acknowledged journal file, read from disk: T' unchanged with the same consumed operations (rebuilds), or T
         * gone (the kept source). The surviving entity's saved NBT, loaded into a new entity, keeps the same bay state.
         */
        void assertReload(RocketEntity survivor) {
            var server = helper.getLevel().getServer();
            RocketJournalGameTestSupport.acknowledgeFixture(helper);
            var file = server.getWorldPath(LevelResource.ROOT).resolve("data").resolve(ManagedSavedDataType.ROCKET_TRANSFERS.fileName());
            RocketTransferSavedData disk;
            try {
                disk = RocketTransferSavedData.load(NbtIo.readCompressed(file.toFile()).getCompound("data"));
            } catch (IOException failure) {
                throw new IllegalStateException("The journal file could not be read", failure);
            }
            var stored = disk.find(committed.transferId()).orElse(null);
            if (kind == Kind.KEEP_SOURCE) {
                helper.assertTrue(stored == null, "The retired T is still in the journal file");
            } else {
                helper.assertTrue(stored != null && stored.checksum().equals(committed.checksum())
                        && stored.phase() == RocketTransferPhase.COMMITTED
                        && stored.destinationEntityId().equals(committed.destinationEntityId())
                        && stored.sourceSnapshot().contentHash().equals(committed.sourceSnapshot().contentHash())
                        && stored.destinationSnapshot().contentHash().equals(committed.destinationSnapshot().contentHash())
                        && baysConsumed(stored.sourceSnapshot()) && baysConsumed(stored.destinationSnapshot()),
                        "The journal file does not hold the same record and bay operations");
            }
            CompoundTag saved = new CompoundTag();
            survivor.saveWithoutId(saved);
            RocketEntity reloaded = ModEntities.ROCKET.get().create(helper.getLevel());
            helper.assertTrue(reloaded != null, "No rocket entity type");
            reloaded.load(saved);
            var snapshot = reloaded.snapshot().orElseThrow();
            helper.assertTrue(snapshot.contentHash().equals(survivor.snapshot().orElseThrow().contentHash()) && baysConsumed(snapshot),
                    "The survivor's saved NBT does not keep its bay operations");
            helper.assertTrue(!kind.consumed() || consumedOps(snapshot).equals(ops), "The reloaded consumed operations changed");
        }

        List<RocketEntity> rocketsOf(UUID logical) {
            var rockets = new ArrayList<RocketEntity>();
            for (Entity entity : helper.getLevel().getAllEntities()) {
                if (entity instanceof RocketEntity rocket && rocket.operational()
                        && rocket.assemblyTransactionId().filter(logical::equals).isPresent()) {
                    rockets.add(rocket);
                }
            }
            return rockets;
        }

        UUID logical() { return flight.record().logicalRocketId(); }
        RocketTransferSavedData journal() { return RocketTransferSavedData.get(helper.getLevel().getServer()); }

        void finish() {
            stage = 2;
            close();
            helper.succeed();
        }

        void close() {
            if (closed) { return; }
            closed = true;
            if (flight == null) { return; }
            var server = helper.getLevel().getServer();
            rocketsOf(logical()).forEach(Entity::discard);
            flight.source().discard();
            var journal = journal();
            if (journal.find(flight.record().transferId()).isPresent()) { journal.remove(flight.record().transferId()); }
            RocketTransferEntities.releaseFlightTickets(server, flight.record());
            var registry = SatelliteMissionSavedData.get(server);
            for (var pkg : packages) {
                registry.satellite(satellite(pkg)).ifPresent(state -> registry.decommission(state.satelliteId(), owner, true, true));
            }
        }
    }
}
